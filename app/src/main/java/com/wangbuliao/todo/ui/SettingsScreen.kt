package com.wangbuliao.todo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wangbuliao.todo.BuildConfig
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.update.Updater
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val up by Updater.state.collectAsState()

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { vm.backList() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 在线更新 ──
            Text("在线更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "更新来源：GitHub · ${Updater.REPO_OWNER}/${Updater.REPO_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "多线路中转（raw/gh-proxy/ghproxy/jsDelivr），失败自动切换；123 网盘同步备份分发",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "启动时自动检查；有新版本将强制更新并展示更新内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.checkUpdate() },
                            enabled = !up.checking && !up.downloading
                        ) { Text(if (up.checking) "检查中…" else "检查更新") }
                        val f = up.downloadedFile
                        if (f != null) {
                            OutlinedButton(onClick = {
                                Updater.install(ctx, File(f))
                            }) { Text("安装已下载版本") }
                        }
                    }
                    if (up.downloading) {
                        Spacer(Modifier.height(8.dp))
                        if (up.progress in 0f..1f) {
                            LinearProgressIndicator(
                                progress = up.progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                up.message ?: "下载中 ${(up.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    up.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── 提醒相关权限 ──
            Text("提醒权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    // 通知权限
                    val notifGranted = Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("通知权限")
                            Text(
                                if (notifGranted) "已授予" else "未授予，提醒将无法弹出",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (notifGranted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!notifGranted) {
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }) { Text("去授予") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 精确闹钟
                    val exact = AlarmScheduler.canExact(ctx)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("精确闹钟")
                            Text(
                                if (exact) "已允许（提醒准点触发）"
                                else "未允许（提醒可能延迟几分钟）",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (exact) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            OutlinedButton(onClick = {
                                try {
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${ctx.packageName}")
                                        )
                                    )
                                } catch (e: Exception) {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Text("去开启") }
                        }
                    }
                }
            }

            // ── 关于 ──
            Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("忘不了 · 待办记事本")
                    Text(
                        "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "待办/已办 · 紧急标注 · 定时提醒 · 自定义分类 · 在线更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
