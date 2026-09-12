package com.wangbuliao.todo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangbuliao.todo.data.TaskRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 提醒闹钟触发：标记已提醒 + 发通知 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0L)
        val title = intent.getStringExtra("title") ?: "待办提醒"
        val note = intent.getStringExtra("note") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val urgent = intent.getBooleanExtra("urgent", false)
        val app = context.applicationContext
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
