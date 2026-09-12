package com.wangbuliao.todo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangbuliao.todo.util.TimeFmt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 简易日期时间选择（不依赖 m3 DatePicker，兼容 material3 1.1.2）：
 *  日期=未来90天横滑；小时/分钟=横滑芯片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindPickerDialog(
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
    val isNew = draft.id <= 0
    var showPicker by remember { mutableStateOf(false) }
    var showCatDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "记一笔" else "编辑事项") },
                navigationIcon = {
                    IconButton(onClick = { vm.backList() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Outlined.Delete, "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxWidth()
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
            Spacer(Modifier.height(14.dp))

            Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.categories.forEach { c ->
                    FilterChip(
                        selected = draft.category == c,
                        onClick = { vm.updateDraft { it.copy(category = c) } },
                        label = { Text(c) }
                    )
                }
                OutlinedButton(onClick = { showCatDialog = true }) { Text("＋ 新分类") }
            }
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("紧急", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    TextButton(onClick = { vm.updateDraft { it.copy(remindAt = 0) } }) {
                        Text("清除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { vm.saveDraft() },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (isNew) "保存" else "保存修改", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
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
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除事项") },
            text = { Text("确定删除「${draft.title}」吗？删除后不可恢复。") },
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
}
