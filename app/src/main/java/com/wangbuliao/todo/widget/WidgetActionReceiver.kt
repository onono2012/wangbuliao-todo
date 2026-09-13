package com.wangbuliao.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wangbuliao.todo.data.DbHelper
import com.wangbuliao.todo.reminder.KeepAliveService
import com.wangbuliao.todo.reminder.PinNotifService

/**
 * 小组件勾选广播：切换任务已办状态，随后刷新小组件与常驻通知。
 * 数据库操作走 goAsync + 子线程，避免阻塞广播队列。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val id = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (id <= 0L) return
        val app = ctx.applicationContext
        val async = goAsync()
        Thread {
            try {
                val db = DbHelper.get(app)
                val t = db.getTask(id)
                if (t != null) {
                    db.setDone(id, !t.done, System.currentTimeMillis())
                }
                WblWidgetProvider.updateAll(app)
                // 与应用内行为一致：开关开启时同步刷新置顶通知/保活状态通知
                PinNotifService.refresh(app)
                KeepAliveService.refresh(app)
            } catch (_: Exception) {
            } finally {
                async.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_TOGGLE = "com.wangbuliao.todo.widget.TOGGLE_DONE"
        const val EXTRA_TASK_ID = "widget_task_id"
    }
}
