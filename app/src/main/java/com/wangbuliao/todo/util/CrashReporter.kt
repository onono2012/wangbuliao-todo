package com.wangbuliao.todo.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.wangbuliao.todo.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃监控（阶段5）：
 *  1. 全局捕获未处理异常，落盘到 files/crash/（crash-时间戳-版本.txt，只留最近 5 份）；
 *  2. 下次启动 MainActivity 读取最近一次崩溃并弹窗提示（每份只提示一次）；
 *  3. 检查更新时顺带上报：已配置 WebDAV 则把崩溃日志 PUT 到备份同目录
 *     （wbl-crash-*.txt），上传成功后删除本地文件；失败保留下次再传。
 *
 * 说明：只记录异常堆栈与设备/版本信息，不采集任务内容等用户数据。
 */
object CrashReporter {

    private const val TAG = "WblCrash"
    private const val MAX_KEEP = 5
    private const val PREFS = "wbl_crash"
    private const val KEY_SHOWN = "last_shown"

    private fun dir(ctx: Context) = File(ctx.filesDir, "crash")

    /** 在 Application.onCreate 调用：安装全局未捕获异常处理器 */
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                write(app, thread, e)
            } catch (_: Exception) {
            }
            // 交还系统默认处理（弹系统崩溃框/杀进程），不改变崩溃行为
            prev?.uncaughtException(thread, e)
        }
    }

    private fun write(ctx: Context, thread: Thread, e: Throwable) {
        val d = dir(ctx).apply { mkdirs() }
        val now = Date()
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        val f = File(d, "wbl-crash-$ts-v${BuildConfig.VERSION_NAME}.txt")
        PrintWriter(f.outputStream().bufferedWriter()).use { w ->
            w.println("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)}")
            w.println("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            w.println("device: ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            w.println("thread: ${thread.name}")
            w.println("--- stacktrace ---")
            e.printStackTrace(w)
        }
        // 只保留最近 MAX_KEEP 份
        d.listFiles()?.sortedByDescending { it.name }?.drop(MAX_KEEP)?.forEach { it.delete() }
        Log.e(TAG, "crash written: ${f.name}")
    }

    /** 本地全部崩溃日志（旧→新） */
    fun pending(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.name.startsWith("wbl-crash-") }
            ?.sortedBy { it.name } ?: emptyList()

    /**
     * 最近一次「尚未提示过」的崩溃，供下次启动弹窗；没有则 null。
     * 弹窗后调 markShown 记录，同一份崩溃只提示一次（文件保留等待上报）。
     */
    fun unshownLatest(ctx: Context): File? {
        val last = pending(ctx).lastOrNull() ?: return null
        val shown = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SHOWN, null)
        return if (last.name == shown) null else last
    }

    fun markShown(ctx: Context, f: File) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SHOWN, f.name).apply()
    }

    fun delete(f: File) {
        try { f.delete() } catch (_: Exception) { }
    }

    /**
     * 检查更新时顺带上报（挂起函数，IO 线程调用）：
     * 未配置 WebDAV 或无待传日志时直接返回 0；
     * 逐个 PUT 到备份同目录，成功即删本地；任一失败即中止（下次再试）。
     * @return 成功上传份数
     */
    suspend fun uploadPending(ctx: Context): Int =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!Prefs.davConfigured()) return@withContext 0
            val files = pending(ctx)
            if (files.isEmpty()) return@withContext 0
            var ok = 0
            for (f in files) {
                try {
                    val code = WebDav.put(crashUrl(f.name), Prefs.davUser.value, Prefs.davPass(), f)
                    if (code in 200..299) {
                        f.delete()
                        ok++
                    } else {
                        Log.w(TAG, "upload ${f.name} http $code")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "upload ${f.name} failed: ${e.javaClass.simpleName}")
                    break
                }
            }
            if (ok > 0) Log.i(TAG, "uploaded $ok crash report(s)")
            ok
        }

    /** 崩溃日志上传地址：备份文件同目录（davUrl 去掉 /wbl-backup.zip 后缀）+ 原文件名 */
    private fun crashUrl(name: String): String {
        val u = Prefs.davUrl.value.trimEnd('/')
        val base = if (u.endsWith("/wbl-backup.zip")) u.removeSuffix("/wbl-backup.zip") else u
        return "$base/$name"
    }
}
