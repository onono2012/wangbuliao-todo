package com.wangbuliao.todo.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 对比度 / 相对亮度纯函数。
 *
 * 不依赖 Compose / Android / 任何 UI 框架，可直接用 JUnit 单测覆盖。
 * 颜色分量约定：0f..1f 的线性存储值（与 Compose Color.red/green/blue 一致，
 * 切勿再除以 255）。亮度（luminance）为 WCAG 相对亮度，范围 0f..1f。
 *
 * 生产调用方：ui/ThemeUi.kt（顶栏文字色选择 + veil 补偿）。
 */
object ColorContrast {

    /** 近黑正文色（顶栏黑字方案）的 ARGB，与 ThemeUi 保持一致 */
    const val NEAR_BLACK_ARGB: Int = 0xFF1B1B1F.toInt()

    /** WCAG AA 正文最小对比度 */
    const val WCAG_AA: Float = 4.5f

    /** veil 补偿不透明度上限（避免过度遮盖渐变） */
    const val VEIL_MAX_ALPHA: Float = 0.35f

    /**
     * 白字达到 WCAG AA(4.5:1) 所需的背景最大亮度：
     * 1.05 / (L + 0.05) = 4.5  →  L = 0.1833…
     */
    const val LUM_MAX_FOR_WHITE_TEXT: Float = 0.183f

    /**
     * 近黑字(0xFF1B1B1F)达到 WCAG AA(4.5:1) 所需的背景最小亮度：
     * (L + 0.05) / (lumNearBlack + 0.05) = 4.5  →  L ≈ 0.2299…
     */
    const val LUM_MIN_FOR_DARK_TEXT: Float = 0.229f

    /** 单通道 sRGB 线性化（WCAG 定义） */
    private fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f
        else (((c + 0.055) / 1.055).toDouble().pow(2.4)).toFloat()

    /** 相对亮度（WCAG 定义，0f..1f）；r/g/b 为 0f..1f 分量 */
    fun relLuminance(r: Float, g: Float, b: Float): Float =
        0.2126f * channel(r) + 0.7152f * channel(g) + 0.0722f * channel(b)

    /** ARGB Int 颜色的相对亮度 */
    fun relLuminance(argb: Int): Float = relLuminance(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    /** 两个相对亮度之间的对比度（1f..21f），参数顺序无关（对称） */
    fun contrastRatio(l1: Float, l2: Float): Float {
        val hi = max(l1, l2)
        val lo = min(l1, l2)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** 前景色 ARGB / 背景色 ARGB 的对比度 */
    fun contrastRatioArgb(fgArgb: Int, bgArgb: Int): Float =
        contrastRatio(relLuminance(fgArgb), relLuminance(bgArgb))

    /** 一组背景亮度上，白字的最差对比度（渐变取所有色标的最小值） */
    fun worstContrastWhiteText(bgLums: List<Float>): Float =
        bgLums.minOf { contrastRatio(1f, it) }

    /** 一组背景亮度上，近黑字的最差对比度 */
    fun worstContrastDarkText(bgLums: List<Float>, darkLum: Float = relLuminance(NEAR_BLACK_ARGB)): Float =
        bgLums.minOf { contrastRatio(it, darkLum) }

    /**
     * 渐变背景上应选择深色文字还是白色文字：
     * 比较两种方案的「最差对比度」，取更大者。
     * @return true = 用近黑字（亮渐变），false = 用白字（暗渐变）
     */
    fun preferDarkText(bgLums: List<Float>, darkLum: Float = relLuminance(NEAR_BLACK_ARGB)): Boolean =
        worstContrastDarkText(bgLums, darkLum) > worstContrastWhiteText(bgLums)

    /**
     * 渐变顶栏 veil（遮纱）补偿：中亮度渐变上黑/白字都到不了 WCAG AA 时，
     * 叠一层极淡白纱（黑字方案，把最暗色标提到 L >= 0.229）
     * 或黑纱（白字方案，把最亮色标压到 L <= 0.183），上限 [VEIL_MAX_ALPHA]。
     *
     * @param bgLums 渐变色标亮度列表（非空）
     * @param darkText 是否黑字方案（preferDarkText 的结果）
     * @return 纱的颜色不透明度 0f..0.35f（0 = 无需补偿）；纱颜色由 darkText 决定（白纱/黑纱）
     */
    fun veilAlpha(bgLums: List<Float>, darkText: Boolean): Float =
        if (darkText) {
            // 黑字最差在最暗色标 → 白纱提亮至 LUM_MIN_FOR_DARK_TEXT
            val lmin = bgLums.min()
            if (lmin >= LUM_MIN_FOR_DARK_TEXT) 0f
            else min(VEIL_MAX_ALPHA, max(0f, (LUM_MIN_FOR_DARK_TEXT - lmin) / (1f - lmin)))
        } else {
            // 白字最差在最亮色标 → 黑纱压暗至 LUM_MAX_FOR_WHITE_TEXT
            val lmax = bgLums.max()
            if (lmax <= LUM_MAX_FOR_WHITE_TEXT) 0f
            else min(VEIL_MAX_ALPHA, max(0f, 1f - LUM_MAX_FOR_WHITE_TEXT / lmax))
        }

    /**
     * veil 补偿后，最差对比度是否达到 WCAG AA。
     * 线性亮度混合近似：newL = a*veilL + (1-a)*bgL（白纱 veilL=1，黑纱 veilL=0）。
     */
    fun meetsWcagAA(contrast: Float): Boolean = contrast >= WCAG_AA
}
