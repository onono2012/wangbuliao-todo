package com.wangbuliao.todo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.wangbuliao.todo.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线更新（GitHub Releases · 多线路中转 · 失败自动切换）：
 *
 * 检查线路（按序尝试，任一成功即返回；上次成功的线路下次优先）：
 *  1. raw.githubusercontent.com 直连仓库 main 分支的 wbl-update.json
 *  2. gh-proxy.com 中转
 *  3. ghproxy.net 中转
 *  4. cdn.jsdelivr.net（jsDelivr CDN）
 *  5. api.github.com releases/latest（兼容旧格式 Release，兜底）
 *
 * wbl-update.json 统一格式（由发布工具 publish.py 生成并提交到仓库 main）：
 *  { "versionCode":10200, "versionName":"1.2.0", "force":true, "desc":"…Markdown…",
 *    "sha256":"…", "size":123456, "fileName":"wbl-v1.2.0-vc10200.apk",
 *    "routes":["https://api.github.com/repos/…/releases/assets/<id>", "https://gh-proxy.com/…", …] }
 *
 * 下载线路：优先使用 json 内 routes（发布方指定），并自动补齐
 * gh-proxy / ghproxy.net / ghfast.top 中转与 github.com 直链兜底；
 * 单线路失败（连接/超时/HTTP 错误/流中断）自动切换下一条，断点续传（Range）跨线路共用，
 * 完成后 sha256 + 文件大小双重校验，不符即删包报错。
 *
 * versionCode 大于当前版本即弹出强制更新对话框（不可取消），下载后调起系统安装。
 */
object Updater {
    private const val TAG = "WblUpdater"
    const val REPO_OWNER = "onono2012"
    const val REPO_NAME = "wangbuliao-todo"
    private const val API_LATEST =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RAW_JSON =
        "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/wbl-update.json"
    private const val GH_BASE = "https://github.com/$REPO_OWNER/$REPO_NAME"

    /** 检查更新线路表（顺序即默认优先级，末位为 GitHub API 兜底） */
    private val CHECK_ROUTES = listOf(
        RAW_JSON,
        "https://gh-proxy.com/$RAW_JSON",
        "https://ghproxy.net/$RAW_JSON",
        "https://cdn.jsdelivr.net/gh/$REPO_OWNER/$REPO_NAME@main/wbl-update.json",
        API_LATEST
    )

    /** 下载中转前缀（发布方 routes 缺失时按此补齐兜底线路） */
    private val DL_PROXIES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/"
    )

    /** 线路测速：每条线路并行拉取前 PROBE_BYTES 字节测实际吞吐，选最快 */
    private const val PROBE_BYTES = 256 * 1024
    private const val PROBE_CONNECT_TIMEOUT = 6_000
    private const val PROBE_READ_TIMEOUT = 6_000

    /** 单条线路测速总时限：超时即用已读字节估算（慢线路不拖累整体测速耗时） */
    private const val PROBE_DEADLINE_NS = 5_000_000_000L

    private const val PREFS = "wbl_prefs"
    private const val KEY_CHECK_ROUTE = "last_check_route"
    private const val KEY_DL_ROUTE = "last_dl_route"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Info(
        val versionCode: Int = 0,
        val versionName: String = "",
        val fileName: String = "",
        /** 下载地址线路表（按优先级排序，下载失败自动切换下一条） */
        val routes: List<String> = emptyList(),
        val desc: String = "",
        /** 安装包 sha256（发布方提供），用于下载完整性校验 */
        val sha256: String = "",
        val expectedSize: Long = 0,
        val force: Boolean = true
    )

    data class State(
        val checking: Boolean = false,
        val downloading: Boolean = false,
        /** 0f..1f 下载进度，-1 表示未知/未下载 */
        val progress: Float = -1f,
        val message: String? = null,
        val found: Info? = null,
        val downloadedFile: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun dismissFound() {
        _state.value = _state.value.copy(found = null)
    }

    /**
     * @param silent 启动自动检查时为 true：仅发现新版本才更新状态；
     *               已是最新/检查失败均静默（不打扰用户）。
     */
    fun check(ctx: Context, silent: Boolean = false) {
        if (_state.value.checking || _state.value.downloading) return
        _state.value = State(checking = true, found = _state.value.found)
        val app = ctx.applicationContext
        scope.launch {
            // 顺带上报崩溃日志（已配置 WebDAV 才生效，失败静默不影响检查更新）
            launch {
                try {
                    com.wangbuliao.todo.util.CrashReporter.uploadPending(app)
                } catch (_: Exception) {
                }
            }
            val routes = orderedCheckRoutes(app)
            var lastEx: Exception? = null
            for ((idx, route) in routes.withIndex()) {
                try {
                    // 检查线路多为 CDN/中转代理，会缓存旧 json；附加 cb 时间戳强制取最新
                    // （下载线路不加 cb：需命中代理缓存以省流量，靠 sha256 校验保证正确性）
                    val o = JSONObject(httpGetOnce(withCacheBuster(route)))
                    val info = if (o.has("tag_name")) parseRelease(o) else parseUpdateJson(o)
                    if (info.versionCode <= 0 || info.routes.isEmpty()) {
                        throw IOException("更新信息格式不正确")
                    }
                    saveRoutePref(app, KEY_CHECK_ROUTE, route)
                    Log.i(TAG, "check ok via route ${routeLabel(route)}")
                    _state.value = when {
                        info.versionCode > BuildConfig.VERSION_CODE ->
                            State(
                                found = info,
                                message = if (silent) null else "发现新版本 v${info.versionName}"
                            )
                        else ->
                            if (silent) State()
                            else State(message = "当前已是最新版本 v${BuildConfig.VERSION_NAME}")
                    }
                    return@launch
                } catch (e: Exception) {
                    lastEx = e
                    Log.w(TAG, "check route ${routeLabel(route)} failed: $e")
                    if (idx < routes.size - 1) {
                        _state.value = _state.value.copy(
                            message = if (silent) null
                            else "线路 ${routeLabel(route)} 失败，自动切换（${idx + 2}/${routes.size}）…"
                        )
                    }
                }
            }
            Log.e(TAG, "all check routes failed", lastEx)
            _state.value = if (silent) State()
            else State(message = "检查更新失败：所有线路均不可达，请稍后重试")
        }
    }

    /** 解析统一格式 wbl-update.json */
    private fun parseUpdateJson(o: JSONObject): Info {
        val routes = mutableListOf<String>()
        o.optJSONArray("routes")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i, "").takeIf { it.isNotBlank() }?.let(routes::add)
            }
        }
        return Info(
            versionCode = o.optInt("versionCode", 0),
            versionName = o.optString("versionName", ""),
            fileName = o.optString("fileName", ""),
            routes = routes,
            desc = o.optString("desc", "").trim(),
            sha256 = o.optString("sha256", "").removePrefix("sha256:"),
            expectedSize = o.optLong("size", 0L),
            force = o.optBoolean("force", true)
        )
    }

    /** 解析 GitHub API releases/latest（兜底线路，旧格式兼容） */
    private fun parseRelease(o: JSONObject): Info {
        val tag = o.optString("tag_name", "")
        val body = (o.optString("body", "") ?: "").trim()
        var fileName = ""
        var apkVc = 0
        var apkSha = ""
        var apkSize = 0L
        val routes = mutableListOf<String>()
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val m = APK_VC_RE.find(a.optString("name", ""))
                if (m != null) {
                    fileName = a.optString("name", "")
                    apkVc = m.groupValues[1].toIntOrNull() ?: 0
                    apkSha = a.optString("digest", "").removePrefix("sha256:")
                    apkSize = a.optLong("size", 0L)
                    // api.github.com 资产端点优先（本机实测 github.com 直链不可达时可用）
                    val assetId = a.optLong("id", 0L)
                    if (assetId > 0) {
                        routes.add(
                            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/assets/$assetId"
                        )
                    }
                    val direct = a.optString("browser_download_url", "")
                    if (direct.isNotBlank()) {
                        for (p in DL_PROXIES) routes.add(p + direct)
                        routes.add(direct)
                    }
                    break
                }
            }
        }
        val vName = if (tag.startsWith("v")) tag.substring(1) else tag
        return Info(
            versionCode = apkVc,
            versionName = vName,
            fileName = fileName,
            routes = routes,
            desc = body,
            sha256 = apkSha,
            expectedSize = apkSize
        )
    }

    private val APK_VC_RE = Regex("-vc(\\d+)\\.apk$", RegexOption.IGNORE_CASE)

    fun download(ctx: Context, info: Info) {
        val app = ctx.applicationContext
        _state.value = _state.value.copy(
            downloading = true, progress = 0f,
            message = "正在下载 v${info.versionName}…"
        )
        scope.launch {
            try {
                val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("存储不可用")
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, "wangbuliao_v${info.versionName}.apk")
                val baseRoutes = orderedDownloadRoutes(app, info)
                // 线路测速：并行探测每条线路前 256KB 实际吞吐，最快者优先（失败线路垫底兜底）
                _state.value = _state.value.copy(
                    message = "正在测速 ${baseRoutes.size} 条线路，选择最快的…"
                )
                val routes = speedTestRoutes(baseRoutes)
                var lastEx: Exception? = null
                var okRoute: String? = null
                var ri = 0
                val dlStart = System.nanoTime()
                // 跨线路总共最多尝试 10 次；每次失败切换下一条线路（断点续传共用同一文件）
                for (attempt in 1..10) {
                    val route = routes[(ri++) % routes.size]
                    try {
                        if (downloadOnce(route, out)) {
                            okRoute = route
                            break
                        }
                    } catch (e: Exception) {
                        lastEx = e
                        Log.w(TAG, "download route ${routeLabel(route)} attempt $attempt failed: $e")
                    }
                    _state.value = _state.value.copy(
                        message = "线路 ${routeLabel(route)} 失败，自动切换下一条（已试 ${minOf(ri, routes.size)}/${routes.size} 条）…"
                    )
                    if (attempt < 10) Thread.sleep(800L * (attempt % 3 + 1))
                }
                if (okRoute == null) throw lastEx ?: IOException("下载失败")
                if (out.length() <= 0) throw IOException("下载内容为空")
                if (info.expectedSize > 0 && out.length() != info.expectedSize) {
                    val got = out.length()
                    out.delete()
                    throw IOException("文件大小不符（$got/${info.expectedSize}），已删除，请重试")
                }
                if (info.sha256.isNotBlank()) {
                    val actual = sha256Hex(out)
                    if (!actual.equals(info.sha256, ignoreCase = true)) {
                        out.delete()
                        throw IOException("安装包校验失败（sha256 不符），已删除，请重试")
                    }
                }
                saveRoutePref(app, KEY_DL_ROUTE, okRoute)
                val dlSec = (System.nanoTime() - dlStart) / 1e9
                val dlSpeed = if (dlSec > 0.1) out.length() / dlSec else 0.0
                Log.i(
                    TAG,
                    "download ok via route ${routeLabel(okRoute)}, " +
                        "${out.length()}B in ${"%.1f".format(dlSec)}s (${fmtSpeed(dlSpeed)})"
                )
                _state.value = _state.value.copy(
                    downloading = false, progress = 1f, found = null,
                    downloadedFile = out.absolutePath,
                    message = "下载完成：${out.name}"
                )
                install(app, out)
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                _state.value = _state.value.copy(
                    downloading = false, progress = -1f,
                    message = "下载失败：${e.javaClass.simpleName} ${e.message}"
                )
            }
        }
    }

    /** 上次成功的线路排最前，其余按默认优先级 */
    private fun orderedCheckRoutes(ctx: Context): List<String> {
        val last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CHECK_ROUTE, null)
        if (last == null || !CHECK_ROUTES.contains(last)) return CHECK_ROUTES
        return listOf(last) + CHECK_ROUTES.filter { it != last }
    }

    /** 下载线路 = json routes + 自动补齐的中转/直链兜底，上次成功线路优先 */
    private fun orderedDownloadRoutes(ctx: Context, info: Info): List<String> {
        val list = info.routes.toMutableList()
        if (info.fileName.isNotBlank() && info.versionName.isNotBlank()) {
            val direct = "$GH_BASE/releases/download/v${info.versionName}/${info.fileName}"
            for (p in DL_PROXIES) {
                val proxied = p + direct
                if (!list.contains(proxied)) list.add(proxied)
            }
            if (!list.contains(direct)) list.add(direct)
        }
        val last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DL_ROUTE, null)
        if (last != null && list.contains(last)) {
            list.remove(last)
            list.add(0, last)
        }
        return list
    }

    private fun saveRoutePref(ctx: Context, key: String, route: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, route).apply()
    }

    /**
     * 线路测速：对所有候选线路并行发起 Range 请求，各读最多 [PROBE_BYTES] 字节，
     * 用「实际收到字节 / 耗时」估算吞吐，按速度降序返回。
     * 探测失败的线路保留在末尾（兜底仍可用，只是排在后面）。
     * 结果通过 State.message 实时反馈给用户，并写日志。
     */
    private suspend fun speedTestRoutes(routes: List<String>): List<String> {
        if (routes.size <= 1) return routes
        val results = coroutineScope {
            routes.map { route ->
                async(Dispatchers.IO) { route to probeRouteSpeed(route) }
            }.awaitAll()
        }
        val ok = results.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
        val failed = results.filter { it.second <= 0.0 }.map { it.first }
        val ordered = ok.map { it.first } + failed
        val desc = ok.joinToString(", ") {
            "${routeLabel(it.first)}=${fmtSpeed(it.second)}"
        }.ifBlank { "无可用线路" }
        Log.i(TAG, "speed test: $desc; failed=${failed.map { routeLabel(it) }}")
        _state.value = _state.value.copy(
            message = "测速完成，最快线路：${if (ok.isNotEmpty()) routeLabel(ok[0].first) + "（" + fmtSpeed(ok[0].second) + "）" else "无（将依次尝试）"}"
        )
        return ordered
    }

    /** 返回该线路的估算速度（字节/秒）；<=0 表示探测失败 */
    private fun probeRouteSpeed(urlStr: String): Double {
        var current = urlStr
        var hops = 0
        val started = System.nanoTime()
        var conn: HttpURLConnection? = null
        try {
            while (true) {
                conn?.disconnect()
                conn = URL(current).openConnection() as HttpURLConnection
                conn.connectTimeout = PROBE_CONNECT_TIMEOUT
                conn.readTimeout = PROBE_READ_TIMEOUT
                conn.instanceFollowRedirects = true
                conn.setRequestProperty(
                    "User-Agent",
                    "wangbuliao-todo-android/${BuildConfig.VERSION_NAME}"
                )
                conn.setRequestProperty("Accept", "application/octet-stream")
                // 只探测开头一小段，足够估算吞吐且不浪费流量
                conn.setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
                val code = conn.responseCode
                if (code in 301..308 && hops < 5) {
                    val loc = conn.getHeaderField("Location") ?: return -1.0
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                    hops++
                    continue
                }
                if (code !in 200..299) return -1.0
                var read = 0L
                val deadline = started + PROBE_DEADLINE_NS
                conn.inputStream.use { ins ->
                    val buf = ByteArray(8192)
                    while (read < PROBE_BYTES) {
                        if (System.nanoTime() > deadline) break // 总时限到：用已读字节估算
                        val n = ins.read(buf, 0, minOf(buf.size, (PROBE_BYTES - read).toInt()))
                        if (n <= 0) break
                        read += n
                    }
                }
                if (read <= 0) return -1.0
                val elapsedSec = (System.nanoTime() - started) / 1e9
                if (elapsedSec <= 0.0) return -1.0
                return read / elapsedSec
            }
        } catch (e: Exception) {
            Log.w(TAG, "probe ${routeLabel(urlStr)} failed: ${e.javaClass.simpleName}")
            return -1.0
        } finally {
            conn?.disconnect()
        }
    }

    private fun fmtSpeed(bytesPerSec: Double): String = when {
        bytesPerSec >= 1_048_576 -> String.format("%.1fMB/s", bytesPerSec / 1_048_576)
        bytesPerSec >= 1024 -> String.format("%.0fKB/s", bytesPerSec / 1024)
        else -> String.format("%.0fB/s", bytesPerSec)
    }

    private fun routeLabel(u: String): String =
        try { URL(u).host } catch (_: Exception) { u }

    /** 附加 cb 时间戳参数，绕过中转代理/CDN 对 wbl-update.json 的旧缓存 */
    private fun withCacheBuster(u: String): String {
        val sep = if (u.contains("?")) "&" else "?"
        return u + sep + "cb=" + System.currentTimeMillis()
    }

    fun install(ctx: Context, file: File) {
        try {
            if (!file.exists() || file.length() <= 0) {
                _state.value = _state.value.copy(message = "安装包不存在，请重新下载")
                return
            }
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                _state.value = _state.value.copy(message = "请先允许「安装未知应用」，返回后点击安装")
                val si = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(si)
                } catch (e: Exception) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            _state.value = _state.value.copy(message = "启动安装失败：${e.message}")
        }
    }

    /** 单次下载尝试（支持断点续传+手动跟随跨主机重定向）；返回 true=下载流正常结束 */
    private fun downloadOnce(urlStr: String, out: File): Boolean {
        val resumeFrom = if (out.exists()) out.length() else 0L
        var current = urlStr
        var conn: HttpURLConnection? = null
        var hops = 0
        var code: Int
        while (true) {
            conn?.disconnect()
            conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 45_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "wangbuliao-todo-android/${BuildConfig.VERSION_NAME}"
            )
            if (current.contains("/releases/assets/")) {
                // GitHub API 资产端点：必须带此 Accept 头才返回二进制（否则返回 JSON 元数据）
                conn.setRequestProperty("Accept", "application/octet-stream")
            }
            if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
            code = conn.responseCode
            // HttpURLConnection 不跟随跨主机重定向，手动处理（中转线路常见多跳）
            if (code in 301..308 && hops < 5) {
                val loc = conn.getHeaderField("Location")
                    ?: throw IOException("重定向缺少 Location")
                current = if (loc.startsWith("http")) loc
                else URL(URL(current), loc).toString()
                hops++
                continue
            }
            break
        }
        val c = conn!!
        try {
            if (code == 416) return true // Range 越界：本地已有完整大小，交由 sha256 校验
            if (code !in 200..299) throw IOException("HTTP $code")
            val appending = code == 206 && resumeFrom > 0
            val start = if (appending) resumeFrom else 0L
            if (!appending && resumeFrom > 0) out.delete() // 服务器未续传→重新下载
            val cl = c.contentLength.toLong()
            val total = if (cl > 0) cl + start else -1L
            c.inputStream.use { ins ->
                FileOutputStream(out, appending).use { os ->
                    val buf = ByteArray(8192)
                    var done = start
                    val t0 = System.nanoTime()
                    var lastUi = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastUi > 500_000_000L) { // 500ms 节流刷新进度+实时速度
                            lastUi = now
                            val el = (now - t0) / 1e9
                            val sp = if (el > 0.3) (done - start) / el else 0.0
                            _state.value = _state.value.copy(
                                progress = if (total > 0) done.toFloat() / total else -1f,
                                message = if (total > 0)
                                    "下载中 ${done * 100 / total}% · ${fmtSpeed(sp)}"
                                else "下载中 ${fmtSpeed(sp)}"
                            )
                        }
                    }
                }
            }
            return true
        } finally {
            c.disconnect()
        }
    }

    private fun sha256Hex(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun httpGetOnce(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        if (url.contains("api.github.com")) {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
        } else {
            conn.setRequestProperty("Accept", "application/json")
        }
        conn.setRequestProperty(
            "User-Agent",
            "wangbuliao-todo-android/${BuildConfig.VERSION_NAME}"
        )
        try {
            var current = url
            var c = conn
            var hops = 0
            while (true) {
                val code = c.responseCode
                if (code in 301..308 && hops < 5) {
                    val loc = c.getHeaderField("Location")
                        ?: throw IOException("重定向缺少 Location")
                    c.disconnect()
                    current = if (loc.startsWith("http")) loc
                    else URL(URL(current), loc).toString()
                    c = URL(current).openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 15_000
                    c.setRequestProperty(
                        "User-Agent",
                        "wangbuliao-todo-android/${BuildConfig.VERSION_NAME}"
                    )
                    hops++
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return c.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }
}
