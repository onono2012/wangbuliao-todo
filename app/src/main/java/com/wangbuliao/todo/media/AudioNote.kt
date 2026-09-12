package com.wangbuliao.todo.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wangbuliao.todo.ui.wblCardColor
import com.wangbuliao.todo.util.TimeFmt
import kotlinx.coroutines.delay
import java.io.File

/** 录音文件管理 */
object AudioNote {
    fun dir(ctx: Context): File = File(ctx.filesDir, "audio").apply { mkdirs() }
    fun newFile(ctx: Context): File = File(dir(ctx), "rec_${System.currentTimeMillis()}.m4a")

    fun hasMicPerm(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun newRecorder(ctx: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx)
        else MediaRecorder()

    /** 启动录制；失败返回 null（调用方保持未录音状态） */
    fun start(ctx: Context): Pair<MediaRecorder, File>? {
        return try {
            val f = newFile(ctx)
            val r = newRecorder(ctx)
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            r to f
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 录音卡片：无录音→「录一段」；录音中→计时+波形+停止；
 * 已有录音→播放条（进度拖动）+ 删除。
 */
@Composable
fun AudioSection(
    audioPath: String,
    audioDur: Long,
    onAudio: (String, Long) -> Unit,
    onClear: () -> Unit,
    onDenied: () -> Unit
) {
    val ctx = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<File?>(null) }
    var recStart by remember { mutableStateOf(0L) }
    var seconds by remember { mutableStateOf(0) }
    val amps = remember { mutableStateListOf<Int>() }
    var hasPerm by remember { mutableStateOf(AudioNote.hasMicPerm(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPerm = granted
        if (granted) {
            AudioNote.start(ctx)?.let { (r, f) ->
                recorder = r
                recFile = f
                recStart = System.currentTimeMillis()
                seconds = 0
                amps.clear()
                recording = true
            } ?: onDenied()
        } else {
            onDenied()
        }
    }

    fun stopRecording(save: Boolean) {
        val r = recorder ?: return
        val dur = System.currentTimeMillis() - recStart
        val f = recFile
        recording = false
        try {
            r.stop()
        } catch (_: Exception) {
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
        recorder = null
        seconds = 0
        amps.clear()
        if (save && f != null && f.exists() && f.length() > 1000 && dur >= 800) {
            onAudio(f.absolutePath, dur)
        } else {
            try {
                f?.delete()
            } catch (_: Exception) {
            }
        }
        recFile = null
    }

    // 录音中：每 200ms 采样振幅；超 10 分钟自动完成
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        var tick = 0
        while (recording) {
            delay(200)
            val r = recorder
            if (r != null) {
                try {
                    amps.add(r.maxAmplitude)
                    if (amps.size > 60) amps.removeAt(0)
                } catch (_: Exception) {
                }
            }
            if (++tick % 5 == 0) {
                seconds = ((System.currentTimeMillis() - recStart) / 1000).toInt()
                if (seconds >= 600) {
                    stopRecording(true)
                    break
                }
            }
        }
    }

    // 离开页面时释放录音资源（未完成的录音丢弃）
    DisposableEffect(Unit) {
        onDispose {
            if (recording) {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            recorder = null
        }
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = wblCardColor())
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Mic, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "语音记事",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            when {
                recording -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Mic, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            TimeFmt.dur(seconds * 1000L),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { stopRecording(false) }) { Text("放弃") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { stopRecording(true) }) {
                            Icon(Icons.Filled.Stop, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("完成")
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().height(36.dp).padding(top = 6.dp)) {
                        if (amps.isEmpty()) return@Canvas
                        val barW = size.width / 61f
                        val mid = size.height / 2f
                        amps.forEachIndexed { i, a ->
                            val h = (a / 32767f).coerceIn(0.04f, 1f) * size.height
                            drawRoundRect(
                                color = androidx.compose.ui.graphics.Color(0xFFE040FB),
                                topLeft = Offset(i * barW, mid - h / 2),
                                size = Size(barW * 0.6f, h),
                                cornerRadius = CornerRadius(barW * 0.3f)
                            )
                        }
                    }
                }
                audioPath.isNotEmpty() -> {
                    AudioPlayer(path = audioPath, durMs = audioDur)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            // 文件删除交给 VM 延迟处理（会话新增立即删；原任务附件保存/放弃时差量清理）
                            onClear()
                        }) {
                            Icon(
                                Icons.Filled.Delete, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("删除录音", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            if (hasPerm) {
                                AudioNote.start(ctx)?.let { (r, f) ->
                                    recorder = r
                                    recFile = f
                                    recStart = System.currentTimeMillis()
                                    seconds = 0
                                    amps.clear()
                                    recording = true
                                } ?: onDenied()
                            } else {
                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Filled.Mic, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("录一段")
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "最长 10 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 播放条：播放/暂停 + 进度拖动 + 时间 */
@Composable
fun AudioPlayer(path: String, durMs: Long) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    var seekTo by remember { mutableStateOf(-1) }

    DisposableEffect(path) {
        onDispose {
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
        }
    }

    // 播放进度轮询 + 拖动定位
    LaunchedEffect(playing) {
        while (playing) {
            val p = player ?: break
            try {
                if (seekTo >= 0) {
                    p.seekTo(seekTo)
                    seekTo = -1
                }
                pos = p.currentPosition
                if (!p.isPlaying && pos >= (durMs - 400).coerceAtLeast(0)) {
                    playing = false
                    pos = 0
                    break
                }
            } catch (_: Exception) {
                playing = false
                break
            }
            delay(200)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) {
                try {
                    player?.pause()
                } catch (_: Exception) {
                }
                playing = false
            } else {
                try {
                    if (player == null) {
                        player = MediaPlayer().apply {
                            setDataSource(path)
                            prepare()
                        }
                    }
                    player?.let {
                        if (pos > 0) it.seekTo(pos)
                        it.start()
                        playing = true
                    }
                } catch (_: Exception) {
                    playing = false
                }
            }
        }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.weight(1f)) {
            Slider(
                value = if (durMs > 0) (pos.toFloat() / durMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { v -> pos = (v * durMs).toInt() },
                onValueChangeFinished = { seekTo = pos },
                enabled = durMs > 0
            )
            Text(
                "${TimeFmt.dur(pos.toLong())} / ${TimeFmt.dur(if (durMs > 0) durMs else pos.toLong())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
