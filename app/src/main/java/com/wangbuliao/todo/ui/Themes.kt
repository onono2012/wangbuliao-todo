package com.wangbuliao.todo.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 主题定义：每套主题含浅色/深色调色板 + 可选渐变（顶栏/悬浮球用）。
 * id 持久化于 Prefs；默认「炫彩」。
 */
data class WblThemeSpec(
    val id: String,
    val name: String,
    val desc: String,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
    /** 主题渐变（null = 纯色顶栏，用 primary） */
    val gradient: List<Color>? = null,
    /** 强制深色（极夜黑主题） */
    val forceDark: Boolean = false,
    /** 设置页预览色 */
    val preview: List<Color>
)

private fun light(
    primary: Long, container: Long, secondary: Long, tertiary: Long, bg: Long = 0xFFFDFBFF
) = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(container),
    onPrimaryContainer = Color(0xFF101010),
    secondary = Color(secondary),
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(0xFF101010),
    tertiary = Color(tertiary),
    background = Color(bg),
    surface = Color(bg)
)

private fun dark(
    primary: Long, container: Long, secondary: Long, tertiary: Long, bg: Long = 0xFF121316
) = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFF101010),
    primaryContainer = Color(container),
    onPrimaryContainer = Color(0xFFEDEDED),
    secondary = Color(secondary),
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(tertiary),
    background = Color(bg),
    surface = Color(bg)
)

val WBL_THEMES: List<WblThemeSpec> = listOf(
    // ① 炫彩（默认）：霓虹渐变
    WblThemeSpec(
        id = "rainbow", name = "炫彩", desc = "霓虹渐变 · 默认",
        light = light(0xFF7C4DFF, 0xFFE8DEFF, 0xFF00B0FF, 0xFFE91E63),
        dark = dark(0xFFB388FF, 0xFF311B92, 0xFF80D8FF, 0xFFFF80AB),
        gradient = listOf(Color(0xFF7C4DFF), Color(0xFFE040FB), Color(0xFF00BCD4)),
        preview = listOf(Color(0xFF7C4DFF), Color(0xFFE040FB), Color(0xFF00BCD4))
    ),
    // ② 肖战：红海应援色
    WblThemeSpec(
        id = "xiaozhan", name = "肖战", desc = "红海 · 热爱可抵岁月漫长",
        light = light(0xFFE60027, 0xFFFFDAD9, 0xFFC2185B, 0xFFFF6D00),
        dark = dark(0xFFFF8A80, 0xFF7F0013, 0xFFFF80AB, 0xFFFFAB91),
        gradient = listOf(Color(0xFFE60027), Color(0xFFFF5252), Color(0xFFFF8A80)),
        preview = listOf(Color(0xFFE60027), Color(0xFFFF5252))
    ),
    // ③ 深海蓝：沉稳经典
    WblThemeSpec(
        id = "ocean", name = "深海蓝", desc = "沉稳专注",
        light = light(0xFF2F5FA8, 0xFFD8E3FF, 0xFF555F71, 0xFF6E5676),
        dark = dark(0xFFAEC6FF, 0xFF13458F, 0xFFBDC7DC, 0xFFDBBDE3)
        , preview = listOf(Color(0xFF2F5FA8), Color(0xFFAEC6FF))
    ),
    // ④ 清新绿：自然护眼
    WblThemeSpec(
        id = "forest", name = "清新绿", desc = "自然护眼",
        light = light(0xFF2E7D32, 0xFFCDEBD0, 0xFF558B2F, 0xFF00796B, bg = 0xFFFBFDF9),
        dark = dark(0xFF81C784, 0xFF1B5E20, 0xFFAED581, 0xFF80CBC4),
        preview = listOf(Color(0xFF2E7D32), Color(0xFF81C784))
    ),
    // ⑤ 暖阳橙：活力明快
    WblThemeSpec(
        id = "sunset", name = "暖阳橙", desc = "活力明快",
        light = light(0xFFEF6C00, 0xFFFFE0B2, 0xFFF4511E, 0xFFC2185B, bg = 0xFFFFFCF8),
        dark = dark(0xFFFFB74D, 0xFFE65100, 0xFFFF8A65, 0xFFF48FB1),
        gradient = listOf(Color(0xFFFF9800), Color(0xFFFF5722)),
        preview = listOf(Color(0xFFEF6C00), Color(0xFFFF9800))
    ),
    // ⑥ 樱花粉：温柔治愈
    WblThemeSpec(
        id = "sakura", name = "樱花粉", desc = "温柔治愈",
        light = light(0xFFD81B60, 0xFFFFD9E4, 0xFFAD1457, 0xFF7B1FA2, bg = 0xFFFFFBFC),
        dark = dark(0xFFFF80AB, 0xFF880E4F, 0xFFFF80AB, 0xFFEA80FC),
        gradient = listOf(Color(0xFFF48FB1), Color(0xFFD81B60)),
        preview = listOf(Color(0xFFD81B60), Color(0xFFF48FB1))
    ),
    // ⑦ 极夜黑：纯黑省电（强制深色）
    WblThemeSpec(
        id = "midnight", name = "极夜黑", desc = "纯黑省电",
        light = dark(0xFFBB86FC, 0xFF33294D, 0xFF9FA8DA, 0xFF80DEEA, bg = 0xFF0A0A0C),
        dark = dark(0xFFBB86FC, 0xFF33294D, 0xFF9FA8DA, 0xFF80DEEA, bg = 0xFF0A0A0C),
        forceDark = true,
        preview = listOf(Color(0xFF0A0A0C), Color(0xFFBB86FC))
    )
)

fun themeById(id: String): WblThemeSpec =
    WBL_THEMES.firstOrNull { it.id == id } ?: WBL_THEMES[0]
