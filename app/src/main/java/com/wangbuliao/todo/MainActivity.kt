@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wangbuliao.todo

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.floatwin.FloatingNoteService
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.reminder.Notif
import com.wangbuliao.todo.reminder.PinNotifService
import com.wangbuliao.todo.reminder.KeepAliveService
import com.wangbuliao.todo.util.Prefs
import com.wangbuliao.todo.ui.CalendarScreen
import com.wangbuliao.todo.ui.EditTaskScreen
import com.wangbuliao.todo.ui.MainViewModel
import com.wangbuliao.todo.ui.Screen
import com.wangbuliao.todo.ui.SettingsScreen
import com.wangbuliao.todo.ui.TaskListScreen
import com.wangbuliao.todo.ui.WblTheme
import com.wangbuliao.todo.ui.wblCardColor
import com.wangbuliao.todo.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** onNewIntent 深链信号：保活通知「打开管理器」 */
    private var goSettingsSignal by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(KeepAliveService.EXTRA_OPEN_SETTINGS, false)) {
            goSettingsSignal = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Notif.ensureChannels(this)

        // 首次启动请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 冷启动恢复提醒闹钟（处理开机后未收到广播、或覆盖安装场景）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(ctx)
            } catch (_: Exception) {
            }
        }
        // 恢复常驻服务：悬浮窗 + 置顶通知（覆盖安装后 BootReceiver 也会触发，这里兜底）
        syncServices(ctx)

        setContent {
            WblTheme {
                val vm: MainViewModel = viewModel()
                val ui by vm.ui.collectAsState()
                val screen by vm.screen.collectAsState()
                val draft by vm.draft.collectAsState()
                val msg by vm.msg.collectAsState()
                val snackbar = remember { SnackbarHostState() }

                // 保活通知「打开管理器」深链 → 直达设置页（冷启动 + onNewIntent 热启动）
                LaunchedEffect(Unit) {
                    if (intent?.getBooleanExtra(KeepAliveService.EXTRA_OPEN_SETTINGS, false) == true) {
                        vm.goScreen(Screen.Settings)
                    }
                }
                LaunchedEffect(goSettingsSignal) {
                    if (goSettingsSignal) {
                        vm.goScreen(Screen.Settings)
                        goSettingsSignal = false
                    }
                }

                // 启动即静检查 GitHub 更新（有新版本→强制更新对话框）
                LaunchedEffect(Unit) {
                    vm.refresh()
                    vm.checkUpdate(silent = true)
                }
                LaunchedEffect(msg) {
                    msg?.let {
                        snackbar.showSnackbar(it)
                        vm.consumeMsg()
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        // 编辑页全屏（无底部导航，专注输入）
                        Screen.Edit -> EditTaskScreen(vm, draft, ui)
                        // 其余页面：底部导航（待办 / 日历 / 设置）
                        else -> {
                            BackHandler(screen != Screen.List) { vm.goScreen(Screen.List) }
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = wblCardColor(),
                                        tonalElevation = 0.dp,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ) {
                                        NavItem(screen, Screen.List, Icons.Outlined.Checklist, "待办") {
                                            vm.goScreen(Screen.List)
                                        }
                                        NavItem(screen, Screen.Calendar, Icons.Outlined.CalendarMonth, "日历") {
                                            vm.goScreen(Screen.Calendar)
                                        }
                                        NavItem(screen, Screen.Settings, Icons.Outlined.Settings, "设置") {
                                            vm.goScreen(Screen.Settings)
                                        }
                                    }
                                }
                            ) { pad ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(pad)
                                        .consumeWindowInsets(pad)
                                ) {
                                    when (screen) {
                                        Screen.List -> TaskListScreen(vm, ui)
                                        Screen.Calendar -> CalendarScreen(vm, ui)
                                        Screen.Settings -> SettingsScreen(vm)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                    UpdateDialog()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置（通知/精确闹钟/悬浮窗授权页）返回时刷新提醒安排与服务状态
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(applicationContext)
            } catch (_: Exception) {
            }
        }
        syncServices(applicationContext)
    }

    private fun syncServices(ctx: android.content.Context) {
        try {
            if (Prefs.floatEnabled.value) {
                if (FloatingNoteService.canDraw(ctx)) FloatingNoteService.start(ctx)
                else {
                    // 用户撤销了悬浮窗授权 → 关闭开关并停服务
                    Prefs.setFloatEnabled(false)
                    FloatingNoteService.stop(ctx)
                }
            } else {
                FloatingNoteService.stop(ctx)
            }
            if (Prefs.pinNotif.value) PinNotifService.start(ctx)
            else PinNotifService.stop(ctx)
            if (Prefs.keepAlive.value) KeepAliveService.start(ctx)
            else KeepAliveService.stop(ctx)
        } catch (e: Exception) {
            android.util.Log.e("WblMain", "syncServices failed", e)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    current: Screen,
    target: Screen,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val selected = current == target
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon, contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        )
    )
}

/**
 * 全局强制更新对话框：发现新版本时覆盖所有页面弹出，
 * 同步展示更新内容（Release body），force=true 不可取消；
 * 下载中显示进度，完成后自动调起系统安装器。
 */
@Composable
private fun UpdateDialog() {
    val ctx = LocalContext.current
    val up by Updater.state.collectAsState()
    val info = up.found ?: return

    AlertDialog(
        onDismissRequest = { if (!info.force) Updater.dismissFound() },
        title = { Text("发现新版本 v${info.versionName}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (info.desc.isNotBlank()) {
                    Text("更新内容", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(info.desc, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.force) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "本版本为强制更新，完成后才能继续使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (up.downloading) {
                    Spacer(Modifier.height(12.dp))
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
                } else if (up.message != null && !up.downloading && up.downloadedFile == null) {
                    // 下载失败等原因时把错误信息展示在对话框内
                    up.message?.let {
                        if (it.startsWith("下载失败") || it.startsWith("启动安装失败") ||
                            it.startsWith("请先允许")) {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { Updater.download(ctx, info) },
                enabled = !up.downloading
            ) { Text(if (up.downloading) "下载中…" else "立即更新") }
        },
        dismissButton = {
            if (!info.force) {
                TextButton(onClick = { Updater.dismissFound() }) { Text("以后再说") }
            }
        }
    )
}
