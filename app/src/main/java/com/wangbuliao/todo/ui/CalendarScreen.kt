package com.wangbuliao.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.util.LunarUtil
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日历页：公历 + 农历 + 节气；点击某天查看当天事项、为当天新增事项。
 * 事项归属规则：设了提醒时间 → 按提醒日；未设提醒 → 按创建日。
 */
@Composable
fun CalendarScreen(vm: MainViewModel, ui: UiState) {
    val today = remember { LocalDate.now() }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthValue) }
    var selected by remember { mutableStateOf(today) }

    // 有事项的日期集合（epochDay）
    val busyDays = remember(ui.allTasks) {
        val zone = ZoneId.systemDefault()
        ui.allTasks.mapNotNull { t ->
            val millis = if (t.remindAt > 0) t.remindAt else t.createdAt
            if (millis <= 0) null
            else java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        }.toSet()
    }

    WblScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        vm.openNewForDate(selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("${selected.monthValue}月${selected.dayOfMonth}日记一笔") }
                )
            }
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                // ── 月份切换栏 ──
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                    }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
                    Spacer(Modifier.width(2.dp))
                    val lunarOfFirst = remember(year, month) { LunarUtil.solar2lunar(year, month, 1) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$year 年 $month 月",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            lunarOfFirst?.let {
                                "农历" + LunarUtil.ganZhiYear(it.lunarYear) +
                                    LunarUtil.zodiacOf(it.lunarYear) + "年 · " + it.monthName
                            } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    IconButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                    }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
                    TextButton(onClick = {
                        year = today.year; month = today.monthValue; selected = today
                    }) { Text("今天") }
                }

                // ── 星期表头 ──
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { i, w ->
                        Text(
                            w,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (i == 0 || i == 6) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── 月历网格 ──
                val cells = remember(year, month) { buildMonthCells(year, month) }
                val card = wblCardColor()
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { cell ->
                                    Box(Modifier.weight(1f)) {
                                        DayCell(
                                            cell = cell,
                                            month = month,
                                            isToday = cell.date == today,
                                            isSelected = cell.date == selected,
                                            hasTask = cell.date != null && busyDays.contains(cell.date.toEpochDay()),
                                            onClick = { cell.date?.let { selected = it } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── 选中日详情 ──
                DayDetail(
                    date = selected,
                    tasks = remember(ui.allTasks, selected) {
                        val zone = ZoneId.systemDefault()
                        ui.allTasks.filter { t ->
                            val millis = if (t.remindAt > 0) t.remindAt else t.createdAt
                            millis > 0 && java.time.Instant.ofEpochMilli(millis)
                                .atZone(zone).toLocalDate() == selected
                        }.sortedWith(compareBy({ it.done }, { it.remindAt }, { -it.createdAt }))
                    },
                    onTaskClick = { vm.openEdit(it.id) },
                    onAdd = {
                        vm.openNewForDate(selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class MonthCell(val date: LocalDate?)

/** 构建当月网格（周日起始，含前后补白） */
private fun buildMonthCells(year: Int, month: Int): List<MonthCell> {
    val first = LocalDate.of(year, month, 1)
    val lead = first.dayOfWeek.value % 7  // 周日=0
    val days = first.lengthOfMonth()
    val cells = ArrayList<MonthCell>()
    repeat(lead) { cells.add(MonthCell(null)) }
    for (d in 1..days) cells.add(MonthCell(LocalDate.of(year, month, d)))
    while (cells.size % 7 != 0) cells.add(MonthCell(null))
    return cells
}

@Composable
private fun DayCell(
    cell: MonthCell,
    month: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasTask: Boolean,
    onClick: () -> Unit
) {
    val d = cell.date
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (d != null) Modifier
                    .border(
                        if (isSelected) 1.6.dp else 0.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (d != null) {
            val lunar = remember(d) { LunarUtil.solar2lunar(d.year, d.monthValue, d.dayOfMonth) }
            val term = remember(d) { LunarUtil.termNameOn(d.year, d.monthValue, d.dayOfMonth) }
            val highlight = remember(d) { LunarUtil.cellHighlight(d.year, d.monthValue, d.dayOfMonth) }
            val weekend = d.dayOfWeek.value >= 6
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${d.dayOfMonth}",
                        fontSize = 14.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            weekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Text(
                    term ?: run {
                        if (lunar == null) "" else when {
                            lunar.isChuxi -> "除夕"
                            lunar.isSpringFestival -> "春节"
                            lunar.lunarDay == 1 -> lunar.monthName
                            else -> lunar.dayName
                        }
                    },
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.primary
                        highlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        weekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    }
                )
                // 有事项的小圆点
                Box(
                    Modifier
                        .size(4.dp)
                        .background(
                            if (hasTask) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = wblCardColor()
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val lunar = remember(date) { LunarUtil.solar2lunar(date.year, date.monthValue, date.dayOfMonth) }
            val term = remember(date) { LunarUtil.termNameOn(date.year, date.monthValue, date.dayOfMonth) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append("${date.year}年${date.monthValue}月${date.dayOfMonth}日 ")
                            append(LunarUtil.weekName(date.year, date.monthValue, date.dayOfMonth))
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    lunar?.let {
                        Text(
                            buildString {
                                append("农历").append(it.fullText.substringAfter("年"))
                                append(" · ").append(it.ganZhiText)
                                if (it.isChuxi) append(" · 除夕")
                                if (it.isSpringFestival) append(" · 春节")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (term != null) {
                        Text(
                            "节气 · $term",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("新增")
                }
            }
            Spacer(Modifier.height(4.dp))
            if (tasks.isEmpty()) {
                Text(
                    "这一天还没有事项，点右上「新增」记一笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 260.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tasks.size) { i ->
                        val t = tasks[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onTaskClick(t) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(categoryColor(t.category), CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title.ifEmpty { "（无标题）" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (t.remindAt > 0) {
                                        Icon(
                                            Icons.Outlined.Notifications, null,
                                            Modifier.size(11.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            java.text.SimpleDateFormat(
                                                if (t.remindAt > 0) "HH:mm" else "",
                                                java.util.Locale.getDefault()
                                            ).format(java.util.Date(t.remindAt)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (t.audioPath.isNotEmpty()) {
                                        Icon(
                                            Icons.Outlined.Mic, null,
                                            Modifier.size(11.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (t.images.isNotEmpty()) {
                                        Icon(
                                            Icons.Outlined.Image, null,
                                            Modifier.size(11.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (t.done) {
                                Text(
                                    "已办",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            } else if (t.urgent) {
                                Text(
                                    "紧急",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
