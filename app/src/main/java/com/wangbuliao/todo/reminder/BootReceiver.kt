package com.wangbuliao.todo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangbuliao.todo.floatwin.FloatingNoteService
import com.wangbuliao.todo.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机完成 / 本应用被覆盖安装后：恢复提醒闹钟 + 悬浮窗 + 置顶通知 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(app)
            } catch (e: Exception) {
                Log.e("WblBoot", "reschedule failed", e)
            }
            try {
                // 悬浮窗（需已授权且开关开启）
                if (Prefs.floatEnabled.value && FloatingNoteService.canDraw(app)) {
                    FloatingNoteService.start(app)
                }
                // 置顶通知
                if (Prefs.pinNotif.value) {
                    PinNotifService.start(app)
                }
                // 后台保活
                if (Prefs.keepAlive.value) {
                    KeepAliveService.start(app)
                }
            } catch (e: Exception) {
                Log.e("WblBoot", "restore services failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
