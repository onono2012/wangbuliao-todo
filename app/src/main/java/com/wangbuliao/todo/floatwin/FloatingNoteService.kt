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
import android.widget.TextView
import android.widget.Toast
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        Notif.ensureChannels(this)
        startForegroundCompat()
        addBubble()
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
                x = if (savedX >= 0) savedX else (resources.displayMetrics.widthPixels - dp(72))
                y = if (savedY >= 0) savedY else resources.displayMetrics.heightPixels / 3
            }
            attachDrag(v, p)
            wm.addView(v, p)
            bubbleView = v
            bubbleParams = p
        } catch (e: Exception) {
            Log.e(TAG, "addBubble failed", e)
        }
    }

    /** 拖动 + 点击（slop 区分）；松手吸附左右边缘 */
    private fun attachDrag(v: View, p: WindowManager.LayoutParams) {
        val slop = dp(8)
        v.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
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
                        if (!moved) {
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
            if (panelView == null) {
                val v = LayoutInflater.from(this).inflate(R.layout.float_panel, null)
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
                wm.addView(v, p)
                panelView = v
                panelParams = p
            }
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
        } catch (e: Exception) {
            Log.e(TAG, "closePanel failed", e)
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
