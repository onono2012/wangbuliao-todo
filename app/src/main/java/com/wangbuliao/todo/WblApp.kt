package com.wangbuliao.todo

import android.app.Application
import com.wangbuliao.todo.reminder.Notif

/** 全局应用上下文持有者（在 WblApp.onCreate 中初始化） */
object AppCtx {
    lateinit var app: Application
}

class WblApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCtx.app = this
        Notif.ensureChannels(this)
    }
}
