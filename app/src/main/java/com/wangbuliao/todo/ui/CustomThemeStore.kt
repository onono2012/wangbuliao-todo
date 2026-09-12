package com.wangbuliao.todo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 自定义照片主题存储：导入用户照片 → 居中裁剪为屏幕比例 → 存 filesDir/themes/。
 * 同时从照片提取鲜艳主色（饱和度加权平均色相）作为主题 primary。
 */
object CustomThemeStore {

    fun dir(ctx: Context): File = File(ctx.filesDir, "themes").apply { mkdirs() }

    fun targetFile(ctx: Context): File = File(dir(ctx), "custom_bg.jpg")

    /**
     * 导入照片：返回 (保存路径, 主色 ARGB)；失败返回 null。
     * 裁剪策略：按屏幕宽高比居中裁剪（cover），最长边限制 1600px。
     */
    fun import(ctx: Context, uri: Uri): Pair<String, Int>? {
        return try {
            // 1) 读边界
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2) 目标尺寸：屏幕比例居中裁剪，长边 ≤1600
            val dm = ctx.resources.displayMetrics
            val screenRatio = dm.widthPixels.toFloat() / dm.heightPixels.toFloat()
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            val srcRatio = srcW.toFloat() / srcH
            // 裁剪框（源图坐标系）
            val cropW: Int
            val cropH: Int
            if (srcRatio > screenRatio) {
                cropH = srcH
                cropW = (srcH * screenRatio).roundToInt()
            } else {
                cropW = srcW
                cropH = (srcW / screenRatio).roundToInt()
            }
            // 输出尺寸
            var outW = cropW
            var outH = cropH
            val longEdge = max(outW, outH)
            if (longEdge > 1600) {
                val scale = 1600f / longEdge
                outW = (outW * scale).roundToInt()
                outH = (outH * scale).roundToInt()
            }
            // 解码采样率（按裁剪框）
            var sample = 1
            while (max(cropW, cropH) / (sample * 2) >= max(outW, outH)) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // 3) 居中裁剪 + 缩放到输出尺寸
            val cx = (decoded.width - min(decoded.width, (decoded.height * screenRatio).roundToInt())) / 2
            val cy = (decoded.height - min(decoded.height, (decoded.width / screenRatio).roundToInt())) / 2
            val cw = min(decoded.width, (decoded.height * screenRatio).roundToInt())
            val ch = min(decoded.height, (decoded.width / screenRatio).roundToInt())
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(
                decoded,
                Rect(cx, cy, cx + cw, cy + ch),
                Rect(0, 0, outW, outH),
                null
            )
            if (decoded !== out) decoded.recycle()

            // 4) 提取主色（在缩小副本上）
            val primary = extractPrimary(out)

            // 5) 保存
            val f = targetFile(ctx)
            FileOutputStream(f).use { fos ->
                out.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            out.recycle()
            Pair(f.absolutePath, primary)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 主色提取：缩到 24x24，跳过过亮/过暗/低饱和像素，
     * 以饱和度为权重对色相做矢量平均，输出 S=0.62 V=0.88 的鲜艳色。
     */
    fun extractPrimary(src: Bitmap): Int {
        val n = 24
        val small = Bitmap.createScaledBitmap(src, n, n, true)
        val hsv = FloatArray(3)
        var sumX = 0.0
        var sumY = 0.0
        var sumS = 0.0
        var sumV = 0.0
        var wSum = 0.0
        for (y in 0 until n) {
            for (x in 0 until n) {
                val c = small.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                val s = hsv[1]
                val v = hsv[2]
                if (s < 0.12f || v < 0.15f || v > 0.96f) continue
                val w = s.toDouble() * v.toDouble()
                val rad = Math.toRadians(hsv[0].toDouble())
                sumX += kotlin.math.cos(rad) * w
                sumY += kotlin.math.sin(rad) * w
                sumS += s * w
                sumV += v * w
                wSum += w
            }
        }
        if (small !== src) small.recycle()
        if (wSum < 0.5) {
            // 照片太素（黑白灰）：返回优雅蓝紫
            return 0xFF7C8CFF.toInt()
        }
        var h = Math.toDegrees(kotlin.math.atan2(sumY / wSum, sumX / wSum)).toFloat()
        if (h < 0) h += 360f
        val sAvg = (sumS / wSum).toFloat().coerceIn(0.35f, 0.78f)
        val vAvg = (sumV / wSum).toFloat().coerceIn(0.55f, 0.92f)
        return Color.HSVToColor(floatArrayOf(h, sAvg, vAvg))
    }

    /** 圆形头像版（悬浮球/预览用）：中心方形裁剪 */
    fun squareThumb(path: String, sizePx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = BitmapFactory.decodeFile(path, opts) ?: return null
            val side = min(src.width, src.height)
            val sq = Bitmap.createBitmap(
                src, (src.width - side) / 2, (src.height - side) / 2, side, side
            )
            val out = Bitmap.createScaledBitmap(sq, sizePx, sizePx, true)
            if (sq !== src) src.recycle()
            if (out !== sq) sq.recycle()
            out
        } catch (e: Exception) {
            null
        }
    }
}
