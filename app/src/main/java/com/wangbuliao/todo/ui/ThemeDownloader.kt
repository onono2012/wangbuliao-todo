package com.wangbuliao.todo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.wangbuliao.todo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** 在线主题条目（来自远端 themes.json） */
data class OnlineTheme(
    val id: String,
    val name: String,
    val desc: String,
    /** "static" = 静态壁纸 | "dynamic" = 动态主题包（zip） */
    val type: String,
    /** 封面相对路径（covers/xxx.jpg） */
    val coverUrl: String,
    /** 下载相对路径：静态=wallpapers/xxx.jpg，动态=packs/xxx.zip */
    val downloadUrl: String?,
    val resolution: String = "",
    val fileSize: Long = 0L,
    val author: String = "",
    val createdAt: String = ""
)

/**
 * 在线主题内容源：GitHub 仓库 `themes/` 目录，多线路自动切换。
 * 线路顺序：raw → gh-proxy.com → ghproxy.net → jsdelivr（上次成功线路优先）。
 */
object ThemeSource {
    private const val OWNER = "onono2012"
    private const val REPO = "wangbuliao-todo"
    private const val BASE_RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/main/themes/"

    val ROUTES: List<String> = listOf(
        BASE_RAW,
        "https://gh-proxy.com/$BASE_RAW",
        "https://ghproxy.net/$BASE_RAW",
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@main/themes/"
    )

    @Volatile
    var lastOkRoute: Int = 0
        private set

    fun markOk(idx: Int) {
        if (idx in ROUTES.indices) lastOkRoute = idx
    }

    /** 线路尝试顺序：上次成功线路优先，其余按序 */
    fun orderedRoutes(): List<Int> =
        ROUTES.indices.sortedWith(compareBy({ if (it == lastOkRoute) 0 else 1 }, { it }))

    fun url(routeIdx: Int, rel: String): String = ROUTES[routeIdx] + rel

    /** 按当前最优线路拼相对路径的完整 URL（封面等） */
    fun urlBest(rel: String): String = url(lastOkRoute, rel)
}

/**
 * 在线主题下载器：
 * - 多线路拉取 themes.json（org.json 解析）
 * - 多线路下载壁纸 / 主题包到 filesDir/themes/&lt;id&gt;/（持久化，不用 cacheDir）
 * - 动态主题自动解压 zip；静态主题保存 wallpaper.jpg
 * - 全部完成后写 manifest.json（onlineThemeSpec / allThemes 依赖它）
 */
class ThemeDownloader(private val context: Context) {

    // ────────────── 列表 ──────────────

    /** 拉取远端主题列表；全部线路失败返回 failure */
    suspend fun fetchThemeList(): Result<List<OnlineTheme>> = withContext(Dispatchers.IO) {
        var lastEx: Exception? = null
        for (idx in ThemeSource.orderedRoutes()) {
            try {
                val url = ThemeSource.url(idx, "themes.json") +
                    "?t=" + System.currentTimeMillis() / 60000
                val json = httpGetText(url)
                val list = parseThemes(json)
                if (list.isEmpty()) throw IOException("主题列表为空")
                ThemeSource.markOk(idx)
                Log.i(TAG, "themes.json loaded via route $idx (${list.size} themes)")
                return@withContext Result.success(list)
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "fetch themes route $idx failed: ${e.message}")
            }
        }
        Result.failure(lastEx ?: IOException("网络不可用"))
    }

    private fun parseThemes(json: String): List<OnlineTheme> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("themes") ?: return emptyList()
        val out = mutableListOf<OnlineTheme>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) continue
            out += OnlineTheme(
                id = id,
                name = name,
                desc = o.optString("desc").trim(),
                type = o.optString("type", "static").trim().ifEmpty { "static" },
                coverUrl = o.optString("coverUrl").trim(),
                downloadUrl = o.optString("downloadUrl").trim().ifEmpty { null },
                resolution = o.optString("resolution", "").trim(),
                fileSize = o.optLong("fileSize", 0L),
                author = o.optString("author", "").trim(),
                createdAt = o.optString("createdAt", "").trim()
            )
        }
        return out
    }

    // ────────────── 下载安装 ──────────────

    /**
     * 下载并安装主题到 filesDir/themes/&lt;id&gt;/，完成后写 manifest.json。
     * @param onProgress 0..1（总大小未知时回调 -1，UI 显示为不确定进度）
     */
    suspend fun downloadTheme(
        theme: OnlineTheme,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(File(context.filesDir, "themes"), theme.id)
        try {
            val rel = theme.downloadUrl ?: throw IOException("缺少下载地址")
            if (dir.exists()) dir.deleteRecursively()
            if (!dir.mkdirs()) throw IOException("无法创建主题目录")

            val tmp = File(dir, ".tmp.download")
            val size = downloadRouted(rel, tmp, onProgress)

            if (theme.type == "dynamic") {
                unzip(tmp, dir)
                tmp.delete()
                if (!File(dir, "index.html").exists()) throw IOException("主题包不完整")
            } else {
                val wall = File(dir, "wallpaper.jpg")
                if (!tmp.renameTo(wall)) {
                    tmp.copyTo(wall, overwrite = true)
                    tmp.delete()
                }
            }

            val accent = if (theme.type == "dynamic") {
                DEFAULT_ACCENT
            } else {
                extractAccent(File(dir, "wallpaper.jpg"))
            }

            val mf = JSONObject()
                .put("id", theme.id)
                .put("name", theme.name)
                .put("desc", theme.desc)
                .put("type", theme.type)
                .put("accent", accent)
                .put("fileSize", size)
                .put("author", theme.author)
                .put("createdAt", theme.createdAt)
            if (theme.type == "dynamic") mf.put("entry", "index.html")
            else mf.put("wallFile", "wallpaper.jpg")
            File(dir, "manifest.json").writeText(mf.toString())

            Log.i(TAG, "theme ${theme.id} installed (${size}B, accent=$accent)")
            Result.success(dir)
        } catch (e: Exception) {
            Log.w(TAG, "download theme ${theme.id} failed: ${e.message}")
            // 失败清理，避免半个目录被当成已安装
            try {
                dir.deleteRecursively()
            } catch (_: Exception) {
            }
            Result.failure(e)
        }
    }

    // ────────────── HTTP ──────────────

    /** 多线路下载到文件；返回写入字节数 */
    private fun downloadRouted(rel: String, out: File, onProgress: (Float) -> Unit): Long {
        var lastEx: Exception? = null
        for (idx in ThemeSource.orderedRoutes()) {
            try {
                val n = downloadOnce(ThemeSource.url(idx, rel), out, onProgress)
                ThemeSource.markOk(idx)
                return n
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "download route $idx failed: ${e.message}")
                out.delete()
            }
        }
        throw lastEx ?: IOException("下载失败")
    }

    /** 单次 GET → 文件；处理重定向（部分线路返回 302） */
    private fun downloadOnce(urlStr: String, out: File, onProgress: (Float) -> Unit): Long {
        var current = urlStr
        var hops = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = conn.responseCode
                if (code in 301..308 && hops < 5) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrEmpty()) {
                        current =
                            if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                        hops++
                        continue
                    }
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = conn.contentLength.toLong()
                var done = 0L
                var lastTick = 0L
                conn.inputStream.use { ins ->
                    FileOutputStream(out).use { os ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            os.write(buf, 0, n)
                            done += n
                            val now = System.nanoTime()
                            if (now - lastTick >= 120_000_000L) {
                                lastTick = now
                                onProgress(
                                    if (total > 0) {
                                        (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        -1f
                                    }
                                )
                            }
                        }
                    }
                }
                if (done <= 0L) throw IOException("空文件")
                if (total > 0 && done != total) throw IOException("下载不完整 $done/$total")
                onProgress(1f)
                return done
            } finally {
                conn.disconnect()
            }
        }
    }

    /** GET 文本（单次请求，含手动重定向兜底） */
    private fun httpGetText(urlStr: String): String {
        var current = urlStr
        var hops = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json, */*")
            }
            try {
                val code = conn.responseCode
                if (code in 301..308 && hops < 5) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrEmpty()) {
                        current =
                            if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                        hops++
                        continue
                    }
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    // ────────────── 工具 ──────────────

    /** 解压 zip 到目录（防路径穿越） */
    private fun unzip(zip: File, destDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.contains("..")) throw IOException("非法压缩包")
                val f = File(destDir, name)
                if (entry.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { os -> zis.copyTo(os) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** 从壁纸提取主题主色（缩略样平均 + 提亮保底） */
    private fun extractAccent(wall: File): String {
        return try {
            val opt = BitmapFactory.Options().apply { inSampleSize = 32 }
            val bmp = BitmapFactory.decodeFile(wall.absolutePath, opt) ?: return DEFAULT_ACCENT
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            val w = bmp.width
            val h = bmp.height
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val c = bmp.getPixel(x, y)
                    r += android.graphics.Color.red(c)
                    g += android.graphics.Color.green(c)
                    b += android.graphics.Color.blue(c)
                    n++
                    x += 2
                }
                y += 2
            }
            bmp.recycle()
            if (n == 0L) return DEFAULT_ACCENT
            var rr = (r / n).toInt()
            var gg = (g / n).toInt()
            var bb = (b / n).toInt()
            val maxv = maxOf(rr, gg, bb)
            if (maxv in 1..139) {
                val k = 168.0 / maxv
                rr = (rr * k).toInt().coerceIn(0, 255)
                gg = (gg * k).toInt().coerceIn(0, 255)
                bb = (bb * k).toInt().coerceIn(0, 255)
            }
            if (rr + gg + bb < 120) return DEFAULT_ACCENT
            String.format("#%02X%02X%02X", rr, gg, bb)
        } catch (_: Exception) {
            DEFAULT_ACCENT
        }
    }

    companion object {
        private const val TAG = "ThemeDownloader"
        private const val DEFAULT_ACCENT = "#8AB4F8"
        private val UA = "wangbuliao-todo/${BuildConfig.VERSION_NAME} (Android)"
    }
}
