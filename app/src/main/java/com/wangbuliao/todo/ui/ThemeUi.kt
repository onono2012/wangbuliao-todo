package com.wangbuliao.todo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 主题化 UI 辅助：让主题色贯穿全部界面（背景 wash / 顶栏 / 卡片透明度 / 分类图标）。
 * 照片主题（肖战）：全屏帅照背景 + 可读性遮罩。
 */

/** 当前是否为照片背景主题（内置照片或自定义照片文件） */
@Composable
fun wblHasPhotoBg(): Boolean {
    val spec = LocalWblTheme.current
    return spec.bgRes != null || spec.photoPath != null
}

/** 顶栏是否需要主题化渲染（照片遮罩或渐变 → 白色文字/图标） */
@Composable
fun wblTopBarThemed(): Boolean {
    val spec = LocalWblTheme.current
    return spec.bgRes != null || spec.photoPath != null || spec.gradient != null
}

/** 顶栏背景 Modifier：照片=深色渐变遮罩；渐变主题=主题渐变；其余=默认 */
@Composable
fun wblTopBarModifier(): Modifier {
    val spec = LocalWblTheme.current
    return when {
        spec.bgRes != null || spec.photoPath != null -> Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.50f),
                    Color.Black.copy(alpha = 0.12f),
                    Color.Transparent
                )
            )
        )
        spec.gradient != null -> Modifier.background(Brush.horizontalGradient(spec.gradient))
        else -> Modifier
    }
}

/**
 * 屏幕背景容器：
 * - 照片主题：全屏照片 + 上/下深色遮罩保证可读性
 * - 其他主题：主题色 wash 渐变（顶部主题色 → 底部背景色），全局生效不只顶栏
 */
@Composable
fun WblScreenBackground(content: @Composable BoxScope.() -> Unit) {
    val spec = LocalWblTheme.current
    val dark = wblIsDark()
    Box(Modifier.fillMaxSize()) {
        when {
            spec.photoPath != null -> {
                val bmp = remember(spec.photoPath) { decodePhotoBg(spec.photoPath) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
                PhotoScrim(dark)
            }
            spec.bgRes != null -> {
                Image(
                    painter = painterResource(spec.bgRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                PhotoScrim(dark)
            }
            else -> {
                // 全屏渐变 + 装饰光斑：主题色洗色更有氛围感
                val c1 = spec.gradient?.firstOrNull() ?: MaterialTheme.colorScheme.primary
                val c2 = spec.gradient?.getOrNull(1) ?: MaterialTheme.colorScheme.tertiary
                val c3 = spec.gradient?.lastOrNull() ?: c2
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to c1.copy(alpha = if (dark) 0.50f else 0.24f),
                            0.42f to c2.copy(alpha = if (dark) 0.20f else 0.09f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                // 右上/左下光斑
                Canvas(Modifier.fillMaxSize()) {
                    val r1 = size.minDimension * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                c3.copy(alpha = if (dark) 0.34f else 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.92f, size.height * 0.06f),
                            radius = r1
                        ),
                        radius = r1,
                        center = Offset(size.width * 0.92f, size.height * 0.06f)
                    )
                    val r2 = size.minDimension * 0.65f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                c1.copy(alpha = if (dark) 0.26f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.05f, size.height * 0.97f),
                            radius = r2
                        ),
                        radius = r2,
                        center = Offset(size.width * 0.05f, size.height * 0.97f)
                    )
                }
                // 底部沉色，保证列表末尾可读
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.85f else 0.65f)
                        )
                    )
                )
            }
        }
        content()
    }
}

/** 照片背景的可读性遮罩（上深下深中间浅） */
@Composable
private fun PhotoScrim(dark: Boolean) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = if (dark) 0.55f else 0.38f),
                    Color.Black.copy(alpha = if (dark) 0.32f else 0.10f),
                    Color.Black.copy(alpha = if (dark) 0.62f else 0.40f)
                )
            )
        )
    )
}

/** 解码自定义主题照片（降采样到 ~1600px 防 OOM） */
private fun decodePhotoBg(path: String): android.graphics.Bitmap? {
    return try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 || bounds.outHeight / (sample * 2) >= 3200) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeFile(path, opts)
    } catch (e: Exception) {
        null
    }
}

/** 卡片背景色：半透明 surface，透出主题 wash / 照片背景 */
@Composable
fun wblCardColor(): Color {
    val spec = LocalWblTheme.current
    val photo = spec.bgRes != null || spec.photoPath != null
    return MaterialTheme.colorScheme.surface.copy(alpha = if (photo) 0.88f else 0.82f)
}

/** 预设分类图标（自定义分类按默认标签图标） */
fun categoryIcon(name: String): ImageVector = when (name) {
    "工作" -> Icons.Outlined.Work
    "生活" -> Icons.Outlined.Cottage
    "学习" -> Icons.Outlined.School
    "随手记" -> Icons.Outlined.EditNote
    "其他" -> Icons.Outlined.MoreHoriz
    else -> Icons.Outlined.Label
}

/** 状态筛选项图标 */
fun statusIcon(f: StatusFilter): ImageVector = when (f) {
    StatusFilter.TODO -> Icons.Outlined.Pending
    StatusFilter.DONE -> Icons.Outlined.TaskAlt
    StatusFilter.ALL -> Icons.Outlined.Layers
}

/** 带主题色图标的分区标题（设置页/编辑页共用） */
@Composable
fun WblSectionHead(
    text: String,
    icon: ImageVector,
    style: TextStyle = MaterialTheme.typography.titleMedium
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, fontWeight = FontWeight.Bold)
    }
}
