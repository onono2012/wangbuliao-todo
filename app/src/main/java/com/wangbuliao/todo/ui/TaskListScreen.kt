package com.wangbuliao.todo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.util.RepeatRule
import com.wangbuliao.todo.util.TimeFmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(vm: MainViewModel, ui: UiState) {
    val spec = LocalWblTheme.current
    var showQuickNote by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    WblScreenBackground {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            // 主题化顶栏：照片主题=深色遮罩，渐变主题=主题渐变，其余=默认纯色
            val themed = wblTopBarThemed()
            val tbc = wblTopBarContentColor()
            TopAppBar(
                title = {
                    // 标题 + 今日农历/节气副标题（每天自动更新）
                    val now = remember { java.util.Calendar.getInstance() }
                    val y = now.get(java.util.Calendar.YEAR)
                    val m = now.get(java.util.Calendar.MONTH) + 1
                    val d = now.get(java.util.Calendar.DAY_OF_MONTH)
                    val sub = remember(y, m, d) {
                        val ld = com.wangbuliao.todo.util.LunarUtil.solar2lunar(y, m, d)
                        val term = com.wangbuliao.todo.util.LunarUtil.termNameOn(y, m, d)
                        buildString {
                            append("$m 月 $d 日 · ")
                            append(com.wangbuliao.todo.util.LunarUtil.weekName(y, m, d))
                            if (ld != null) {
                                append(" · 农历")
                                append(
                                    if (term != null) term
                                    else if (ld.isChuxi) "除夕"
                                    else if (ld.isSpringFestival) "春节(正月初一)"
                                    else ld.monthName + ld.dayName
                                )
                            }
                        }
                    }
                    Column {
                        Text(
                            "忘不了",
                            color = tbc
                        )
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (themed) tbc
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    val tint = if (themed) tbc else MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索", tint = tint)
                    }
                    IconButton(onClick = { showQuickNote = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "随手记", tint = tint)
                    }
                    // 设置入口已移至底部导航栏
                },
                colors = if (themed) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = tbc,
                        actionIconContentColor = tbc
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                modifier = wblTopBarModifier()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openNew() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // 搜索框
            if (showSearch) {
                OutlinedTextField(
                    value = ui.search,
                    onValueChange = { vm.setSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索标题 / 内容 / 分类") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (ui.search.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearch("") }) {
                                Icon(Icons.Filled.Close, "清空")
                            }
                        }
                    }
                )
            }
            // 状态筛选行
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFilter.values().forEach { f ->
                    FilterChip(
                        colors = wblChipColors(),
                        selected = ui.status == f,
                        onClick = { vm.setStatus(f) },
                        label = { Text(f.label) },
                        leadingIcon = {
                            Icon(statusIcon(f), null, Modifier.size(16.dp))
                        }
                    )
                }
                FilterChip(
                    colors = wblChipColors(),
                    selected = ui.urgentOnly,
                    onClick = { vm.toggleUrgentOnly() },
                    label = { Text("仅紧急") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.LocalFireDepartment, null,
                            Modifier.size(16.dp)
                        )
                    }
                )
            }
            // 分类筛选行（⑦ 直观化：chip 直接带待办数徽标，一眼看清各分类负载）
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalPending = ui.allTasks.count { !it.done }
                FilterChip(
                    colors = wblChipColors(),
                    selected = ui.category == null,
                    onClick = { vm.setCategoryFilter(null) },
                    label = {
                        Text(if (totalPending > 0) "全部分类 ·$totalPending" else "全部分类")
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Category, null, Modifier.size(16.dp))
                    }
                )
                ui.categories.forEach { c ->
                    val n = ui.allTasks.count { !it.done && it.category == c }
                    FilterChip(
                        colors = wblChipColors(),
                        selected = ui.category == c,
                        onClick = { vm.setCategoryFilter(c) },
                        label = { Text(if (n > 0) "$c ·$n" else c) },
                        leadingIcon = {
                            Icon(categoryIcon(c), null, Modifier.size(16.dp))
                        }
                    )
                }
            }
            // ⑦ 选中某分类时：分类汇总卡（配色图标 + 待办/已办计数 + 完成进度条）
            ui.category?.let { cat ->
                val pending = ui.allTasks.count { !it.done && it.category == cat }
                val doneN = ui.allTasks.count { it.done && it.category == cat }
                val total = pending + doneN
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = wblCardColor())
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(categoryColor(cat)),
                            Alignment.Center
                        ) {
                            Icon(
                                categoryIcon(cat), null,
                                Modifier.size(20.dp), tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                cat,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$pending 条待办 · $doneN 条已办",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (total > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = doneN.toFloat() / total.toFloat(),
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = categoryColor(cat),
                                    trackColor = categoryColor(cat).copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }
            val visible = ui.visible
            when {
                ui.loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                visible.isEmpty() -> Box(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = 80.dp),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.EventNote, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                ui.search.isNotBlank() -> "没有匹配「${ui.search.trim()}」的事项"
                                ui.allTasks.isEmpty() -> "还没有事项，点右下角「记一笔」开始\n顶栏 ✎ 或悬浮球可随手记"
                                else -> "该筛选条件下没有事项"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { t -> TaskCard(t, vm) }
                }
            }
        }
    }
    }

    if (showQuickNote) {
        QuickNoteDialog(
            onDismiss = { showQuickNote = false },
            onSave = { vm.quickNote(it) }
        )
    }
}

/** 随手记对话框：一行/多行文本立即保存（分类=随手记） */
@Composable
fun QuickNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EditNote, null,
                    Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("随手记")
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("此刻想到什么？直接记下来…") },
                minLines = 2,
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) onSave(t)
                    onDismiss()
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(task: Task, vm: MainViewModel) {
    val now = System.currentTimeMillis()
    val dark = wblIsDark()
    val highlight = task.urgent && !task.done
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                    alpha = if (wblHasPhotoBg()) 0.78f else 0.62f
                )
            )
        } else {
            CardDefaults.cardColors(containerColor = wblCardColor())
        }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = { vm.openEdit(task.id) },
                    onLongClick = { vm.togglePinned(task) }
                )
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { vm.toggleDone(task) },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.pinned && !task.done) {
                        Icon(
                            Icons.Outlined.PushPin, "已置顶",
                            Modifier.size(15.dp).padding(end = 3.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (task.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        task.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 分类标签
                    val cc = categoryColor(task.category)
                    Surface(
                        color = cc.copy(alpha = if (dark) 0.28f else 0.14f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            task.category,
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = categoryTextColor(task.category, dark)
                        )
                    }
                    // 紧急标记
                    if (task.urgent && !task.done) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "紧急",
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                    // 录音徽章
                    if (task.audioPath.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Mic, "语音",
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            TimeFmt.dur(task.audioDur),
                            fontSize = 11.sp,
                            color = wblAccentColor()
                        )
                    }
                    // 图片徽章
                    if (task.images.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Image, "图片",
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "${task.images.size}图",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    // 提醒时间（重复任务附「每天/每周/每月」标签）
                    if (task.remindAt > 0 && !task.done) {
                        val overdue = task.remindAt < now
                        Icon(
                            Icons.Outlined.Notifications, null,
                            Modifier.size(14.dp),
                            tint = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val repeatLabel = RepeatRule.label(task.repeat)
                        Text(
                            TimeFmt.remind(task.remindAt) +
                                (if (repeatLabel.isNotEmpty()) " · $repeatLabel" else "") +
                                if (overdue) "（已过）" else "",
                            fontSize = 11.sp,
                            color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(Modifier.align(Alignment.CenterVertically)) {
                IconButton(
                    onClick = { vm.openEdit(task.id) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit, "编辑",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete, "删除",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除事项") },
            text = { Text("确定删除「${task.title}」吗？其录音与图片附件将一并删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteTask(task)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
