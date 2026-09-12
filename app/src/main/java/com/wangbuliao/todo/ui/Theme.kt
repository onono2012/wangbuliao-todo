package com.wangbuliao.todo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wangbuliao.todo.util.Prefs

/** 当前主题规格（渐变/预览色等非 Material 属性从这里取） */
val LocalWblTheme = staticCompositionLocalOf { themeById("rainbow") }

@Composable
fun WblTheme(content: @Composable () -> Unit) {
    val themeId by Prefs.theme.collectAsState()
    val spec = themeById(themeId)
    val dark = spec.forceDark || isSystemInDarkTheme()
    CompositionLocalProvider(LocalWblTheme provides spec) {
        MaterialTheme(
            colorScheme = if (dark) spec.dark else spec.light,
            typography = Typography(),
            content = content
        )
    }
}

/** 当前是否为深色渲染（含强制深色主题） */
@Composable
fun wblIsDark(): Boolean {
    val spec = LocalWblTheme.current
    return spec.forceDark || isSystemInDarkTheme()
}

/** 分类标签颜色：预设分类固定色，自定义分类按名称哈希取色相 */
fun categoryColor(name: String): Color = when (name) {
    "工作" -> Color(0xFF1565C0)
    "生活" -> Color(0xFF2E7D32)
    "学习" -> Color(0xFF6A1B9A)
    "随手记" -> Color(0xFF00838F)
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
