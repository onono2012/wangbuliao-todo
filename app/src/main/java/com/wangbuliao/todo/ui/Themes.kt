package com.wangbuliao.todo.ui

import androidx.compose.material3.darkColorScheme
import com.wangbuliao.todo.R
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.wangbuliao.todo.util.Prefs
import java.io.File

/**
 * 主题定义：每套主题含浅色/深色调色板 + 可选渐变（顶栏/悬浮球用）。
 * id 持久化于 Prefs；默认「炫彩」。
 * 自定义照片主题（id="custom"）：用户自选照片动态构建，主色从照片提取。
 */
data class WblThemeSpec(
    val id: String,
    val name: String,
    val desc: String,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
    /** 主题渐变（null = 纯色顶栏，用 primary） */
    val gradient: List<Color>? = null,
    /** 强制深色（极夜黑/鎏金黑主题） */
    val forceDark: Boolean = false,
    /** 设置页预览色 */
    val preview: List<Color>,
    /** 全屏照片背景（drawable 资源；null = 主题色 wash 渐变背景） */
    val bgRes: Int? = null,
    /** 全屏照片背景（文件路径，自定义照片主题用；优先级高于 bgRes） */
    val photoPath: String? = null,
    /** 主题预览图（设置页圆形预览；null = 用 preview 渐变色点 / photoPath） */
    val previewRes: Int? = null,
    /** 悬浮球圆形贴图（drawable；null = 渐变球 / photoPath 圆图） */
    val bubbleRes: Int? = null
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
    // ② 肖战：红海应援色 + 高清帅照全屏背景
    WblThemeSpec(
        id = "xiaozhan", name = "肖战", desc = "红海 · 高清帅照全屏背景",
        light = light(0xFFE60027, 0xFFFFDAD9, 0xFFC2185B, 0xFFFF6D00),
        dark = dark(0xFFFF8A80, 0xFF7F0013, 0xFFFF80AB, 0xFFFFAB91),
        gradient = listOf(Color(0xFFE60027), Color(0xFFFF5252), Color(0xFFFF8A80)),
        preview = listOf(Color(0xFFE60027), Color(0xFFFF5252)),
        bgRes = R.drawable.xz_bg,
        previewRes = R.drawable.xz_prev,
        bubbleRes = R.drawable.xz_bubble
    ),
    // ③ 极光：冰蓝→紫罗兰→品红 极光渐变
    WblThemeSpec(
        id = "aurora", name = "极光", desc = "冰蓝紫罗兰 · 极光渐变",
        light = light(0xFF304FFE, 0xFFDEE3FF, 0xFF00ACC1, 0xFFAA00FF, bg = 0xFFFBFCFF),
        dark = dark(0xFF8C9EFF, 0xFF1A237E, 0xFF80DEEA, 0xFFEA80FC),
        gradient = listOf(Color(0xFF00E5FF), Color(0xFF536DFE), Color(0xFFD500F9)),
        preview = listOf(Color(0xFF00E5FF), Color(0xFF536DFE), Color(0xFFD500F9))
    ),
    // ④ 暮山紫：粉紫暮色
    WblThemeSpec(
        id = "twilight", name = "暮山紫", desc = "粉紫暮色 · 温柔梦幻",
        light = light(0xFF7E57C2, 0xFFEDE3FF, 0xFFEC407A, 0xFF5C6BC0, bg = 0xFFFDFAFF),
        dark = dark(0xFFB39DDB, 0xFF4527A0, 0xFFF48FB1, 0xFF9FA8DA),
        gradient = listOf(Color(0xFFFF6EC4), Color(0xFF7873F5)),
        preview = listOf(Color(0xFFFF6EC4), Color(0xFF7873F5))
    ),
    // ⑤ 鎏金：黑金奢华（强制深色）
    WblThemeSpec(
        id = "gilded", name = "鎏金", desc = "黑金奢华 · 质感之夜",
        light = dark(0xFFD4AF37, 0xFF3A2F0B, 0xFFE6C96A, 0xFFBFA15A, bg = 0xFF120E06),
        dark = dark(0xFFD4AF37, 0xFF3A2F0B, 0xFFE6C96A, 0xFFBFA15A, bg = 0xFF120E06),
        forceDark = true,
        gradient = listOf(Color(0xFFBF953F), Color(0xFFFCF6BA), Color(0xFFB38728)),
        preview = listOf(Color(0xFFBF953F), Color(0xFFFCF6BA), Color(0xFFB38728))
    ),
    // ⑥ 深海蓝：沉稳经典
    WblThemeSpec(
        id = "ocean", name = "深海蓝", desc = "沉稳专注",
        light = light(0xFF2F5FA8, 0xFFD8E3FF, 0xFF555F71, 0xFF6E5676),
        dark = dark(0xFFAEC6FF, 0xFF13458F, 0xFFBDC7DC, 0xFFDBBDE3),
        gradient = listOf(Color(0xFF2F5FA8), Color(0xFF5C8AE6)),
        preview = listOf(Color(0xFF2F5FA8), Color(0xFFAEC6FF))
    ),
    // ⑦ 清新绿：自然护眼
    WblThemeSpec(
        id = "forest", name = "清新绿", desc = "自然护眼",
        light = light(0xFF2E7D32, 0xFFCDEBD0, 0xFF558B2F, 0xFF00796B, bg = 0xFFFBFDF9),
        dark = dark(0xFF81C784, 0xFF1B5E20, 0xFFAED581, 0xFF80CBC4),
        gradient = listOf(Color(0xFF43A047), Color(0xFF00897B)),
        preview = listOf(Color(0xFF2E7D32), Color(0xFF81C784))
    ),
    // ⑧ 暖阳橙：活力明快
    WblThemeSpec(
        id = "sunset", name = "暖阳橙", desc = "活力明快",
        light = light(0xFFEF6C00, 0xFFFFE0B2, 0xFFF4511E, 0xFFC2185B, bg = 0xFFFFFCF8),
        dark = dark(0xFFFFB74D, 0xFFE65100, 0xFFFF8A65, 0xFFF48FB1),
        gradient = listOf(Color(0xFFFF9800), Color(0xFFFF5722)),
        preview = listOf(Color(0xFFEF6C00), Color(0xFFFF9800))
    ),
    // ⑨ 樱花粉：温柔治愈
    WblThemeSpec(
        id = "sakura", name = "樱花粉", desc = "温柔治愈",
        light = light(0xFFD81B60, 0xFFFFD9E4, 0xFFAD1457, 0xFF7B1FA2, bg = 0xFFFFFBFC),
        dark = dark(0xFFFF80AB, 0xFF880E4F, 0xFFFF80AB, 0xFFEA80FC),
        gradient = listOf(Color(0xFFF48FB1), Color(0xFFD81B60)),
        preview = listOf(Color(0xFFD81B60), Color(0xFFF48FB1))
    ),
    // ⑩ 极夜黑：纯黑省电（强制深色）
    WblThemeSpec(
        id = "midnight", name = "极夜黑", desc = "纯黑省电",
        light = dark(0xFFBB86FC, 0xFF33294D, 0xFF9FA8DA, 0xFF80DEEA, bg = 0xFF0A0A0C),
        dark = dark(0xFFBB86FC, 0xFF33294D, 0xFF9FA8DA, 0xFF80DEEA, bg = 0xFF0A0A0C),
        forceDark = true,
        preview = listOf(Color(0xFF0A0A0C), Color(0xFFBB86FC))
    )
)

/** 自定义照片主题 id */
const val CUSTOM_THEME_ID = "custom"

/** 依据 Prefs 中的自定义照片动态构建 spec；无照片/文件丢失返回 null */
fun customThemeSpec(): WblThemeSpec? {
    val path = Prefs.customPhoto.value
    if (path.isNullOrEmpty()) return null
    if (!File(path).exists()) return null
    val primary = Prefs.customPrimary.value
    val p = Color(primary)
    val scheme = darkColorScheme(
        primary = p,
        onPrimary = Color(0xFF101010),
        primaryContainer = p.copy(alpha = 0.35f),
        onPrimaryContainer = Color(0xFFEDEDED),
        secondary = p,
        secondaryContainer = p.copy(alpha = 0.35f),
        onSecondaryContainer = Color(0xFFEDEDED),
        tertiary = Color(0xFFFFFFFF),
        background = Color(0xFF101216),
        surface = Color(0xFF101216)
    )
    return WblThemeSpec(
        id = CUSTOM_THEME_ID, name = "我的照片", desc = "自定义 · 你选的照片全屏背景",
        light = scheme, dark = scheme,
        forceDark = true,
        gradient = listOf(p, Color(0xFF101216)),
        preview = listOf(p, Color(0xFF101216)),
        photoPath = path
    )
}

fun themeById(id: String): WblThemeSpec {
    if (id == CUSTOM_THEME_ID) {
        customThemeSpec()?.let { return it }
        return WBL_THEMES[0]
    }
    return WBL_THEMES.firstOrNull { it.id == id } ?: WBL_THEMES[0]
}

/** 全部可选主题（含已启用的自定义照片主题） */
fun allThemes(): List<WblThemeSpec> =
    customThemeSpec()?.let { WBL_THEMES + it } ?: WBL_THEMES
