package com.wangbuliao.todo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.reminder.KeepAliveService
import com.wangbuliao.todo.reminder.PinNotifService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 小组件广播接收器：
 * - [ACTION_TOGGLE]（勾选圈大热区）：切换任务已办状态，随后刷新小组件与常驻通知。
 *   走 TaskRepo.setDone 以保证与应用内/通知栏行为一致——重复任务会自动
 *   滚动到下一周期并重排闹钟，而不是简单置为已办。
 * - 行点击（fill-in 携带 [EXTRA_OPEN_ID]）：打开 App 直达该任务编辑页。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        // 行点击 → 打开指定任务（无需异步）
        val openId = intent.getLongExtra(EXTRA_OPEN_ID, 0L)
        if (openId > 0L) {
            try {
                val open = Intent(app, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_TASK_ID, openId)
                app.startActivity(open)
            } catch (_: Exception) {
            }
            return
        }
        if (intent.action != ACTION_TOGGLE) return
        val id = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (id <= 0L) return
        val async = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = TaskRepo.get(id)
                if (t != null) TaskRepo.setDone(id, !t.done)
                // 与应用内行为一致：开关开启时同步刷新置顶通知/保活状态通知
                PinNotifService.refresh(app)
                KeepAliveService.refresh(app)
            } catch (_: Exception) {
            } finally {
                async.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.wangbuliao.todo.widget.TOGGLE_DONE"
        const val EXTRA_TASK_ID = "widget_task_id"
        const val EXTRA_OPEN_ID = "widget_open_id"
    }
}
