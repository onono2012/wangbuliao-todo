package com.wangbuliao.todo.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 应用内震动反馈（受 Prefs.vibrate 总开关控制）：
 * - 完成待办：轻快双击「哒-哒」
 * - 取消完成：单次短震
 */
object Haptics {
    private fun vibrator(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun vibrate(ctx: Context, pattern: LongArray) {
        if (!Prefs.vibrate.value) return
        try {
            val v = vibrator(ctx) ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }

    /** 完成待办（勾选）时的确认震动 */
    fun taskDone(ctx: Context) = vibrate(ctx, longArrayOf(0, 55, 70, 55))

    /** 取消完成时的短震 */
    fun taskUndone(ctx: Context) = vibrate(ctx, longArrayOf(0, 30))

    /** 悬浮窗快速保存成功的提示震动 */
    fun quickSaved(ctx: Context) = vibrate(ctx, longArrayOf(0, 40, 60, 40))
}
