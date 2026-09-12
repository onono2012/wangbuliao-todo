package com.wangbuliao.todo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.data.TaskRepo

object AlarmScheduler {
    private const val TAG = "WblAlarm"

    private fun am(ctx: Context): AlarmManager? =
        ctx.getSystemService(AlarmManager::class.java)

    /** 是否可用精确闹钟（API31+ 需授权；API33+ 声明 USE_EXACT_ALARM 自动授予） */
    fun canExact(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am(ctx)?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    private fun intent(ctx: Context, t: Task): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("task_id", t.id)
            putExtra("title", t.title)
            putExtra("note", t.note)
            putExtra("category", t.category)
            putExtra("urgent", t.urgent)
        }
        return PendingIntent.getBroadcast(
            ctx, t.id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 安排提醒；无提醒或已办则取消 */
    fun schedule(ctx: Context, t: Task) {
        if (t.remindAt <= 0 || t.done) {
            cancel(ctx, t)
            return
        }
        val am = am(ctx) ?: return
        val trigger = if (t.remindAt > System.currentTimeMillis()) {
            t.remindAt
        } else {
            // 已过期未提醒：1.5 秒后立即触发
            System.currentTimeMillis() + 1500L
        }
        try {
            if (canExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent(ctx, t))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent(ctx, t))
                Log.w(TAG, "精确闹钟未授权，使用非精确提醒 taskId=${t.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "schedule failed, fallback set()", e)
            try {
                am.set(AlarmManager.RTC_WAKEUP, trigger, intent(ctx, t))
            } catch (e2: Exception) {
                Log.e(TAG, "fallback set failed", e2)
            }
        }
    }

    fun cancel(ctx: Context, t: Task) {
        try {
            am(ctx)?.cancel(intent(ctx, t))
        } catch (e: Exception) {
            Log.e(TAG, "cancel failed", e)
        }
    }

    /** 开机/应用更新后恢复所有未完成提醒；已过期的立即补发通知 */
    suspend fun rescheduleAll(ctx: Context) {
        try {
            val now = System.currentTimeMillis()
            TaskRepo.pendingReminders().forEach { t ->
                if (t.remindAt > now) {
                    schedule(ctx, t)
                } else if (!t.reminded) {
                    Notif.remind(ctx, t.id, t.title, t.note, t.category, t.urgent)
                    TaskRepo.markReminded(t.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "rescheduleAll failed", e)
        }
    }
}
