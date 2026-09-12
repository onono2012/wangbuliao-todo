package com.wangbuliao.todo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/**
 * 图片附件管理：拍照文件、相册导入（复制进应用私有目录保证长期可读）、
 * 缩略图解码（inSampleSize 压缩 + LruCache 内存缓存）。
 */
object ImageStore {
    private const val MAX_IMAGES = 9

    fun dir(ctx: Context): File = File(ctx.filesDir, "images").apply { mkdirs() }

    /** 拍照目标文件（经 FileProvider 提供给相机） */
    fun newCameraFile(ctx: Context): File = File(dir(ctx), "cam_${System.currentTimeMillis()}.jpg")

    fun maxImages() = MAX_IMAGES

    /** 相册 uri → 复制到私有目录；失败返回 null */
    fun importUri(ctx: Context, uri: Uri): String? {
        return try {
            val out = File(dir(ctx), "imp_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            } ?: return null
            if (out.exists() && out.length() > 0) out.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /** FileProvider uri → 原始文件路径（不校验存在/大小；相机取消时清残留用） */
    fun rawPathFromUri(ctx: Context, uri: Uri): String? {
        return try {
            val name = uri.pathSegments.lastOrNull() ?: return null
            File(dir(ctx), name).absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** FileProvider uri → 私有目录文件路径（拍照回传用） */
    fun pathFromUri(ctx: Context, uri: Uri): String? {
        return try {
            val segs = uri.pathSegments
            // content://<pkg>.fileprovider/images/cam_xxx.jpg
            val name = segs.lastOrNull() ?: return null
            val f = File(dir(ctx), name)
            if (f.exists() && f.length() > 0) f.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    fun delete(path: String) {
        try {
            File(path).delete()
            thumbCache.remove(path)
        } catch (_: Exception) {
        }
    }

    // ── 缩略图 ──
    private val thumbCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun thumb(path: String, reqSize: Int): Bitmap? {
        thumbCache.get(path)?.let { return it }
        val bmp = decodeSampled(path, reqSize) ?: return null
        thumbCache.put(path, bmp)
        return bmp
    }

    private fun decodeSampled(path: String, reqSize: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqSize && bounds.outHeight / (sample * 2) >= reqSize) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null
        }
    }
}

/** 缩略图组件：异步解码，加载中显示占位底色 */
@Composable
fun Thumb(path: String, size: Dp, onClick: (() -> Unit)? = null) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ImageStore.thumb(path, sizePx(size))
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

private fun sizePx(size: Dp): Int = (size.value * 3).toInt().coerceAtLeast(200)
