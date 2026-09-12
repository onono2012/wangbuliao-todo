package com.wangbuliao.todo.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.R

object Notif {
    private const val TAG = "WblNotif"
    private const val CH_REMIND = "wbl_reminder"

    fun ensureChannels(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val ch = NotificationChannel(
                CH_REMIND,
                "待办提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = "任务到期提醒通知"
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        } catch (e: Exception) {
            Log.e(TAG, "ensureChannels failed", e)
        }
    }

    fun remind(
        ctx: Context,
        taskId: Long,
        title: String,
        note: String,
        category: String,
        urgent: Boolean
    ) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
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
            val text = buildString {
                if (category.isNotEmpty()) append("[$category] ")
                append(if (note.isNotEmpty()) note else "点击查看详情")
            }
            val n = NotificationCompat.Builder(ctx, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle((if (urgent) "【紧急】" else "") + title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            NotificationManagerCompat.from(ctx).notify(notifId(taskId), n)
        } catch (e: Exception) {
            Log.e(TAG, "remind failed taskId=$taskId", e)
        }
    }

    private fun notifId(taskId: Long): Int = (10000 + taskId % 100000).toInt()
}
