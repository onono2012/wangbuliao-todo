package com.wangbuliao.todo.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
 * 置顶通知（v1.5.4 起为「普通常驻通知」直发，不再是前台服务）：
 * 在系统通知栏最上方常驻一条「待办速览」，折叠态直接露出前 2 条标题，
 * 展开显示最多 8 条明细，点击直达 App。开关在设置页。
 *
 * 改造原因：ColorOS/realme UI 禁止用户展开前台服务通知，
 * InboxStyle 明细永远不可见（下拉点箭头无反应）；
 * 普通 ongoing 通知与微信等同等待遇，可正常展开看明细。
 * 进程保活职责由 KeepAliveService（前台服务）承担。
 * 保留原 start/stop/refresh 调用签名，调用方零改动。
 */
object PinNotifService {

    private const val TAG = "WblPinNotif"
    private const val NOTIF_ID = 9002

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 兼容旧调用：开启置顶栏 = 立即发布/刷新通知 */
    fun start(ctx: Context) = refresh(ctx)

    fun stop(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    /** 数据变化后刷新置顶通知（仅在开关开启时发布） */
    fun refresh(ctx: Context) {
        try {
            if (!com.wangbuliao.todo.util.Prefs.pinNotif.value) return
        } catch (_: Exception) {
            return
        }
        val app = ctx.applicationContext
        scope.launch {
            try {
                Notif.ensureChannels(app)
                // 待办明细（展开可见）：置顶 > 紧急 > 提醒时间升序 > 创建时间倒序
                val pending = TaskRepo.tasks().filter { !it.done }
                    .sortedWith(
                        compareByDescending<Task> { it.pinned }
                            .thenByDescending { it.urgent }
                            .thenBy { if (it.remindAt > 0) it.remindAt else Long.MAX_VALUE }
                            .thenByDescending { it.createdAt }
                    )
                val nm = app.getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIF_ID, buildNotification(app, pending))
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
            }
        }
    }

    private fun buildNotification(ctx: Context, pending: List<Task>): android.app.Notification {
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 7, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = pending.size
        val next = pending.firstOrNull { it.remindAt > 0 }
        // 折叠态直接露出前 2 条标题（ColorOS 折叠视图也能看到关键待办）
        val brief = pending.take(2).joinToString(" · ") { it.title.ifBlank { "（无标题）" } }
        val text = when {
            count == 0 -> "全部办完了 ✨ 点一下记点新东西"
            count <= 2 -> brief
            next != null -> "$brief 等 $count 条 · ${TimeFmt.remind(next.remindAt)}"
            else -> "$brief 等 $count 条"
        }
        val b = NotificationCompat.Builder(ctx, Notif.CH_PIN)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("📌 忘不了 · $count 条待办")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setSortKey("0_wbl_pin")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        // 展开明细：最多 8 条，标注 置顶/紧急/提醒时间
        if (pending.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("📌 待办 $count 条")
            pending.take(8).forEach { t ->
                inbox.addLine(detailLine(t))
            }
            inbox.setSummaryText(
                if (count > 8) "…等 $count 条 · 点按打开 App" else "点按打开 App"
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
}
