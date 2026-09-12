package com.wangbuliao.todo.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.R
import com.wangbuliao.todo.util.Prefs

object Notif {
    private const val TAG = "WblNotif"
    const val CH_PIN = "wbl_pin"
    const val CH_FLOAT = "wbl_float"

    /** 提醒渠道 id 随铃声版本变化（Android 渠道声音创建后不可改） */
    fun remindChannelId(): String = "wbl_reminder_v${Prefs.ringVer}"

    fun ensureChannels(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

            // 清理旧版本提醒渠道（换铃声后 ver+1）
            val cur = remindChannelId()
            nm.notificationChannels?.forEach { ch ->
                if (ch.id.startsWith("wbl_reminder") && ch.id != cur) {
                    try {
                        nm.deleteNotificationChannel(ch.id)
                    } catch (_: Exception) {
                    }
                }
            }

            // 提醒渠道：高优先级弹出 + 自定义铃声 + 震动
            val soundUri = Prefs.ringUri.value?.let { safeParse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val remindCh = NotificationChannel(
                cur, "记事提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到期提醒：弹出横幅 + 铃声 + 震动"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                if (soundUri != null) {
                    setSound(
                        soundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            nm.createNotificationChannel(remindCh)

            // 置顶通知渠道（常驻，onlyAlertOnce 避免每次更新都响）
            val pinCh = NotificationChannel(
                CH_PIN, "待办置顶栏", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "常驻系统通知栏最上方的待办速览"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(pinCh)

            // 悬浮窗前台服务渠道（低优先级不打扰）
            val floatCh = NotificationChannel(
                CH_FLOAT, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "随手记悬浮窗运行状态"
                setShowBadge(false)
            }
            nm.createNotificationChannel(floatCh)
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannels failed", e)
        }
    }

    private fun safeParse(s: String): Uri? =
        try {
            Uri.parse(s)
        } catch (e: Exception) {
            null
        }

    fun hasNotifPerm(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    fun remind(
        ctx: Context,
        taskId: Long,
        title: String,
        note: String,
        category: String,
        urgent: Boolean
    ) {
        try {
            if (!hasNotifPerm(ctx)) {
                Log.w(TAG, "通知权限未授予，跳过提醒")
                return
            }
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, taskId.toInt(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 通知栏动作：完成 / 10分钟后再提醒
            val donePi = actionPi(ctx, taskId, ReminderReceiver.ACTION_DONE, 1)
            val snoozePi = actionPi(ctx, taskId, ReminderReceiver.ACTION_SNOOZE, 2)

            val text = buildString {
                if (category.isNotEmpty()) append("[$category] ")
                append(if (note.isNotEmpty()) note else "点击查看详情")
            }
            val n = NotificationCompat.Builder(ctx, remindChannelId())
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle((if (urgent) "【紧急】" else "") + title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .addAction(0, "完成", donePi)
                .addAction(0, "10分钟后再提醒", snoozePi)
                .build()
            NotificationManagerCompat.from(ctx).notify(notifId(taskId), n)
        } catch (e: Exception) {
            Log.e(TAG, "remind failed taskId=$taskId", e)
        }
    }

    private fun actionPi(ctx: Context, taskId: Long, action: String, reqCode: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra("task_id", taskId)
        }
        return PendingIntent.getBroadcast(
            ctx, (taskId * 10 + reqCode).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelRemind(ctx: Context, taskId: Long) {
        try {
            NotificationManagerCompat.from(ctx).cancel(notifId(taskId))
        } catch (_: Exception) {
        }
    }

    fun notifId(taskId: Long): Int = (10000 + taskId % 100000).toInt()
}
