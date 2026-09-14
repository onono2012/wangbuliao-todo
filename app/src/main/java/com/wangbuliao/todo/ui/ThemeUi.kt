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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangbuliao.todo.util.ColorContrast

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

/** 颜色相对亮度（WCAG 0..1）——委托 [ColorContrast] 纯函数（可单测） */
private fun Color.relLum(): Float = ColorContrast.relLuminance(red, green, blue)

private fun contrastRatio(l1: Float, l2: Float): Float = ColorContrast.contrastRatio(l1, l2)

/**
 * 顶栏内容色（标题/副标题/图标）：
 * - 照片主题 → 白色（深色遮罩保证可读）
 * - 渐变主题 → 对整条渐变计算「最差对比度」，白字/近黑字取更可读者
 *   （修复：极光青/樱花粉/暖阳橙/暮山紫/鎏金等亮渐变上白字看不清）
 * - 其余 → onSurface
 */
@Composable
fun wblTopBarContentColor(): Color {
    val spec = LocalWblTheme.current
    if (spec.bgRes != null || spec.photoPath != null) return Color.White
    val g = spec.gradient ?: return MaterialTheme.colorScheme.onSurface
    val darkText = ColorContrast.preferDarkText(g.map { it.relLum() })
    return if (darkText) Color(ColorContrast.NEAR_BLACK_ARGB) else Color.White
}

/**
 * 渐变顶栏对比度补偿 veil：中亮度渐变（黑/白字都到不了 WCAG 4.5）时，
 * 叠一层极淡白纱（黑字主题）/黑纱（白字主题），把最差对比度顶到 4.5，上限 35%。
 */
@Composable
fun wblTopBarVeil(): Pair<Color, Float> {
    val spec = LocalWblTheme.current
    if (spec.bgRes != null || spec.photoPath != null) return Color.Black to 0f
    val g = spec.gradient ?: return Color.Black to 0f
    val lums = g.map { it.relLum() }
    val darkText = ColorContrast.preferDarkText(lums)
    return if (darkText) Color.White to ColorContrast.veilAlpha(lums, true)
    else Color.Black to ColorContrast.veilAlpha(lums, false)
}

/** 顶栏背景 Modifier：照片=深色遮罩；渐变=主题渐变+对比度补偿 veil；其余=默认 */
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
        spec.gradient != null -> run {
            val veil = wblTopBarVeil()
            Modifier.background(Brush.horizontalGradient(spec.gradient))
                .then(
                    if (veil.second > 0.001f) {
                        Modifier.background(veil.first.copy(alpha = veil.second))
                    } else Modifier
                )
        }
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
    // 动态主题 WebView 层：内置玻璃雨珠走 assets；在线下载的动态主题走 filesDir 本地目录
    val dynUrl = when {
        spec.id == "glass-rain" -> "file:///android_asset/glass-rain/index.html"
        spec.dynamicDir != null -> "file://" + spec.dynamicDir + "/index.html"
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        // 动态主题：最底层放置 WebView 动画层
        if (dynUrl != null) {
            GlassRainBackground(dynUrl, Modifier.fillMaxSize())
        }
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
    return MaterialTheme.colorScheme.surface.copy(alpha = if (photo) 0.92f else 0.86f)
}

/** 有效卡片背景 ARGB（surface@86% 叠 background 的近似，供对比度计算） */
@Composable
fun wblEffectiveCardArgb(): Int {
    val s = MaterialTheme.colorScheme
    val spec = LocalWblTheme.current
    val photo = spec.bgRes != null || spec.photoPath != null
    val alpha = if (photo) 0.92f else 0.86f
    // 照片主题背景≈深色遮罩后的照片，按 scheme.background 近似即可
    return ColorContrast.blendArgb(
        s.surface.toArgb(), s.background.toArgb(), alpha
    )
}

/**
 * 保证可读的主题强调色（对比度根治⑨）：
 * primary 在当前卡片背景上对比度不足 WCAG AA 时自动压暗/提亮，
 * 用于所有「以主题色显示的小字/选中态图标」，替代裸 colorScheme.primary。
 */
@Composable
fun wblAccentColor(): Color {
    val p = MaterialTheme.colorScheme.primary.toArgb()
    return Color(ColorContrast.ensureReadableArgb(p, wblEffectiveCardArgb()))
}

/**
 * ⑨ 一次性根治：全站 FilterChip（状态筛选行/分类行/重复规则等）统一可读配色。
 * - 未选中：实色 chip 背景（surface 叠页面背景，不透底）+ 保证 WCAG AA 的文字/图标色 + 清晰描边
 * - 选中：主题强调色实底 + 自动选择黑/白文字（按对比度取优）
 * 所有 FilterChip 必须传 colors = wblChipColors()，禁止再用默认配色。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun wblChipColors(): SelectableChipColors {
    val s = MaterialTheme.colorScheme
    val spec = LocalWblTheme.current
    val photo = spec.bgRes != null || spec.photoPath != null
    // chip 实际落在页面背景上：surface 以 86%/92% 叠加 background 后的实色
    val chipBgArgb = ColorContrast.blendArgb(
        s.surface.toArgb(), s.background.toArgb(), if (photo) 0.92f else 0.86f
    )
    val chipBg = Color(chipBgArgb)
    // 未选中文字：onSurfaceVariant 不可读时自动压暗/提亮到 AA
    val labelArgb = ColorContrast.ensureReadableArgb(s.onSurfaceVariant.toArgb(), chipBgArgb)
    val label = Color(labelArgb)
    // 选中态：强调色实底 + 黑/白文字取对比度更高者
    val accent = wblAccentColor()
    val accentLum = accent.luminance()
    val selText = if (ColorContrast.preferDarkText(listOf(accentLum)))
        Color(ColorContrast.NEAR_BLACK_ARGB) else Color.White
    return FilterChipDefaults.filterChipColors(
        containerColor = chipBg,
        labelColor = label,
        iconColor = label,
        selectedContainerColor = accent,
        selectedLabelColor = selText,
        selectedLeadingIconColor = selText
    )
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
