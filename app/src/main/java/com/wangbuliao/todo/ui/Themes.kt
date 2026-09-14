package com.wangbuliao.todo.ui

import com.wangbuliao.todo.R
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalInspectionMode
import com.wangbuliao.todo.util.Prefs
import java.io.File

/** Light / Dark theme color specs */
data class ThemeColors(
    val light: ColorScheme,
    val dark: ColorScheme
)

/** One spec for a whole theme (light + dark) */
data class WblThemeSpec(
    val id: String,
    val name: String,
    val desc: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    /** Optional pre-computed gradient stops for the full-screen wash */
    val gradient: List<Color>? = null,
    /** If true, force dark mode regardless of system setting */
    val forceDark: Boolean = false,
    /** Drawable resource for preview (photo-based themes) */
    val previewRes: Int? = null,
    /** Full-screen photo background resource */
    val bgRes: Int? = null,
    /** Bubble tint for FAB / floating note */
    val bubbleRes: Int? = null,
    /** Photo path for custom photo theme */
    val photoPath: String? = null,
    /** 在线动态主题：本地资源目录（含 index.html），由 GlassRainBackground 加载 */
    val dynamicDir: String? = null
) {
    val preview: List<Color> get() = gradient ?: listOf(Color.Unspecified)
}

/** Helper to build a light ColorScheme from hex strings */
private fun light(
    primary: Long,
    surface: Long,
    onPrimaryContainer: Long,
    onSecondaryContainer: Long,
    tertiary: Long = 0xFF616161,
    bg: Long = 0xFFFFFFFF
): ColorScheme = lightColorScheme(
    primary = Color(primary),
    secondary = Color(surface),
    tertiary = Color(tertiary),
    background = Color(bg),
    surface = Color(bg),
    primaryContainer = Color(onPrimaryContainer).copy(alpha = 0.24f),
    secondaryContainer = Color(onSecondaryContainer).copy(alpha = 0.24f),
    errorContainer = Color(0xFFD32F2F).copy(alpha = 0.15f),
    surfaceVariant = Color(bg).copy(alpha = 0.96f)
)

/** Helper to build a dark ColorScheme from hex strings */
private fun dark(
    primary: Long,
    container: Long,
    tertiary: Long,
    onTertiary: Long,
    bg: Long = 0xFF101216
): ColorScheme = darkColorScheme(
    primary = Color(primary),
    secondary = Color(container),
    tertiary = Color(tertiary),
    background = Color(bg),
    surface = Color(bg),
    primaryContainer = Color(primary).copy(alpha = 0.28f),
    secondaryContainer = Color(container).copy(alpha = 0.22f),
    tertiaryContainer = Color(tertiary).copy(alpha = 0.24f),
    onPrimaryContainer = Color(onTertiary).copy(alpha = 0.12f)
)

val WBL_THEMES: List<WblThemeSpec> = listOf(
    // ① 炫彩（默认）：霓虹渐变
    WblThemeSpec(
        id = "rainbow", name = "炫彩", desc = "霓虹渐变 · 默认",
        light = light(0xFF7C4DFF, 0xFFE8DEFF, 0xFF00B0FF, 0xFFE91E63),
        dark = dark(0xFFB388FF, 0xFF311B92, 0xFF80D8FF, 0xFFFF80AB),
        gradient = listOf(Color(0xFF7C4DFF), Color(0xFFE040FB), Color(0xFF00BCD4)),
    ),
    // ② 肖战：红海应援色 + 高清帅照全屏背景
    WblThemeSpec(
        id = "xiaozhan", name = "肖战", desc = "红海 · 高清帅照全屏背景",
        light = light(0xFFE60027, 0xFFFFDAD9, 0xFFC2185B, 0xFFFF6D00),
        dark = dark(0xFFFF8A80, 0xFF7F0013, 0xFFFF80AB, 0xFFFFAB91),
        gradient = listOf(Color(0xFFE60027), Color(0xFFFF5252), Color(0xFFFF8A80)),
        bgRes = R.drawable.xz_bg,
        previewRes = R.drawable.xz_prev,
        bubbleRes = R.drawable.xz_bubble
    ),
    // ③ 玻璃雨珠：透明背景 Canvas粒子动画 · 默认新皮肤
    WblThemeSpec(
        id = "glass-rain", name = "玻璃雨珠", desc = "深蓝黑色底 · Canvas粒子动画",
        light = dark(0xFF64B5F6, 0xFF05070D, 0xFF90CAF4, 0xFFBBDEFB, bg = 0xFF05070D),
        dark = dark(0xFF64B5F6, 0xFF05070D, 0xFF90CAF4, 0xFFBBDEFB, bg = 0xFF05070D),
        forceDark = true,
        gradient = listOf(Color(0xFF1976D2), Color(0xFF0D47A1)),
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
        photoPath = path
    )
}

/** 在线主题 id 前缀：`online:<远端主题 id>`，与内置主题区分 */
const val ONLINE_THEME_PREFIX = "online:"

/** 在线主题安装根目录：filesDir/themes/ */
fun onlineThemesRoot(): File =
    File(com.wangbuliao.todo.AppCtx.app.filesDir, "themes")

/** 单个在线主题目录 */
fun onlineThemeDir(rawId: String): File = File(onlineThemesRoot(), rawId)

/**
 * 由本地 manifest.json 构建在线主题 spec（ThemeDownloader 下载安装后写入）。
 * 文件缺失 / 解析失败返回 null。
 */
fun onlineThemeSpec(rawId: String): WblThemeSpec? {
    if (rawId.isEmpty()) return null
    val dir = onlineThemeDir(rawId)
    val mf = File(dir, "manifest.json")
    if (!mf.exists()) return null
    return try {
        val o = org.json.JSONObject(mf.readText())
        val name = o.optString("name", rawId)
        val desc = o.optString("desc", "在线主题")
        val type = o.optString("type", "static")
        val accent = try {
            android.graphics.Color.parseColor(o.optString("accent", "#8AB4F8"))
        } catch (_: Exception) {
            0xFF8AB4F8.toInt()
        }
        val p = Color(accent)
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
        if (type == "dynamic") {
            val entry = o.optString("entry", "index.html")
            if (!File(dir, entry).exists()) return null
            WblThemeSpec(
                id = ONLINE_THEME_PREFIX + rawId, name = name, desc = desc,
                light = scheme, dark = scheme,
                forceDark = true,
                gradient = listOf(p, Color(0xFF05070D)),
                dynamicDir = dir.absolutePath
            )
        } else {
            val wallName = o.optString("wallFile", "wallpaper.jpg")
            val wall = File(dir, wallName)
            if (!wall.exists()) return null
            WblThemeSpec(
                id = ONLINE_THEME_PREFIX + rawId, name = name, desc = desc,
                light = scheme, dark = scheme,
                forceDark = true,
                gradient = listOf(p, Color(0xFF101216)),
                photoPath = wall.absolutePath
            )
        }
    } catch (_: Exception) {
        null
    }
}

/** 已安装的在线主题（扫描 filesDir/themes/<id>/manifest.json） */
fun installedOnlineThemes(): List<WblThemeSpec> {
    val root = onlineThemesRoot()
    if (!root.isDirectory) return emptyList()
    return (root.listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .sortedBy { it.name }
        .mapNotNull { onlineThemeSpec(it.name) }
}

fun themeById(id: String): WblThemeSpec {
    if (id == CUSTOM_THEME_ID) {
        customThemeSpec()?.let { return it }
        return WBL_THEMES[0]
    }
    if (id.startsWith(ONLINE_THEME_PREFIX)) {
        onlineThemeSpec(id.removePrefix(ONLINE_THEME_PREFIX))?.let { return it }
        return WBL_THEMES[0]
    }
    return WBL_THEMES.firstOrNull { it.id == id } ?: WBL_THEMES[0]
}


/** 获取所有可用主题（含自定义 + 已安装在线主题） */
fun allThemes(): List<WblThemeSpec> {
    val list = WBL_THEMES.toMutableList()
    customThemeSpec()?.let { list.add(it) }
    list.addAll(installedOnlineThemes())
    return list
}

/** 当前是否为深色渲染（含强制深色主题） */
@Composable
fun wblIsDark(): Boolean {
    val spec = LocalWblTheme.current
    return spec.forceDark || isSystemInDarkTheme()
}
