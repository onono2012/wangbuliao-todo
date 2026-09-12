package com.wangbuliao.todo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.util.TimeFmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(vm: MainViewModel, ui: UiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("忘不了") },
                actions = {
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
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
            // 状态筛选行
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFilter.values().forEach { f ->
                    FilterChip(
                        selected = ui.status == f,
                        onClick = { vm.setStatus(f) },
                        label = { Text(f.label) }
                    )
                }
                FilterChip(
                    selected = ui.urgentOnly,
                    onClick = { vm.toggleUrgentOnly() },
                    label = { Text("仅紧急") }
                )
            }
            // 分类筛选行
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ui.category == null,
                    onClick = { vm.setCategoryFilter(null) },
                    label = { Text("全部分类") }
                )
                ui.categories.forEach { c ->
                    FilterChip(
                        selected = ui.category == c,
                        onClick = { vm.setCategoryFilter(c) },
                        label = { Text(c) }
                    )
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
                    Text(
                        if (ui.allTasks.isEmpty()) "还没有事项，点右下角「记一笔」开始"
                        else "该筛选条件下没有事项",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(task: Task, vm: MainViewModel) {
    val now = System.currentTimeMillis()
    val dark = isSystemInDarkTheme()
    val highlight = task.urgent && !task.done
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = { vm.openEdit(task.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { vm.toggleDone(task) },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
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
                    // 提醒时间
                    if (task.remindAt > 0 && !task.done) {
                        val overdue = task.remindAt < now
                        Icon(
                            Icons.Outlined.Notifications, null,
                            Modifier.size(14.dp),
                            tint = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            TimeFmt.remind(task.remindAt) + if (overdue) "（已过）" else "",
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
            text = { Text("确定删除「${task.title}」吗？删除后不可恢复。") },
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
