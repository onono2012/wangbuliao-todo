package com.wangbuliao.todo.util

import java.util.Calendar

/**
 * 重复任务规则（纯函数，便于单测）。
 * repeat 取值：0=不重复，1=每天，2=每周，3=每月。
 * 语义：重复必须依附提醒时间（remindAt>0）；完成本期后自动滚动到下一周期。
 */
object RepeatRule {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3

    /** UI 选项（编辑页 chips） */
    val OPTIONS = listOf(
        NONE to "不重复",
        DAILY to "每天",
        WEEKLY to "每周",
        MONTHLY to "每月"
    )

    /** 简短标签（列表卡片/小组件角标），不重复返回空串 */
    fun label(repeat: Int): String = when (repeat) {
        DAILY -> "每天"
        WEEKLY -> "每周"
        MONTHLY -> "每月"
        else -> ""
    }

    /**
     * 从 from 出发按周期推进，返回第一个严格晚于 now 的时间点。
     * 不重复 / from<=0 / 非法 repeat 一律返回 0（表示不滚动）。
     * 每月推进用 Calendar.add(MONTH,1)，自动处理大小月与闰年（1月31日→2月28/29日）。
     * guard 上限防御异常数据导致的死循环。
     */
    fun next(from: Long, repeat: Int, now: Long): Long {
        if (repeat == NONE || from <= 0L) return 0L
        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        var guard = 0
        do {
            when (repeat) {
                DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
                WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                MONTHLY -> cal.add(Calendar.MONTH, 1)
                else -> return 0L
            }
        } while (cal.timeInMillis <= now && ++guard < 1000)
        return if (cal.timeInMillis > now) cal.timeInMillis else 0L
    }
}
