package com.wangbuliao.todo.floatwin

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.R
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.reminder.Notif
import com.wangbuliao.todo.reminder.PinNotifService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 随手记悬浮窗：可拖动悬浮球，点开输入面板，任意界面下极速记录。
 * 前台服务(specialUse)保活；面板保存走 TaskRepo.quickNote（分类=随手记）。
 */
class FloatingNoteService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var panelOpen = false

    // ── v1.5.4：10 秒无操作自动贴边（半隐藏 + 变淡，触摸即恢复） ──
    private val dockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dockRunnable = Runnable { dockToEdge() }
    private var docked = false
    private var dockSide = 1          // -1 左边缘 / 1 右边缘
    private var bubbleAnim: android.animation.ValueAnimator? = null

    private fun scheduleDock() {
        dockHandler.removeCallbacks(dockRunnable)
        if (!panelOpen) dockHandler.postDelayed(dockRunnable, IDLE_DOCK_MS)
    }

    private fun cancelDock() {
        dockHandler.removeCallbacks(dockRunnable)
    }

    /** 贴边：悬浮球一半移出屏幕侧边并变淡 */
    private fun dockToEdge() {
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        if (docked || panelOpen) return
        val w = resources.displayMetrics.widthPixels
        val onLeft = p.x + v.width / 2 < w / 2
        dockSide = if (onLeft) -1 else 1
        val targetX = if (onLeft) -v.width / 2 else w - v.width / 2
        docked = true
        animateBubble(p.x, targetX, DOCK_ALPHA)
    }

    /** 恢复：立即滑回完全可见位置并恢复不透明（触摸场景需即时响应，避免与拖拽动画冲突） */
    private fun undock() {
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        if (!docked) return
        docked = false
        bubbleAnim?.cancel()
        val w = resources.displayMetrics.widthPixels
        p.x = if (dockSide < 0) dp(4) else w - v.width - dp(4)
        v.alpha = 1f
        try {
            wm.updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    private fun animateBubble(fromX: Int, toX: Int, toAlpha: Float) {
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        bubbleAnim?.cancel()
        val fromAlpha = v.alpha
        bubbleAnim = android.animation.ValueAnimator.ofInt(fromX, toX).apply {
            duration = 220
            addUpdateListener { a ->
                val f = a.animatedFraction
                p.x = a.animatedValue as Int
                v.alpha = fromAlpha + (toAlpha - fromAlpha) * f
                try {
                    wm.updateViewLayout(v, p)
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        Notif.ensureChannels(this)
        startForegroundCompat()
        addBubble()
        // 主题切换即时跟随：悬浮球重新上色/换贴图
        scope.launch {
            com.wangbuliao.todo.util.Prefs.theme.collect {
                bubbleView?.let { applyBubbleTheme(it) }
                if (panelOpen) panelView?.let { applyPanelTheme(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startForegroundCompat() {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 8, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, Notif.CH_FLOAT)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("随手记悬浮窗运行中")
            .setContentText("点悬浮球快速记录 · 设置页可关闭")
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        try {
            val inflater = LayoutInflater.from(this)
            val v = inflater.inflate(R.layout.float_bubble, null)
            applyBubbleTheme(v)
            val savedX = com.wangbuliao.todo.util.Prefs.floatX
            val savedY = com.wangbuliao.todo.util.Prefs.floatY
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (savedX >= 0) savedX else (resources.displayMetrics.widthPixels - dp(56))
                y = if (savedY >= 0) savedY else resources.displayMetrics.heightPixels / 3
            }
            attachDrag(v, p)
            wm.addView(v, p)
            bubbleView = v
            bubbleParams = p
            // 启动后即开始 10 秒无操作计时
            scheduleDock()
        } catch (e: Exception) {
            Log.e(TAG, "addBubble failed", e)
        }
    }

    /** 拖动 + 点击（slop 区分）；松手吸附左右边缘 */
    /** 悬浮球按主题渲染：照片主题=圆形贴图，其余=主题渐变/主色球（修复旧版固定彩虹色） */
    private fun applyBubbleTheme(v: View) {
        try {
            val spec = com.wangbuliao.todo.ui.themeById(
                com.wangbuliao.todo.util.Prefs.theme.value
            )
            val text = v.findViewById<TextView>(R.id.bubble_text)
            val photo = v.findViewById<ImageView>(R.id.bubble_photo)
            val size = dp(40)
            // 圆图来源：drawable 球面图 > 自定义照片文件 > 无（渐变球）
            val src: android.graphics.Bitmap? = when {
                spec.bubbleRes != null ->
                    android.graphics.BitmapFactory.decodeResource(resources, spec.bubbleRes)
                spec.photoPath != null ->
                    com.wangbuliao.todo.media.ImageStore.thumb(spec.photoPath, size * 3)
                else -> null
            }
            if (src != null) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(src, size, size, true)
                val out = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(out)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.shader = android.graphics.BitmapShader(
                    scaled,
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                photo.setImageBitmap(out)
                photo.visibility = View.VISIBLE
                text.visibility = View.GONE
            } else {
                photo.visibility = View.GONE
                text.visibility = View.VISIBLE
                text.background = themedOval(spec)
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyBubbleTheme failed", e)
        }
    }

    /** 主题椭圆背景：阴影层 + 渐变/主色主体（等价 bg_bubble_shadow，但用当前主题色） */
    private fun themedOval(
        spec: com.wangbuliao.todo.ui.WblThemeSpec
    ): android.graphics.drawable.Drawable {
        val gradColors = if (spec.gradient != null) {
            spec.gradient.map { it.toArgb() }.toIntArray()
        } else {
            val p = spec.light.primary.toArgb()
            intArrayOf(p, p)
        }
        val shadow = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x33000000)
        }
        val main = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, gradColors
        ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        return android.graphics.drawable.LayerDrawable(
            arrayOf<android.graphics.drawable.Drawable>(shadow, main)
        ).apply {
            setLayerInset(0, dp(1), dp(2), dp(1), 0)
            setLayerInset(1, dp(1), dp(1), dp(1), dp(2))
        }
    }

    private fun attachDrag(v: View, p: WindowManager.LayoutParams) {
        val slop = dp(8)
        v.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false
            private var wasDocked = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 触摸即打断贴边计时；处于贴边态时本次触摸只负责恢复
                        cancelDock()
                        wasDocked = docked
                        if (wasDocked) undock()
                        downX = e.rawX
                        downY = e.rawY
                        startX = p.x
                        startY = p.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            p.x = (startX + dx).coerceAtLeast(0)
                            p.y = (startY + dy).coerceAtLeast(0)
                            try {
                                wm.updateViewLayout(v, p)
                            } catch (_: Exception) {
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (wasDocked && !moved) {
                            // 贴边态下首次点按只负责恢复，不打开面板
                        } else if (!moved) {
                            togglePanel()
                        } else {
                            // 吸附屏幕左右边缘
                            val w = resources.displayMetrics.widthPixels
                            p.x = if (p.x + v.width / 2 < w / 2) dp(4) else w - v.width - dp(4)
                            try {
                                wm.updateViewLayout(v, p)
                            } catch (_: Exception) {
                            }
                            com.wangbuliao.todo.util.Prefs.floatX = p.x
                            com.wangbuliao.todo.util.Prefs.floatY = p.y
                        }
                        scheduleDock()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun togglePanel() {
        if (panelOpen) closePanel() else openPanel()
    }

    private fun openPanel() {
        try {
            cancelDock()
            if (panelView == null) {
                val v = LayoutInflater.from(this).inflate(R.layout.float_panel, null)
                applyPanelTheme(v)
                wireMediaButtons(v)
                val p = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(48)
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                }
                val et = v.findViewById<EditText>(R.id.et_note)
                v.findViewById<TextView>(R.id.btn_save).setOnClickListener {
                    val text = et.text.toString().trim()
                    if (text.isEmpty()) {
                        toast("先写点什么再保存哦")
                        return@setOnClickListener
                    }
                    et.setText("")
                    scope.launch(Dispatchers.IO) {
                        try {
                            TaskRepo.quickNote(text)
                            PinNotifService.refresh(applicationContext)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(applicationContext)
                            com.wangbuliao.todo.util.Haptics.quickSaved(applicationContext)
                            launch(Dispatchers.Main) { toast("已记入「随手记」✍") }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) { toast("保存失败：${e.message}") }
                        }
                    }
                    closePanel()
                }
                v.findViewById<TextView>(R.id.btn_open).setOnClickListener {
                    val i = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("wbl_from_float", true)
                    }
                    try {
                        startActivity(i)
                    } catch (e: Exception) {
                        Log.e(TAG, "open app failed", e)
                    }
                    closePanel()
                }
                v.findViewById<TextView>(R.id.btn_close).setOnClickListener { closePanel() }
                // 关闭悬浮窗：停服务并同步关闭设置页开关（Boot/App 自启均以该开关为门控）
                v.findViewById<TextView>(R.id.btn_float_off).setOnClickListener {
                    com.wangbuliao.todo.util.Prefs.setFloatEnabled(false)
                    closePanel()
                    toast("悬浮窗已关闭，可在设置页重新开启")
                    stopSelf()
                }
                wm.addView(v, p)
                panelView = v
                panelParams = p
            }
            panelView?.let { applyPanelTheme(it) }
            panelView?.visibility = View.VISIBLE
            panelOpen = true
            bubbleView?.visibility = View.GONE
            panelView?.findViewById<EditText>(R.id.et_note)?.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "openPanel failed", e)
        }
    }

    private fun closePanel() {
        try {
            panelView?.let {
                it.findViewById<EditText>(R.id.et_note)?.setText("")
                it.visibility = View.GONE
            }
            bubbleView?.visibility = View.VISIBLE
            panelOpen = false
            // 面板收起后重新开始 10 秒无操作计时
            scheduleDock()
        } catch (e: Exception) {
            Log.e(TAG, "closePanel failed", e)
        }
    }

    /** 面板主题化：深色玻璃底 + 当前主题主色按钮/描边（跟随主题切换） */
    private fun applyPanelTheme(v: View) {
        try {
            val spec = com.wangbuliao.todo.ui.themeById(
                com.wangbuliao.todo.util.Prefs.theme.value
            )
            val primary = if (spec.gradient != null) {
                spec.gradient[0].toArgb()
            } else {
                spec.light.primary.toArgb()
            }
            val glass = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xF2161822.toInt())
                setStroke(dp(1), (primary and 0x00FFFFFF) or 0x55000000)
            }
            v.findViewById<View>(R.id.panel_root).background = glass
            val ghost = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0x1AFFFFFF)
                setStroke(dp(1), 0x33FFFFFF)
            }
            val solid = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(primary)
            }
            val field = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0x14FFFFFF)
                setStroke(dp(1), 0x22FFFFFF)
            }
            listOf(R.id.btn_open, R.id.btn_cam, R.id.btn_gallery, R.id.btn_record)
                .forEach { v.findViewById<View>(it).background = ghost }
            v.findViewById<View>(R.id.btn_save).background = solid
            v.findViewById<View>(R.id.et_note).background = field
            // 主色太暗时保存按钮文字改白（黑金等主题主色偏亮则用深字）
            val lum = (android.graphics.Color.red(primary) * 299 +
                android.graphics.Color.green(primary) * 587 +
                android.graphics.Color.blue(primary) * 114) / 1000
            v.findViewById<TextView>(R.id.btn_save)
                .setTextColor(if (lum > 150) 0xFF101010.toInt() else 0xFFFFFFFF.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "applyPanelTheme failed", e)
        }
    }

    /** 媒体按钮：拍照 / 相册 / 录音 → FloatMediaActivity（透明页完成后直接入库） */
    private fun wireMediaButtons(v: View) {
        v.findViewById<View>(R.id.btn_cam).setOnClickListener {
            launchMedia(FloatMediaActivity.ACTION_CAMERA)
        }
        v.findViewById<View>(R.id.btn_gallery).setOnClickListener {
            launchMedia(FloatMediaActivity.ACTION_GALLERY)
        }
        v.findViewById<View>(R.id.btn_record).setOnClickListener {
            launchMedia(FloatMediaActivity.ACTION_RECORD)
        }
    }

    private fun launchMedia(action: String) {
        try {
            val i = Intent(this, FloatMediaActivity::class.java).apply {
                // v1.5.4：MULTIPLE_TASK 配合 manifest 空 taskAffinity，
                // 每次采集都进独立任务栈，完成后不回落主 App 界面
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                putExtra(FloatMediaActivity.EXTRA_ACTION, action)
            }
            startActivity(i)
            closePanel()
        } catch (e: Exception) {
            Log.e(TAG, "launchMedia failed", e)
        }
    }

    private fun toast(s: String) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        dockHandler.removeCallbacksAndMessages(null)
        bubbleAnim?.cancel()
        try {
            bubbleView?.let { wm.removeView(it) }
            panelView?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        bubbleView = null
        panelView = null
        scope.cancel()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "WblFloat"
        private const val NOTIF_ID = 9001

        /** 无操作自动贴边延时（毫秒） */
        private const val IDLE_DOCK_MS = 10_000L
        /** 贴边后悬浮球透明度（半隐藏变淡） */
        private const val DOCK_ALPHA = 0.35f

        fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        fun overlaySettingsIntent(ctx: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${ctx.packageName}")
            )

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, FloatingNoteService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, FloatingNoteService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }
    }
}
