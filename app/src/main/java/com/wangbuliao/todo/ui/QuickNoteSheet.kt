package com.wangbuliao.todo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.media.AudioSection
import com.wangbuliao.todo.media.ImageStore
import com.wangbuliao.todo.media.Thumb
import com.wangbuliao.todo.util.TimeFmt
import java.io.File
import java.util.Calendar

/** 提醒快捷预设（记一笔面板 + 悬浮面板共用） */
object RemindPreset {
    fun hourLater(h: Int): Long = System.currentTimeMillis() + h * 3600_000L

    /** 今晚 hh:00；若已过则顺延到明天同一时刻 */
    fun tonight(hour: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** 明早 hh:00 */
    fun tomorrow(hour: Int): Long {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}

/**
 * 记一笔 · 下拉快速面板（v1.5.5）。
 * 一屏内完成：内容 + 分类 + 提醒 + 媒体，避免进完整编辑页。「更多选项」跳转完整编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteSheet(
    vm: MainViewModel,
    categories: List<String>,
    onDismiss: () -> Unit,
    onMore: () -> Unit
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf("") }
    var category by remember {
        mutableStateOf(
            categories.firstOrNull() ?: Task.QUICK_CATEGORY
        )
    }
    var remindAt by remember { mutableStateOf(0L) }
    var showPicker by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var audioPath by remember { mutableStateOf("") }
    var audioDur by remember { mutableStateOf(0L) }
    val images = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    // 关闭面板时清理未保存的媒体，防孤儿文件
    fun cleanupUnsaved() {
        images.forEach { ImageStore.delete(it) }
        if (audioPath.isNotEmpty()) {
            try { File(audioPath).delete() } catch (_: Exception) {}
        }
    }

    // 拍照
    var camUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val u = camUri; camUri = null
        if (u != null) {
            val path = ImageStore.pathFromUri(ctx, u)
            if (ok && path != null && images.size < ImageStore.maxImages()) {
                images.add(path)
            } else {
                ImageStore.rawPathFromUri(ctx, u)?.let { ImageStore.delete(it) }
            }
        }
    }
    // 相册多选
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val room = ImageStore.maxImages() - images.size
            val added = uris.take(room).mapNotNull { ImageStore.importUri(ctx, it) }
            images.addAll(added)
            if (uris.size > room) vm.notifyMsg("最多 ${ImageStore.maxImages()} 张，已保留前 $room 张")
        }
    }

    ModalBottomSheet(
        onDismissRequest = { cleanupUnsaved(); onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("记一笔", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                WblTextButton(onClick = { onMore() }) { Text("更多选项") }
            }

            // 内容输入
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                placeholder = { Text("想做点什么？随手记下来…") },
                minLines = 3
            )
            Spacer(Modifier.height(14.dp))

            // 分类
            SectionLabel("分类")
            ChipRow(
                options = if (categories.contains(Task.QUICK_CATEGORY))
                    categories else listOf(Task.QUICK_CATEGORY) + categories,
                selected = category,
                onSelect = { category = it }
            )
            Spacer(Modifier.height(14.dp))

            // 提醒
            SectionLabel(if (remindAt > 0) "提醒 · ${TimeFmt.remind(remindAt)}" else "提醒")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(colors = wblChipColors(), selected = remindAt == 0L,
                    onClick = { remindAt = 0 }, label = { Text("不提醒") })
                FilterChip(colors = wblChipColors(), selected = false,
                    onClick = { remindAt = RemindPreset.hourLater(1) }, label = { Text("1小时后") })
                FilterChip(colors = wblChipColors(), selected = false,
                    onClick = { remindAt = RemindPreset.hourLater(3) }, label = { Text("3小时后") })
                FilterChip(colors = wblChipColors(), selected = false,
                    onClick = { remindAt = RemindPreset.tonight(21) }, label = { Text("今晚21点") })
                FilterChip(colors = wblChipColors(), selected = false,
                    onClick = { remindAt = RemindPreset.tomorrow(9) }, label = { Text("明早9点") })
                FilterChip(colors = wblChipColors(), selected = false,
                    onClick = { showPicker = true }, label = { Text("自定义") })
            }
            Spacer(Modifier.height(14.dp))

            // 媒体
            SectionLabel("附件")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        try {
                            val f = ImageStore.newCameraFile(ctx)
                            val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                            camUri = u; cameraLauncher.launch(u)
                        } catch (e: Exception) {
                            vm.notifyMsg("无法启动相机：${e.message ?: "系统拒绝"}")
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)) },
                    label = { Text("拍照") },
                    colors = AssistChipDefaults.assistChipColors()
                )
                AssistChip(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)) },
                    label = { Text("相册") },
                    colors = AssistChipDefaults.assistChipColors()
                )
                AssistChip(
                    onClick = { showAudio = !showAudio },
                    label = { Text(if (showAudio) "收起录音" else "录音") },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }

            // 图片缩略图行
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEach { p ->
                        Box {
                            Thumb(p, 64.dp)
                            IconButton(
                                onClick = { ImageStore.delete(p); images.remove(p) },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                            ) {
                                Icon(Icons.Filled.Close, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // 录音卡片
            if (showAudio) {
                Spacer(Modifier.height(10.dp))
                AudioSection(
                    audioPath = audioPath,
                    audioDur = audioDur,
                    onAudio = { p, d -> audioPath = p; audioDur = d },
                    onClear = { audioPath = ""; audioDur = 0 },
                    onDenied = { vm.notifyMsg("需要麦克风权限") }
                )
            }

            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val ok = text.isNotBlank() || images.isNotEmpty() || audioPath.isNotEmpty()
                    if (!ok) { vm.notifyMsg("先写点内容吧"); return@Button }
                    vm.quickCreate(
                        text = text,
                        category = category,
                        urgent = false,
                        remindAt = remindAt,
                        images = images.toList(),
                        audioPath = audioPath,
                        audioDur = audioDur
                    ) { saved ->
                        if (saved) {
                            // 保存成功后媒体归属任务，清空引用避免 dismiss 时误删
                            images.clear(); audioPath = ""; audioDur = 0
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("保存", fontSize = MaterialTheme.typography.titleMedium.fontSize) }
        }
    }

    if (showPicker) {
        RemindPickerDialog(
            initial = remindAt,
            onConfirm = { remindAt = it; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(colors = wblChipColors(), selected = o == selected,
                onClick = { onSelect(o) }, label = { Text(o) })
        }
    }
}
