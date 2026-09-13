package com.wangbuliao.todo.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.R
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 置顶通知服务：在系统通知栏最上方常驻一条「待办速览」通知，
 * 显示未完成数量与最近提醒，点击直达 App。开关在设置页。
 * 渠道 IMPORTANCE_HIGH + sortKey 置顶 + onlyAlertOnce 不重复响铃。
 */
class PinNotifService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notif.ensureChannels(this)
        // 先以占位内容进入前台（5 秒规则），再异步刷新明细
        startForegroundCompat(buildNotification(null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // 待办明细（展开可见）：置顶 > 紧急 > 提醒时间升序 > 创建时间倒序
                val pending = TaskRepo.tasks().filter { !it.done }
                    .sortedWith(
                        compareByDescending<Task> { it.pinned }
                            .thenByDescending { it.urgent }
                            .thenBy { if (it.remindAt > 0) it.remindAt else Long.MAX_VALUE }
                            .thenByDescending { it.createdAt }
                    )
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIF_ID, buildNotification(pending))
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
            }
        }
        return START_STICKY
    }

    /** pending: 排序后的未完成清单；null = 占位（正在同步） */
    private fun buildNotification(pending: List<Task>?): android.app.Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 7, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = pending?.size ?: -1
        val next = pending?.firstOrNull { it.remindAt > 0 }
        val text = when {
            count < 0 -> "正在同步…"
            count == 0 -> "全部办完了 ✨ 点一下记点新东西"
            next != null -> "$count 条待办 · 最近提醒 ${TimeFmt.remind(next.remindAt)}"
            else -> "$count 条待办 · 下拉展开看明细"
        }
        val b = NotificationCompat.Builder(this, Notif.CH_PIN)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("📌 忘不了 · 待办速览")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setSortKey("0_wbl_pin")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        // 展开明细：最多 6 条，标注 置顶/紧急/提醒时间
        if (pending != null && pending.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("📌 待办 $count 条")
            pending.take(6).forEach { t ->
                inbox.addLine(detailLine(t))
            }
            inbox.setSummaryText(
                if (count > 6) "…等 $count 条 · 点按打开 App" else "点按打开 App"
            )
            b.setStyle(inbox)
        }
        return b.build()
    }

    private fun detailLine(t: Task): String = buildString {
        append(
            when {
                t.pinned -> "📌 "
                t.urgent -> "🔥 "
                t.remindAt > 0 -> "⏰ "
                else -> "· "
            }
        )
        append(t.title.ifBlank { "（无标题）" })
        if (t.remindAt > 0) append("　${TimeFmt.remind(t.remindAt)}")
    }

    private fun startForegroundCompat(n: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "WblPinNotif"
        private const val NOTIF_ID = 9002

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, PinNotifService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, PinNotifService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }

        /** 数据变化后刷新置顶通知内容（仅在服务已运行时有效） */
        fun refresh(ctx: Context) {
            if (com.wangbuliao.todo.util.Prefs.pinNotif.value) start(ctx)
        }
    }
}
