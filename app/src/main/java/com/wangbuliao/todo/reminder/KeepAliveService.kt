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
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 后台保活服务：低优先级前台服务常驻，最大限度避免被系统杀后台。
 * 状态通知展示未完成事项清单（最多 5 条 + 数量汇总）；
 * v1.5.4：置顶栏改为普通通知后不再承担保活，本服务是唯一的常驻前台服务；
 * 置顶栏开启时本通知自动极简化（明细看置顶栏，避免两条重复）。
 * 点击通知或「打开管理器」直达 App 管理界面。
 * START_STICKY：被杀后系统自动重建；开机由 BootReceiver 恢复。
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notif.ensureChannels(this)
        startForegroundCompat(buildNotification(-1, emptyList()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val tasks = TaskRepo.tasks()
                val pending = tasks.filter { !it.done }
                    .sortedWith(
                        Comparator { a, b ->
                            if (a.urgent != b.urgent) return@Comparator if (a.urgent) -1 else 1
                            val ra = if (a.remindAt > 0) a.remindAt else Long.MAX_VALUE
                            val rb = if (b.remindAt > 0) b.remindAt else Long.MAX_VALUE
                            if (ra != rb) return@Comparator ra.compareTo(rb)
                            b.createdAt.compareTo(a.createdAt)
                        }
                    )
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(
                    NOTIF_ID,
                    buildNotification(pending.size, pending.take(5).map { it.title.ifEmpty { "未命名" } })
                )
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(pendingCount: Int, items: List<String>): android.app.Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 11, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 「打开管理器」动作：直达设置页管理保活/置顶/悬浮窗
        val mgr = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SETTINGS, true)
        }
        val mgrPi = PendingIntent.getActivity(
            this, 12, mgr,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // v1.5.4：置顶栏开启时本通知极简化（待办明细看置顶栏，避免两条通知重复列清单）
        val pinOn = try {
            com.wangbuliao.todo.util.Prefs.pinNotif.value
        } catch (_: Exception) {
            false
        }
        val text = when {
            pinOn -> "后台守护运行中 · 待办明细见置顶栏"
            pendingCount < 0 -> "正在同步…"
            pendingCount == 0 -> "全部办完了 ✨ 点一下记点新东西"
            else -> "$pendingCount 条未完成 · " + items.first()
        }
        val inbox = NotificationCompat.InboxStyle()
        items.forEach { inbox.addLine("· $it") }
        inbox.setSummaryText(
            if (pendingCount > items.size) "…等 $pendingCount 条未完成" else "点按打开管理器"
        )
        return NotificationCompat.Builder(this, Notif.CH_KEEP)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("🛡️ 忘不了 · 后台守护中")
            .setContentText(text)
            .setStyle(
                if (pinOn || items.isEmpty()) NotificationCompat.BigTextStyle().bigText(text)
                else inbox
            )
            .setContentIntent(pi)
            .addAction(0, "打开管理器", mgrPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
        private const val TAG = "WblKeepAlive"
        private const val NOTIF_ID = 9003
        const val EXTRA_OPEN_SETTINGS = "wbl_open_settings"

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }

        /** 数据变化后刷新状态通知（仅在服务运行时有效）。
         *  v1.5.5 通知去重：悬浮窗开着时其前台服务已承担保活（同为 START_STICKY），
         *  守护通知自动休眠——同一时刻最多一条"服务类"通知，避免通知栏刷屏。 */
        fun refresh(ctx: Context) {
            val floatOn = try {
                com.wangbuliao.todo.util.Prefs.floatEnabled.value
            } catch (_: Exception) {
                false
            }
            if (floatOn) {
                stop(ctx)
            } else if (com.wangbuliao.todo.util.Prefs.keepAlive.value) {
                start(ctx)
            }
        }
    }
}
