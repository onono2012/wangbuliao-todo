package com.wangbuliao.todo.floatwin

import android.app.Activity
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.core.content.FileProvider
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.media.AudioNote
import com.wangbuliao.todo.media.ImageStore
import com.wangbuliao.todo.reminder.PinNotifService
import com.wangbuliao.todo.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 悬浮窗媒体采集透明页：拍照 / 相册选图 / 录音，完成后直接存为「随手记」。
 * 由 FloatingNoteService 面板按钮拉起；无可见界面（录音时显示小对话框）。
 */
class FloatMediaActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cameraFile: File? = null
    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recStart = 0L
    private var recDialog: AlertDialog? = null

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = cameraFile
        if (ok && f != null && f.exists() && f.length() > 0) {
            saveQuick("", images = listOf(f.absolutePath), toast = "照片已记入「随手记」📷")
        } else {
            f?.delete()
            toast("未拍摄照片")
            finish()
        }
    }

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = ImageStore.importUri(this, uri)
            if (path != null) {
                saveQuick("", images = listOf(path), toast = "图片已记入「随手记」🖼")
                return@registerForActivityResult
            }
        }
        toast("未选择图片")
        finish()
    }

    private val micPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else {
            toast("需要麦克风权限才能录音")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_CAMERA -> launchCamera()
            ACTION_GALLERY -> pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            ACTION_RECORD -> {
                if (AudioNote.hasMicPerm(this)) startRecording()
                else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            else -> finish()
        }
    }

    private fun launchCamera() {
        try {
            val f = ImageStore.newCameraFile(this)
            cameraFile = f
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            takePicture.launch(uri)
        } catch (e: Exception) {
            toast("无法启动相机：${e.message}")
            finish()
        }
    }

    private fun startRecording() {
        val pair = try {
            AudioNote.start(this)
        } catch (e: Exception) {
            null
        }
        if (pair == null) {
            toast("录音启动失败")
            finish()
            return
        }
        recorder = pair.first
        recFile = pair.second
        recStart = System.currentTimeMillis()
        showRecDialog()
    }

    /** 录音中的极简对话框：计时 + 停止按钮（60s 自动停） */
    private fun showRecDialog() {
        val timer = TextView(this).apply {
            text = "🎤 录音中 00:00"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        val hint = TextView(this).apply {
            text = "最长 60 秒 · 点「停止并保存」入库"
            textSize = 12f
            gravity = Gravity.CENTER
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(timer)
            addView(hint)
        }
        recDialog = AlertDialog.Builder(this)
            .setTitle("语音速记")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("停止并保存") { _, _ -> stopRecording(save = true) }
            .setNegativeButton("丢弃") { _, _ -> stopRecording(save = false) }
            .create()
        recDialog?.show()
        // 计时刷新 + 60s 自动停止
        scope.launch {
            while (recorder != null) {
                val sec = (System.currentTimeMillis() - recStart) / 1000
                timer.text = "🎤 录音中 %02d:%02d".format(sec / 60, sec % 60)
                if (sec >= 60) {
                    stopRecording(save = true)
                    break
                }
                kotlinx.coroutines.delay(300)
            }
        }
    }

    private fun stopRecording(save: Boolean) {
        val r = recorder ?: return
        recorder = null
        recDialog?.dismiss()
        recDialog = null
        val dur = System.currentTimeMillis() - recStart
        try {
            r.stop()
        } catch (_: Exception) {
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
        val f = recFile
        if (save && f != null && f.exists() && f.length() > 0 && dur > 500) {
            saveQuick("", audioPath = f.absolutePath, audioDur = dur, toast = "语音已记入「随手记」🎤")
        } else {
            f?.delete()
            toast("录音已丢弃")
            finish()
        }
    }

    private fun saveQuick(
        text: String,
        audioPath: String = "",
        audioDur: Long = 0,
        images: List<String> = emptyList(),
        toast: String
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                TaskRepo.quickNote(text, audioPath, audioDur, images)
                PinNotifService.refresh(applicationContext)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(applicationContext)
                Haptics.quickSaved(applicationContext)
                launch(Dispatchers.Main) {
                    toast(toast)
                    finish()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    toast("保存失败：${e.message}")
                    finish()
                }
            }
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 录音中直接销毁页面：停止并丢弃
        recorder?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
            recFile?.delete()
        }
        recorder = null
        recDialog?.dismiss()
        scope.cancel()
    }

    companion object {
        const val EXTRA_ACTION = "wbl_media_action"
        const val ACTION_CAMERA = "camera"
        const val ACTION_GALLERY = "gallery"
        const val ACTION_RECORD = "record"
    }
}
