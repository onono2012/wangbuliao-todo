package com.wangbuliao.todo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangbuliao.todo.data.TaskRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 提醒闹钟触发：标记已提醒 + 发通知。
 * 通知栏动作：DONE=直接完成；SNOOZE=10 分钟后再提醒（改期闹钟）。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0L)
        val app = context.applicationContext
        when (intent.action) {
            ACTION_DONE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (taskId > 0) {
                            TaskRepo.get(taskId)?.let { t ->
                                AlarmScheduler.cancel(app, t)
                                TaskRepo.setDone(taskId, true)
                            }
                            Notif.cancelRemind(app, taskId)
                            PinNotifService.refresh(app)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(app)
                        }
                    } catch (e: Exception) {
                        Log.e("WblReminder", "done action failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                val pending = goAsync()
                // 稍后提醒：通知动作携带 snooze_min（5/10 分钟），默认 10
                val snoozeMin = intent.getIntExtra("snooze_min", 10).coerceIn(1, 120)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (taskId > 0) {
                            val t = TaskRepo.get(taskId)
                            if (t != null) {
                                val at = System.currentTimeMillis() + snoozeMin * 60 * 1000L
                                TaskRepo.setRemindAt(taskId, at)
                                AlarmScheduler.schedule(app, t.copy(remindAt = at))
                            }
                            Notif.cancelRemind(app, taskId)
                        }
                    } catch (e: Exception) {
                        Log.e("WblReminder", "snooze action failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> {
                // 闹钟触发：发提醒通知
                val title = intent.getStringExtra("title") ?: "待办提醒"
                val note = intent.getStringExtra("note") ?: ""
                val category = intent.getStringExtra("category") ?: ""
                val urgent = intent.getBooleanExtra("urgent", false)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (taskId > 0) TaskRepo.markReminded(taskId)
                        Notif.remind(app, taskId, title, note, category, urgent)
                    } catch (e: Exception) {
                        Log.e("WblReminder", "remind failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.wangbuliao.todo.remind.DONE"
        const val ACTION_SNOOZE = "com.wangbuliao.todo.remind.SNOOZE"
    }
}
