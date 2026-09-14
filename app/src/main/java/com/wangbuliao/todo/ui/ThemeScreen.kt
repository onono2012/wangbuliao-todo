package com.wangbuliao.todo.ui

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangbuliao.todo.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主题页（底部导航独立入口）：
 * 十套内置主题 + 自定义照片主题的选择与管理。
 * 切换即时全局生效（列表/日历/设置/编辑/悬浮球/通知跟随）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val themeId by Prefs.theme.collectAsState()
    val customPhoto by Prefs.customPhoto.collectAsState()

    // 自定义照片主题：选图 → 裁剪存库 → 提取主色 → 自动启用
    var importingPhoto by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importingPhoto = true
        importMsg = null
        CoroutineScope(Dispatchers.Main).launch {
            val r = withContext(Dispatchers.IO) { CustomThemeStore.import(ctx, uri) }
            importingPhoto = false
            if (r != null) {
                Prefs.setCustomPhoto(r.first, r.second)
                Prefs.setTheme(CUSTOM_THEME_ID)
                importMsg = "已启用「我的照片」主题"
            } else {
                importMsg = "照片导入失败，换一张试试"
            }
        }
    }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                val themed = wblTopBarThemed()
                val tbc = wblTopBarContentColor()
                TopAppBar(
                    title = { Text("主题外观", color = tbc) },
                    navigationIcon = {
                        IconButton(onClick = { vm.backList() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = tbc)
                        }
                    },
                    colors = if (themed) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = tbc,
                            navigationIconContentColor = tbc
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    modifier = wblTopBarModifier()
                )
            }
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 主题列表 ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Palette, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "选一套主题",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = wblCardColor()),
                    border = wblCardBorder()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "十套精心配色 + 自定义照片主题，全屏背景，浅色/深色自动适配；切换即时全局生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        allThemes().forEach { spec ->
                            val selected = themeId == spec.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { Prefs.setTheme(spec.id) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 预览：drawable 照片 / 自定义照片文件 / 渐变色点
                                val photoBmp = remember(spec.photoPath) {
                                    spec.photoPath?.let { CustomThemeStore.squareThumb(it, 96) }
                                }
                                Box(
                                    Modifier.size(34.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (spec.previewRes == null && photoBmp == null) {
                                                Modifier.background(
                                                    if (spec.preview.size >= 2) {
                                                        Brush.linearGradient(spec.preview)
                                                    } else {
                                                        Brush.linearGradient(
                                                            listOf(spec.preview[0], spec.preview[0])
                                                        )
                                                    }
                                                )
                                            } else Modifier
                                        )
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (spec.previewRes != null) {
                                        Image(
                                            painterResource(spec.previewRes), null,
                                            Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    photoBmp?.let { bmp ->
                                        Image(
                                            bmp.asImageBitmap(), null,
                                            Modifier.matchParentSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check, null,
                                            Modifier.size(18.dp), tint = Color.White
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        spec.name, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        spec.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Text(
                                        "使用中", style = MaterialTheme.typography.labelSmall,
                                        color = wblAccentColor()
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 自定义照片主题：上传/更换/移除 ──
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = wblCardColor()),
                    border = wblCardBorder()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        WblOutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            enabled = !importingPhoto,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (importingPhoto) {
                                LinearProgressIndicator(Modifier.width(60.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("导入裁剪中…")
                            } else {
                                Text(
                                    if (customPhoto.isNullOrEmpty()) "📤 上传照片，做专属全屏主题"
                                    else "📤 更换自定义照片"
                                )
                            }
                        }
                        importMsg?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = wblAccentColor()
                            )
                        }
                        if (!customPhoto.isNullOrEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "照片全屏显示，悬浮球也会变成你的照片；主色自动从照片提取",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                WblTextButton(onClick = {
                                    val f = File(customPhoto!!)
                                    if (f.exists()) f.delete()
                                    Prefs.setCustomPhoto(null)
                                    if (themeId == CUSTOM_THEME_ID) Prefs.setTheme("rainbow")
                                }) {
                                    Text("移除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
}
