package com.wangbuliao.todo.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.wangbuliao.todo.MainActivity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份/恢复（手动触发，通用 WebDAV 通道，用户在设置页自行配置服务器/账号）。
 * 备份内容：SQLite 数据库 wbl.db + 自定义照片主题 files/themes/。
 * 恢复前先把当前数据本地兜底备份到 files/pre_restore/，避免误覆盖。
 */
object BackupManager {

    private const val REMOTE_NAME = "wbl-backup.zip"

    private fun remoteUrl() = Prefs.davUrl.value.trimEnd('/').let {
        if (it.endsWith("/$REMOTE_NAME")) it else "$it/$REMOTE_NAME"
    }

    fun configured(): Boolean = Prefs.davConfigured()

    private fun dbFile(ctx: Context) = ctx.getDatabasePath("wbl.db")
    private fun themesDir(ctx: Context) = File(ctx.filesDir, "themes")

    /** 把 db + themes 打包成 zip */
    fun pack(ctx: Context, out: File): File {
        // 确保 WAL 落盘，备份一致快照
        com.wangbuliao.todo.data.DbHelper.get(ctx).readableDatabase
            .rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            val db = dbFile(ctx)
            if (db.exists()) {
                z.putNextEntry(ZipEntry("databases/wbl.db"))
                db.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            val td = themesDir(ctx)
            if (td.isDirectory) td.listFiles()?.forEach { f ->
                z.putNextEntry(ZipEntry("themes/${f.name}"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out
    }

    /** 备份到用户配置的 WebDAV；返回描述文本 */
    suspend fun backup(ctx: Context): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!configured()) return@withContext "请先填写 WebDAV 服务器与账号"
        try {
            val tmp = File(ctx.cacheDir, "wbl-backup.zip")
            pack(ctx, tmp)
            val code = WebDav.put(remoteUrl(), Prefs.davUser.value, Prefs.davPass(), tmp)
            tmp.delete()
            if (code in 200..299) "备份成功（${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}）"
            else "备份失败 HTTP $code"
        } catch (e: Exception) {
            "备份失败：${e.javaClass.simpleName}（检查地址/网络/账号）"
        }
    }

    /** 恢复前本地兜底备份当前数据 */
    fun localSafetyBackup(ctx: Context): File {
        val dir = File(ctx.filesDir, "pre_restore").apply { mkdirs() }
        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        return pack(ctx, File(dir, "local-$ts.zip"))
    }

    /** 从 WebDAV 恢复；成功后重启进程使数据库重新加载。返回描述文本 */
    suspend fun restore(ctx: Context): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!configured()) return@withContext "请先填写 WebDAV 服务器与账号"
        val zip = File(ctx.cacheDir, "wbl-restore.zip")
        val code = try {
            WebDav.get(remoteUrl(), Prefs.davUser.value, Prefs.davPass(), zip)
        } catch (e: Exception) {
            zip.delete()
            return@withContext "恢复失败：${e.javaClass.simpleName}（检查地址/网络/账号）"
        }
        if (code != 200) { zip.delete(); return@withContext "云端无备份或下载失败 HTTP $code" }
        // 先解包到临时目录校验完整性，再落位替换，避免坏 zip 半写损坏现有数据
        val tmpDir = File(ctx.cacheDir, "restore_tmp").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                var e = z.nextEntry
                var dbOk = false
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory && !name.contains("..")) {
                        val target = when {
                            name.startsWith("databases/") -> File(tmpDir, name)
                            name.startsWith("themes/") -> File(tmpDir, name)
                            else -> null
                        }
                        if (target != null) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { z.copyTo(it) }
                            if (name == "databases/wbl.db") dbOk = true
                        }
                    }
                    e = z.nextEntry
                }
                if (!dbOk) throw java.io.IOException("备份包中缺少 wbl.db")
            }
        } catch (ex: Exception) {
            zip.delete(); tmpDir.deleteRecursively()
            return@withContext "恢复取消：备份包无效（${ex.javaClass.simpleName}），当前数据未动"
        }
        localSafetyBackup(ctx)  // 恢复前必须先备份当前数据
        // 关闭数据库连接后替换文件
        com.wangbuliao.todo.data.DbHelper.get(ctx).close()
        val db = dbFile(ctx)
        val td = themesDir(ctx).apply { mkdirs() }
        File(tmpDir, "databases/wbl.db").copyTo(db, overwrite = true)
        File(tmpDir, "themes").let { st ->
            if (st.isDirectory) st.listFiles()?.forEach { it.copyTo(File(td, it.name), overwrite = true) }
        }
        zip.delete(); tmpDir.deleteRecursively()
        // 重启进程加载恢复后的数据
        val app = ctx.applicationContext
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pi = android.app.PendingIntent.getActivity(app, 0, intent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val am = app.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 500, pi)
        Process.killProcess(Process.myPid())
        "恢复完成，应用重启中"
    }
}
