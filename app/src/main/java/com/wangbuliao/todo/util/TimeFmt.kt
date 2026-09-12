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

    /** 短时间（卡片用）：今天 HH:mm / 昨天 HH:mm / M月d日 / yyyy年M月d日 */
    fun short(ts: Long): String {
        if (ts <= 0) return ""
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val d = Date(ts)
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> "今天 $hm"
            yesterday.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) -> "昨天 $hm"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) ->
                SimpleDateFormat("M月d日", Locale.getDefault()).format(d)
            else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(d)
        }
    }

    /** 时长 mm:ss（录音/播放） */
    fun dur(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }
}
