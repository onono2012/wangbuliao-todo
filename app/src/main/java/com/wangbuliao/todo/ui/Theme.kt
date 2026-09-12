package com.wangbuliao.todo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF555F71),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF002E65),
    primaryContainer = Color(0xFF13458F),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFBDC7DC),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDBBDE3)
)

@Composable
fun WblTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

/** 分类标签颜色：预设分类固定色，自定义分类按名称哈希取色相 */
fun categoryColor(name: String): Color = when (name) {
    "工作" -> Color(0xFF1565C0)
    "生活" -> Color(0xFF2E7D32)
    "学习" -> Color(0xFF6A1B9A)
    "其他" -> Color(0xFFEF6C00)
    else -> {
        val hue = (kotlin.math.abs(name.hashCode()) % 360).toFloat()
        Color.hsl(hue, 0.55f, 0.42f)
    }
}

/** 分类文字颜色：深色主题下提亮保证对比度 */
fun categoryTextColor(name: String, dark: Boolean): Color {
    val base = categoryColor(name)
    return if (dark) lerp(base, Color.White, 0.45f) else base
}
