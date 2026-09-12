package com.wangbuliao.todo.util

import android.content.Context
import android.content.SharedPreferences
import com.wangbuliao.todo.AppCtx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 轻量偏好存储（SharedPreferences + StateFlow 双写）。
 * 所有 setter 同时更新持久值与内存 flow，UI 收集 flow 即时响应。
 */
object Prefs {
    private const val NAME = "wbl_prefs"
    private val sp: SharedPreferences
        get() = AppCtx.app.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── 主题 ──
    private val _theme = MutableStateFlow("rainbow")
    val theme: StateFlow<String> = _theme
    fun setTheme(id: String) {
        sp.edit().putString("theme_id", id).apply()
        _theme.value = id
    }

    // ── 悬浮窗 ──
    private val _floatEnabled = MutableStateFlow(false)
    val floatEnabled: StateFlow<Boolean> = _floatEnabled
    fun setFloatEnabled(b: Boolean) {
        sp.edit().putBoolean("float_enabled", b).apply()
        _floatEnabled.value = b
    }

    // 悬浮球位置（屏幕像素）
    var floatX: Int
        get() = sp.getInt("float_x", -1)
        set(v) { sp.edit().putInt("float_x", v).apply() }
    var floatY: Int
        get() = sp.getInt("float_y", -1)
        set(v) { sp.edit().putInt("float_y", v).apply() }

    // ── 置顶通知 ──
    private val _pinNotif = MutableStateFlow(false)
    val pinNotif: StateFlow<Boolean> = _pinNotif
    fun setPinNotif(b: Boolean) {
        sp.edit().putBoolean("pin_notif", b).apply()
        _pinNotif.value = b
    }

    // ── 提醒铃声 ──
    private val _ringUri = MutableStateFlow<String?>(null)
    val ringUri: StateFlow<String?> = _ringUri

    /** 铃声通道版本号：换铃声后 +1，通知渠道随之重建（Android 渠道声音创建后不可改） */
    val ringVer: Int
        get() = sp.getInt("ring_ver", 1)

    fun setRingUri(uri: String?) {
        sp.edit()
            .putString("ring_uri", uri)
            .putInt("ring_ver", ringVer + 1)
            .apply()
        _ringUri.value = uri
    }

    /** 在 WblApp.onCreate 中调用（AppCtx 就绪后） */
    fun init() {
        _theme.value = sp.getString("theme_id", "rainbow") ?: "rainbow"
        _floatEnabled.value = sp.getBoolean("float_enabled", false)
        _pinNotif.value = sp.getBoolean("pin_notif", false)
        _ringUri.value = sp.getString("ring_uri", null)
    }
}
