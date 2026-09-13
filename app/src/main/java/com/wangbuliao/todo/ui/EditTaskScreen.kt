package com.wangbuliao.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wangbuliao.todo.media.AudioSection
import com.wangbuliao.todo.media.ImageStore
import com.wangbuliao.todo.media.Thumb
import com.wangbuliao.todo.util.RepeatRule
import com.wangbuliao.todo.util.TimeFmt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 简易日期时间选择（不依赖 m3 DatePicker，兼容 material3 1.1.2）：
 *  日期=未来90天横滑；小时/分钟=横滑芯片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindPickerDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var dayOffset by remember {
        mutableStateOf(
            if (initial > 0) {
                ((initial - today.timeInMillis) / 86400000L).toInt().coerceIn(0, 89)
            } else 0
        )
    }
    var hour by remember {
        mutableStateOf(
            if (initial > 0) Calendar.getInstance().apply { timeInMillis = initial }
                .get(Calendar.HOUR_OF_DAY)
            else {
                val nh = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1
                if (nh > 23) 0 else nh
            }
        )
    }
    var minute by remember {
        mutableStateOf(
            if (initial > 0) {
                Calendar.getInstance().apply { timeInMillis = initial }
                    .let { it.get(Calendar.MINUTE) / 5 * 5 }
            } else 0
        )
    }
    val df = remember { SimpleDateFormat("M月d日", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒时间") },
        text = {
            Column {
                // v1.5.5 快捷预设：一键选中常用提醒时间（不直观问题 → 直接给结果）
                Text("快捷", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fun applyPreset(ts: Long) {
                        val c = Calendar.getInstance().apply { timeInMillis = ts }
                        dayOffset = ((ts - today.timeInMillis) / 86400000L).toInt().coerceIn(0, 89)
                        hour = c.get(Calendar.HOUR_OF_DAY)
                        minute = c.get(Calendar.MINUTE) / 5 * 5
                    }
                    FilterChip(colors = wblChipColors(), selected = false,
                        onClick = { applyPreset(RemindPreset.hourLater(1)) },
                        label = { Text("1小时后") })
                    FilterChip(colors = wblChipColors(), selected = false,
                        onClick = { applyPreset(RemindPreset.hourLater(3)) },
                        label = { Text("3小时后") })
                    FilterChip(colors = wblChipColors(), selected = false,
                        onClick = { applyPreset(RemindPreset.tonight(21)) },
                        label = { Text("今晚21点") })
                    FilterChip(colors = wblChipColors(), selected = false,
                        onClick = { applyPreset(RemindPreset.tomorrow(9)) },
                        label = { Text("明早9点") })
                }
                Spacer(Modifier.height(10.dp))
                Text("日期", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..89).forEach { off ->
                        val label = when (off) {
                            0 -> "今天"
                            1 -> "明天"
                            else -> df.format(Date(today.timeInMillis + off * 86400000L))
                        }
                        FilterChip(
                            colors = wblChipColors(),
                            selected = dayOffset == off,
                            onClick = { dayOffset = off },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("小时", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..23).forEach { h ->
                        FilterChip(
                            colors = wblChipColors(),
                            selected = hour == h,
                            onClick = { hour = h },
                            label = { Text(String.format(Locale.getDefault(), "%02d", h)) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("分钟", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..11).forEach { i ->
                        val m = i * 5
                        FilterChip(
                            colors = wblChipColors(),
                            selected = minute == m,
                            onClick = { minute = m },
                            label = { Text(String.format(Locale.getDefault(), "%02d", m)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Calendar.getInstance().apply {
                    timeInMillis = today.timeInMillis + dayOffset * 86400000L
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onConfirm(c.timeInMillis)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(vm: MainViewModel, draft: EditDraft, ui: UiState) {
    val ctx = LocalContext.current
    val isNew = draft.id <= 0
    var showPicker by remember { mutableStateOf(false) }
    var showCatDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }

    // 系统返回键：草稿有未保存修改时先弹确认，防误丢已填内容/照片/录音
    BackHandler {
        if (vm.isDraftDirty()) showDiscardConfirm = true else vm.backList()
    }

    // 拍照：FileProvider 提供 filesDir/images 目标文件
    var camUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val u = camUri
        camUri = null
        if (u != null) {
            val path = ImageStore.pathFromUri(ctx, u)
            if (ok && path != null && draft.images.size < ImageStore.maxImages()) {
                vm.updateDraft { it.copy(images = it.images + path) }
            } else {
                // 取消/失败/已达上限：清理相机残留文件（ColorOS 取消也可能已写入），防孤儿图片占存储
                ImageStore.rawPathFromUri(ctx, u)?.let { ImageStore.delete(it) }
            }
        }
    }
    // 相册多选：导入应用私有目录（与拍照文件统一管理）
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val room = ImageStore.maxImages() - draft.images.size
            val added = uris.take(room).mapNotNull { ImageStore.importUri(ctx, it) }
            if (added.isNotEmpty()) vm.updateDraft { it.copy(images = it.images + added) }
            if (uris.size > room) vm.notifyMsg("最多 ${ImageStore.maxImages()} 张，已保留前 $room 张")
        }
    }

    WblScreenBackground {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            val themed = wblTopBarThemed()
            val tbc = wblTopBarContentColor()
            val tint = tbc
            TopAppBar(
                title = {
                    Text(if (isNew) "记一笔" else "编辑事项", color = tint)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vm.isDraftDirty()) showDiscardConfirm = true else vm.backList()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = tint)
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Outlined.Delete, "删除",
                                tint = if (themed) tbc
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = if (themed) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = tbc,
                        navigationIconContentColor = tbc,
                        actionIconContentColor = tbc
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                modifier = wblTopBarModifier()
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxWidth()) {
        // ⑥ 排版重构：内容滚动区 + 底部常驻保存栏（保存键不再埋在页尾）
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { v -> vm.updateDraft { it.copy(title = v) } },
                label = { Text("要做的事 *") },
                placeholder = { Text("例如：下午3点开项目评审会") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { v -> vm.updateDraft { it.copy(note = v) } },
                label = { Text("备注") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // ── 分类（卡片分组）──
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
                    WblSectionHead("分类", Icons.Outlined.Category, MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ui.categories.forEach { c ->
                            FilterChip(
                                colors = wblChipColors(),
                                selected = draft.category == c,
                                onClick = { vm.updateDraft { it.copy(category = c) } },
                                label = { Text(c) },
                                leadingIcon = {
                                    Icon(categoryIcon(c), null, Modifier.size(16.dp))
                                }
                            )
                        }
                        OutlinedButton(onClick = { showCatDialog = true }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("新分类")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── 标记（卡片分组：紧急 + 置顶）──
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            WblSectionHead("紧急", Icons.Outlined.LocalFireDepartment, MaterialTheme.typography.titleSmall)
                            Text(
                                "紧急事项置顶并高亮显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.urgent,
                            onCheckedChange = { v -> vm.updateDraft { it.copy(urgent = v) } }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            WblSectionHead("置顶", Icons.Outlined.PushPin, MaterialTheme.typography.titleSmall)
                            Text(
                                "置顶事项固定在待办列表最上方",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.pinned,
                            onCheckedChange = { v -> vm.updateDraft { it.copy(pinned = v) } }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── 附件（卡片分组：语音 + 图片）──
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
            AudioSection(
                audioPath = draft.audioPath,
                audioDur = draft.audioDur,
                onAudio = { path, dur -> vm.updateDraft { it.copy(audioPath = path, audioDur = dur) } },
                onClear = { vm.removeDraftAudio() },
                onDenied = { vm.notifyMsg("需要麦克风权限才能录音") }
            )
            Spacer(Modifier.height(14.dp))

            // ── 图片记事 ──
            WblSectionHead("图片", Icons.Outlined.Image, MaterialTheme.typography.titleSmall)
            Text(
                "拍照或从相册选择（最多 ${ImageStore.maxImages()} 张）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.images.forEach { path ->
                    Box {
                        Thumb(path = path, size = 84.dp, onClick = { showImageViewer = path })
                        Icon(
                            Icons.Outlined.Close, "移除图片",
                            Modifier.align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.removeDraftImage(path) }
                                .padding(3.dp),
                            tint = Color.White
                        )
                    }
                }
                if (draft.images.size < ImageStore.maxImages()) {
                    ImageAddButton(
                        icon = Icons.Filled.AddAPhoto, label = "拍照",
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        try {
                            val f = ImageStore.newCameraFile(ctx)
                            val u = FileProvider.getUriForFile(
                                ctx, "${ctx.packageName}.fileprovider", f
                            )
                            camUri = u
                            cameraLauncher.launch(u)
                        } catch (e: Exception) {
                            // ColorOS 等设备可能拒绝启动系统相机（权限撤销/无相机应用）：提示而非崩溃
                            camUri = null
                            vm.notifyMsg("无法启动相机：${e.message ?: "系统拒绝"}")
                        }
                    }
                    ImageAddButton(
                        icon = Icons.Filled.PhotoLibrary, label = "相册",
                        tint = MaterialTheme.colorScheme.tertiary
                    ) {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }
            }
                } // 附件卡片 Column
            } // 附件卡片 Card
            Spacer(Modifier.height(12.dp))

            // ── 提醒（卡片分组：提醒时间 + 重复规则）──
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            WblSectionHead("提醒", Icons.Outlined.Notifications, MaterialTheme.typography.titleSmall)
                            Text(
                                if (draft.remindAt > 0) TimeFmt.remind(draft.remindAt)
                                else "到时弹出通知提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showPicker = true }) {
                            Text(if (draft.remindAt > 0) "修改" else "设置")
                        }
                        if (draft.remindAt > 0) {
                            Spacer(Modifier.padding(start = 6.dp))
                            TextButton(onClick = { vm.updateDraft { it.copy(remindAt = 0, repeat = 0) } }) {
                                Text("清除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // ── 重复（仅设置了提醒时间时可选）：每天/每周/每月，完成本期后自动滚动到下一期 ──
                    if (draft.remindAt > 0) {
                        Spacer(Modifier.height(10.dp))
                        WblSectionHead("重复", Icons.Outlined.Refresh, MaterialTheme.typography.titleSmall)
                        Text(
                            "勾选完成后自动滚动到下一周期，保持待办",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RepeatRule.OPTIONS.forEach { (v, label) ->
                                FilterChip(
                                    colors = wblChipColors(),
                                    selected = draft.repeat == v,
                                    onClick = { vm.updateDraft { it.copy(repeat = v) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } // 滚动区结束

        // ⑥ 底部常驻保存栏：任何滚动位置都可见，无需翻到页尾保存
        Button(
            onClick = { vm.saveDraft() },
            modifier = Modifier.padding(horizontal = 16.dp)
                .fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isNew) "保存" else "保存修改", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        } // 外层 Column 结束
    }
    }

    if (showPicker) {
        RemindPickerDialog(
            initial = draft.remindAt,
            onConfirm = { ts ->
                showPicker = false
                vm.updateDraft { it.copy(remindAt = ts) }
            },
            onDismiss = { showPicker = false }
        )
    }
    if (showCatDialog) {
        AlertDialog(
            onDismissRequest = { showCatDialog = false },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = newCat,
                    onValueChange = { newCat = it },
                    label = { Text("分类名称") },
                    placeholder = { Text("例如：健身") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCat.trim().isNotEmpty()) {
                        vm.addCategory(newCat)
                        newCat = ""
                        showCatDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showCatDialog = false }) { Text("取消") }
            }
        )
    }
    showImageViewer?.let { path ->
        AlertDialog(
            onDismissRequest = { showImageViewer = null },
            title = { Text("查看图片") },
            text = {
                Box(Modifier.fillMaxWidth()) {
                    Thumb(path = path, size = 300.dp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageViewer = null }) { Text("关闭") }
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除事项") },
            text = { Text("确定删除「${draft.title}」吗？其录音与图片附件将一并删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteDraftTask()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃编辑？") },
            text = {
                Text("本次修改尚未保存。放弃后，已填写的内容和新拍摄/选择的图片、录音将被丢弃。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    vm.discardDraft()
                }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") }
            }
        )
    }
}


/** 图片添加按钮（虚线风格方块） */
@Composable
private fun ImageAddButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier.size(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, Modifier.size(26.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
