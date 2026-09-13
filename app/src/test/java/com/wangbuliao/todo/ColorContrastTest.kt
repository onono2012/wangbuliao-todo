package com.wangbuliao.todo

import com.wangbuliao.todo.util.ColorContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ColorContrast 纯函数单测（阶段6：对比度固化）。
 * 五类场景：
 *  1) 相对亮度 relLuminance（WCAG 定义、sRGB 线性化两分支、ARGB Int 入口）
 *  2) 对比度 contrastRatio 标准值（黑白 21:1、同色 1:1、对称性、#767676 白底 ≈4.54）
 *  3) 渐变最差对比度（多色标取最小：白字最差在最亮标、黑字最差在最暗标）
 *  4) 可读文字色选择 preferDarkText（暗渐变→白字、亮渐变→黑字、真实主题渐变回归）
 *  5) veil 补偿 veilAlpha（无需补偿=0、提亮/压暗到 WCAG AA 阈值、上限 0.35、补偿后达标）
 */
class ColorContrastTest {

    private val D = 0.002f // 浮点容差

    // ---------- 场景1：相对亮度 ----------

    @Test
    fun luminance_white_is_1() {
        assertEquals(1f, ColorContrast.relLuminance(1f, 1f, 1f), D)
    }

    @Test
    fun luminance_black_is_0() {
        assertEquals(0f, ColorContrast.relLuminance(0f, 0f, 0f), D)
    }

    @Test
    fun luminance_primaries_match_wcag_coefficients() {
        assertEquals(0.2126f, ColorContrast.relLuminance(1f, 0f, 0f), D)
        assertEquals(0.7152f, ColorContrast.relLuminance(0f, 1f, 0f), D)
        assertEquals(0.0722f, ColorContrast.relLuminance(0f, 0f, 1f), D)
    }

    @Test
    fun luminance_low_channel_uses_linear_branch() {
        // c <= 0.03928 → c/12.92（sRGB 线性段）
        val c = 0.03f
        assertEquals(c / 12.92f, ColorContrast.relLuminance(c, 0f, 0f) / 0.2126f, D)
    }

    @Test
    fun luminance_mid_gray_known_value() {
        // 0.5 灰 → ≈0.2140（WCAG 常用参考值）
        assertEquals(0.2140f, ColorContrast.relLuminance(0.5f, 0.5f, 0.5f), 0.001f)
    }

    @Test
    fun luminance_argb_int_entry() {
        assertEquals(1f, ColorContrast.relLuminance(0xFFFFFFFF.toInt()), D)
        assertEquals(0f, ColorContrast.relLuminance(0xFF000000.toInt()), D)
        // ARGB 与分量入口一致
        assertEquals(
            ColorContrast.relLuminance(0x1B / 255f, 0x1B / 255f, 0x1F / 255f),
            ColorContrast.relLuminance(ColorContrast.NEAR_BLACK_ARGB),
            D
        )
    }

    // ---------- 场景2：对比度标准值 ----------

    @Test
    fun contrast_black_on_white_is_21() {
        assertEquals(21f, ColorContrast.contrastRatio(1f, 0f), D)
        assertEquals(
            21f,
            ColorContrast.contrastRatioArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            D
        )
    }

    @Test
    fun contrast_same_color_is_1() {
        assertEquals(1f, ColorContrast.contrastRatio(0.37f, 0.37f), D)
        assertEquals(
            1f,
            ColorContrast.contrastRatioArgb(0xFF1B1B1F.toInt(), 0xFF1B1B1F.toInt()),
            D
        )
    }

    @Test
    fun contrast_is_symmetric() {
        assertEquals(
            ColorContrast.contrastRatio(0.2f, 0.8f),
            ColorContrast.contrastRatio(0.8f, 0.2f),
            D
        )
    }

    @Test
    fun contrast_wcag_example_gray_on_white() {
        // #767676 on white ≈ 4.54 —— WCAG AA 临界灰（经典示例）
        val ratio = ColorContrast.contrastRatioArgb(0xFF767676.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(4.54f, ratio, 0.02f)
        assertTrue(ColorContrast.meetsWcagAA(ratio))
        // #777777 起低于 4.5 则不达标（临界性验证）
        val below = ColorContrast.contrastRatioArgb(0xFF787878.toInt(), 0xFFFFFFFF.toInt())
        assertTrue("4.54 附近应处于 AA 临界", below in 4.4f..4.6f)
    }

    // ---------- 场景3：渐变最差对比度 ----------

    @Test
    fun worst_white_text_uses_brightest_stop() {
        val lums = listOf(0.05f, 0.5f, 0.9f)
        // 白字最差 = 最亮色标
        assertEquals(ColorContrast.contrastRatio(1f, 0.9f), ColorContrast.worstContrastWhiteText(lums), D)
    }

    @Test
    fun worst_dark_text_uses_darkest_stop() {
        val lums = listOf(0.05f, 0.5f, 0.9f)
        val darkLum = ColorContrast.relLuminance(ColorContrast.NEAR_BLACK_ARGB)
        // 黑字最差 = 最暗色标
        assertEquals(ColorContrast.contrastRatio(0.05f, darkLum), ColorContrast.worstContrastDarkText(lums), D)
    }

    @Test
    fun worst_contrast_single_stop_gradient() {
        assertEquals(
            ColorContrast.contrastRatio(1f, 0.3f),
            ColorContrast.worstContrastWhiteText(listOf(0.3f)),
            D
        )
    }

    // ---------- 场景4：可读文字色选择 ----------

    @Test
    fun dark_gradient_prefers_white_text() {
        assertFalse(ColorContrast.preferDarkText(listOf(0.01f, 0.05f)))
    }

    @Test
    fun bright_gradient_prefers_dark_text() {
        assertTrue(ColorContrast.preferDarkText(listOf(0.5f, 0.9f)))
    }

    @Test
    fun real_theme_gradients_regression() {
        // 极光青（亮渐变）→ 黑字；午夜蓝（暗渐变）→ 白字（v1.3.0 可读性修复回归）
        val aurora = listOf(0xFF7F7FD5.toInt(), 0xFF86A8E7.toInt(), 0xFF91EAE4.toInt()).map { ColorContrast.relLuminance(it) }
        assertTrue("极光青应选黑字", ColorContrast.preferDarkText(aurora))
        val midnight = listOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()).map { ColorContrast.relLuminance(it) }
        assertFalse("午夜蓝应选白字", ColorContrast.preferDarkText(midnight))
        // 鎏金（亮）→ 黑字；樱花粉（亮）→ 黑字
        val gilded = listOf(0xFFF7E7CE.toInt(), 0xFFE6C88A.toInt()).map { ColorContrast.relLuminance(it) }
        assertTrue("鎏金应选黑字", ColorContrast.preferDarkText(gilded))
        val sakura = listOf(0xFFFBD3E9.toInt(), 0xFFF9A8C9.toInt()).map { ColorContrast.relLuminance(it) }
        assertTrue("樱花粉应选黑字", ColorContrast.preferDarkText(sakura))
    }

    // ---------- 场景5：veil 补偿 ----------

    @Test
    fun veil_zero_when_dark_text_already_readable() {
        // 最暗标已达 0.229 → 无需白纱
        assertEquals(0f, ColorContrast.veilAlpha(listOf(0.23f, 0.6f), darkText = true), D)
    }

    @Test
    fun veil_zero_when_white_text_already_readable() {
        // 最亮标已 <= 0.183 → 无需黑纱
        assertEquals(0f, ColorContrast.veilAlpha(listOf(0.02f, 0.18f), darkText = false), D)
    }

    @Test
    fun veil_white_lifts_darkest_stop_to_threshold() {
        val lums = listOf(0.10f, 0.60f)
        val a = ColorContrast.veilAlpha(lums, darkText = true)
        assertEquals(0.1433f, a, 0.002f) // (0.229-0.1)/0.9
        // 补偿后最暗标亮度 ≈ 0.229 → 黑字对比度达 AA
        val lifted = 0.10f + a * (1f - 0.10f)
        val darkLum = ColorContrast.relLuminance(ColorContrast.NEAR_BLACK_ARGB)
        assertTrue(
            "白纱补偿后黑字应达 WCAG AA",
            ColorContrast.meetsWcagAA(ColorContrast.contrastRatio(lifted, darkLum))
        )
    }

    @Test
    fun veil_black_dims_brightest_stop_to_threshold() {
        val lums = listOf(0.05f, 0.25f)
        val a = ColorContrast.veilAlpha(lums, darkText = false)
        assertEquals(0.268f, a, 0.002f) // 1 - 0.183/0.25
        // 补偿后最亮标 ≈ 0.183 → 白字对比度达 AA
        val dimmed = 0.25f * (1f - a)
        assertTrue(
            "黑纱补偿后白字应达 WCAG AA",
            ColorContrast.meetsWcagAA(ColorContrast.contrastRatio(1f, dimmed))
        )
    }

    @Test
    fun veil_alpha_capped_at_35_percent() {
        // 需要很大补偿时封顶 0.35（避免过度遮盖渐变）
        val a = ColorContrast.veilAlpha(listOf(0.05f, 0.9f), darkText = false)
        assertEquals(ColorContrast.VEIL_MAX_ALPHA, a, D)
    }
}
