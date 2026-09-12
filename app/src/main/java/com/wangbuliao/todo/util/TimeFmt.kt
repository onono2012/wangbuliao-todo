package com.wangbuliao.todo.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFmt {
    /** 提醒时间友好显示：今天 HH:mm / M月d日 HH:mm / yyyy年M月d日 HH:mm */
    fun remind(ts: Long): String {
        if (ts <= 0) return ""
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val d = Date(ts)
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        return when {
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> "今天 $hm"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) ->
                SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(d)
            else -> SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(d)
        }
    }
}
