@file:OptIn(ExperimentalMaterial3Api::class)

package com.wangbuliao.todo.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

/**
 * 「发现」在线主题页：
 * 多线路拉取主题列表 → 封面预览 → 一键下载（静态壁纸/动态压缩包）
 * → 落盘 filesDir/themes/<id>/ → 成功后自动应用（onThemeSelected("online:<id>")）。
 */
@Composable
fun ThemeGridScreen(
    onThemeSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { ThemeDownloader(context) }
    val scope = rememberCoroutineScope()

    var themes by remember { mutableStateOf<List<OnlineTheme>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var installedTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        errorMsg = null
        downloader.fetchThemeList().fold(
            onSuccess = { list ->
                themes = list
                loading = false
            },
            onFailure = { e ->
                errorMsg = e.message ?: "网络错误"
                loading = false
            }
        )
    }

    // 已安装主题集合（依赖 installedTick 在下载完成后刷新）
    val installedIds = remember(themes, installedTick) {
        themes.filter { onlineThemeSpec(it.id) != null }.map { it.id }.toSet()
    }

    fun startDownload(theme: OnlineTheme) {
        if (downloadingId != null) return
        scope.launch {
            downloadingId = theme.id
            progress = 0f
            downloader.downloadTheme(theme) { p ->
                if (p >= 0f) progress = p
            }.fold(
                onSuccess = {
                    downloadingId = null
                    installedTick++
                    Toast.makeText(context, "已应用：${theme.name}", Toast.LENGTH_SHORT).show()
                    onThemeSelected(ONLINE_THEME_PREFIX + theme.id)
                },
                onFailure = { e ->
                    downloadingId = null
                    Toast.makeText(
                        context,
                        "下载失败：${e.message ?: "请检查网络"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    Scaffold(
        // 关键：透明容器——默认不透明 background 会盖住全局 WblScreenBackground 的动态主题 WebView 动画层
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent), modifier = wblTopBarModifier(),
                title = { Text("发现更多主题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                loading && themes.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在获取主题列表…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                themes.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = errorMsg?.let { "加载失败：$it" } ?: "暂无在线主题",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(20.dp))
                        WblButton(onClick = { refreshTick++ }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("重试")
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 158.dp),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(themes.size) { index ->
                            val theme = themes[index]
                            val installed = installedIds.contains(theme.id)
                            val isDownloading = downloadingId == theme.id
                            OnlineThemeCard(
                                theme = theme,
                                installed = installed,
                                isDownloading = isDownloading,
                                progress = if (isDownloading) progress else 0f,
                                onClick = {
                                    when {
                                        installed -> {
                                            Toast.makeText(
                                                context,
                                                "已应用：${theme.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onThemeSelected(ONLINE_THEME_PREFIX + theme.id)
                                        }

                                        isDownloading || downloadingId != null -> Unit
                                        else -> startDownload(theme)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 在线主题卡片：封面 + 类型角标 + 状态按钮（下载/进度/已下载）+ 名称描述。
 * 封面：多线路下载 → 本地缓存（filesDir/themeCovers）→ Coil 读本地文件（秒开）；
 * 下载中/失败显示主题色占位渐变，不再全黑。
 */
@Composable
private fun OnlineThemeCard(
    theme: OnlineTheme,
    installed: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { ThemeDownloader(context) }
    var coverFile by remember(theme.id, theme.coverUrl) { mutableStateOf<java.io.File?>(null) }

    // 异步拉取封面（命中缓存立即返回；否则多线路下载后落盘）
    LaunchedEffect(theme.id, theme.coverUrl) {
        if (theme.coverUrl.isNotEmpty()) {
            coverFile = downloader.loadCoverFile(theme)
        }
    }

    // 占位渐变：按主题名哈希取两个稳定色相，保证同主题每次一致
    val placeholder = remember(theme.id) {
        val h = (theme.id.hashCode() and 0x7fffffff) % 360
        val c1 = Color(
            android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 0.55f, 0.42f))
        )
        val c2 = Color(
            android.graphics.Color.HSVToColor(floatArrayOf(((h + 55) % 360).toFloat(), 0.62f, 0.28f))
        )
        listOf(c1, c2)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            // 底层占位渐变（封面未就绪 / 下载失败时可见，避免全黑）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(placeholder)),
                contentAlignment = Alignment.Center
            ) {
                if (coverFile == null) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            // 封面图（本地文件，秒开）
            val cf = coverFile
            if (cf != null) {
                Image(
                    painter = rememberAsyncImagePainter(cf),
                    contentDescription = theme.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // 类型角标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (theme.type == "dynamic") "动态" else "静态",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 右下状态按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                when {
                    installed -> Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已下载",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    isDownloading -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )

                    else -> Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "下载",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 下载进度条（贴底）
            if (isDownloading) {
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = theme.desc.ifEmpty { theme.resolution },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
