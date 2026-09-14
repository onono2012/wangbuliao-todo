package com.wangbuliao.todo.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 玻璃雨珠动画背景 — 原生 WebView 承载 JS Canvas 动画
 *
 * @param url 页面地址：内置主题用 `file:///android_asset/glass-rain/index.html`；
 *            在线动态主题用 `file://<filesDir>/themes/<id>/index.html`
 *
 * 放在最底层，透明背景 + 禁止触摸，让上层 UI 正常工作。
 */
@Composable
fun GlassRainBackground(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { createGlassRainWebView(it) },
        update = { webView ->
            // loadUrl 只在首次调用（防止重组时反复重载动画）
            @Suppress("UNCHECKED_CAST")
            val last = webView.tag as? Pair<String, Boolean>
            if (last == null || last.first != url) {
                webView.loadUrl(url)
                webView.tag = Pair(url, true)
            }
        },
        modifier = modifier
    )
}

// 自定义 WebView：拦截触摸事件并返回 false（事件穿透到下层 Compose 层）
@SuppressLint("ClickableViewAccessibility")
private class TransparentWebView(context: Context) : WebView(context) {

    init {
        // 核心：完全透明
        setBackgroundColor(Color.TRANSPARENT)
        background = null

        // JS + 本地文件访问（在线主题从 filesDir 加载，需 allowFileAccess）
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)

        // 清除内边距
        setPadding(0, 0, 0, 0)

        // WebViewClient：确保本地资源正确加载
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                // 阻止任何导航（包括 hash 变化等）
                return true
            }
        }

        // 关键配置：拦截所有触摸事件，让事件传递到下层 Compose 内容
        setOnTouchListener { _, _ -> false }

        // 额外保险：让 view 不可聚焦、不可点击
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isEnabled = false

        // 启用硬件加速（Canvas 动画需要）— API>=21 WebView 默认启用
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
        ) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createGlassRainWebView(ctx: Context): TransparentWebView {
    return TransparentWebView(ctx).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
