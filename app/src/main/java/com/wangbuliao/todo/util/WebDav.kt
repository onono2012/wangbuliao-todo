package com.wangbuliao.todo.util

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端（PUT/GET），用于数据备份/恢复与崩溃日志上报。
 * 服务器地址/账号/密码全部来自用户在设置页录入的 Prefs（dav_url/dav_user/dav_pass），
 * 应用内零硬编码凭据，兼容任意标准 WebDAV 服务（123 网盘/坚果云/Nextcloud 等）。
 */
object WebDav {

    private fun auth(user: String, pass: String): String =
        "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)

    /** 上传本地文件到 WebDAV 路径；返回 HTTP 状态码 */
    fun put(url: String, user: String, pass: String, file: File): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "PUT"
        c.doOutput = true
        c.setRequestProperty("Authorization", auth(user, pass))
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.connectTimeout = 30_000
        c.readTimeout = 120_000
        c.setFixedLengthStreamingMode(file.length())
        c.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 64 * 1024) } }
        return c.responseCode.also { c.disconnect() }
    }

    /** 下载 WebDAV 文件到本地；返回 HTTP 状态码（200 表示成功写出） */
    fun get(url: String, user: String, pass: String, out: File): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.setRequestProperty("Authorization", auth(user, pass))
        c.connectTimeout = 30_000
        c.readTimeout = 120_000
        val code = c.responseCode
        if (code != 200) { c.disconnect(); return code }
        c.inputStream.use { ins -> FileOutputStream(out).use { ins.copyTo(it, 64 * 1024) } }
        c.disconnect()
        return 200
    }
}
