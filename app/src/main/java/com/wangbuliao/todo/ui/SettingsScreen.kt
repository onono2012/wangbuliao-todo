package com.wangbuliao.todo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wangbuliao.todo.BuildConfig
import com.wangbuliao.todo.floatwin.FloatingNoteService
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.reminder.Notif
import com.wangbuliao.todo.reminder.PinNotifService
import com.wangbuliao.todo.reminder.KeepAliveService
import com.wangbuliao.todo.update.Updater
import com.wangbuliao.todo.util.BackupManager
import com.wangbuliao.todo.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val up by Updater.state.collectAsState()

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // 悬浮窗开关：需系统「显示在其他应用上层」授权，返回后核对真实状态
    val floatEnabled by Prefs.floatEnabled.collectAsState()
    val pinEnabled by Prefs.pinNotif.collectAsState()
    var ringName by remember { mutableStateOf(ringDisplayName(ctx)) }

    val overlaySettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (FloatingNoteService.canDraw(ctx)) {
            if (!Prefs.floatEnabled.value) Prefs.setFloatEnabled(true)
            FloatingNoteService.start(ctx)
        } else {
            Prefs.setFloatEnabled(false)
            FloatingNoteService.stop(ctx)
        }
    }

    // 铃声选择：系统铃声选择器，返回 uri 后重建通知渠道
    val ringPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // 持久化 URI 读权限（重启后仍可用）
            if (uri != null) {
                try {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }
            Prefs.setRingUri(uri?.toString())
            Notif.ensureChannels(ctx)
            ringName = ringDisplayName(ctx)
        }
    }

    // 电池优化白名单：跳系统页，返回后刷新状态
    var optRefresh by remember { mutableStateOf(0) }
    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { optRefresh++ }

    // 本地备份：SAF 选择导出文件 / 选择备份包；选择恢复文件后先弹确认框
    val localBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> if (uri != null) vm.doLocalBackup(uri) }
    var pendingLocalRestore by remember { mutableStateOf<Uri?>(null) }
    val localRestorePick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            pendingLocalRestore = uri
        }
    }

    WblScreenBackground {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            val themed = wblTopBarThemed()
            val tbc = wblTopBarContentColor()
            val tint = tbc
            TopAppBar(
                title = { Text("设置", color = tint) },
                navigationIcon = {
                    IconButton(onClick = { vm.backList() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = tint)
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
            // ── 随手记悬浮窗 ──
            SectionTitle("随手记悬浮窗", Icons.Outlined.PictureInPictureAlt)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("悬浮球快速记录")
                        Text(
                            if (floatEnabled) "运行中：任意界面点悬浮球即可记一笔"
                            else "开启后可在任意界面极速记录（需授予悬浮窗权限）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = floatEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                if (FloatingNoteService.canDraw(ctx)) {
                                    Prefs.setFloatEnabled(true)
                                    FloatingNoteService.start(ctx)
                                } else {
                                    try {
                                        overlaySettings.launch(
                                            FloatingNoteService.overlaySettingsIntent(ctx)
                                        )
                                    } catch (e: Exception) {
                                        overlaySettings.launch(
                                            Intent(Settings.ACTION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            } else {
                                Prefs.setFloatEnabled(false)
                                FloatingNoteService.stop(ctx)
                            }
                        }
                    )
                }
            }

            // ── 置顶通知 ──
            SectionTitle("置顶通知", Icons.Outlined.PushPin)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("通知栏待办置顶栏")
                        Text(
                            if (pinEnabled) "已开启：待办速览常驻通知栏最上方"
                            else "开启后未完成数量与下一个提醒常驻通知栏顶部",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { on ->
                            Prefs.setPinNotif(on)
                            if (on) PinNotifService.start(ctx) else PinNotifService.stop(ctx)
                        }
                    )
                }
            }

            // ── 后台保活 ──
            SectionTitle("后台保活", Icons.Outlined.Shield)
            val keepAlive by Prefs.keepAlive.collectAsState()
            val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE)
                as android.os.PowerManager
            val ignoring = remember(optRefresh) {
                pm.isIgnoringBatteryOptimizations(ctx.packageName)
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("保活守护服务")
                            Text(
                                if (keepAlive) "运行中：前台服务常驻，最大限度防止被系统杀后台"
                                else "开启后应用常驻后台，提醒不漏发",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "状态通知会显示未完成事项清单，点「打开管理器」直达本页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepAlive,
                            onCheckedChange = { on ->
                                Prefs.setKeepAlive(on)
                                if (on) KeepAliveService.start(ctx) else KeepAliveService.stop(ctx)
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("电池优化白名单")
                            Text(
                                if (ignoring) "✓ 已忽略电池优化（保活效果最佳）"
                                else "未加白名单：系统省电时可能杀掉后台",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ignoring) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!ignoring) {
                            OutlinedButton(onClick = {
                                try {
                                    batteryOptLauncher.launch(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${ctx.packageName}")
                                        )
                                    )
                                } catch (e: Exception) {
                                    try {
                                        batteryOptLauncher.launch(
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }) { Text("去允许") }
                        }
                    }
                    Text(
                        "小贴士：realme/OPPO 请在系统「设置 → 电池 → 应用耗电管理」中允许本应用后台运行与自启动，保活更稳",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 提醒铃声 ──
            SectionTitle("提醒铃声", Icons.Outlined.MusicNote)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("当前铃声")
                        Text(
                            ringName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "提醒到期：横幅弹出 + 铃声 + 震动；通知栏可直接「完成 / 10分钟后再提醒」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提醒铃声")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            Prefs.ringUri.value?.let {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(it)
                                )
                            }
                        }
                        ringPicker.launch(i)
                    }) { Text("更换") }
                }
                // 震动总开关：通知震动 + 应用内完成待办触感
                val vibOn by Prefs.vibrate.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("震动提醒")
                        Text(
                            if (vibOn) "到期通知震动（长-短-长）+ 完成待办轻震反馈"
                            else "已关闭全部震动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vibOn,
                        onCheckedChange = { on ->
                            Prefs.setVibrate(on)
                            Notif.ensureChannels(ctx)  // 渠道 id 含震动标志，重建生效
                        }
                    )
                }
            }

            // ── 云端备份 ──
            SectionTitle("云端备份", Icons.Outlined.Cloud)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
                    val bk by vm.backup.collectAsState()
                    var showDavCfg by remember { mutableStateOf(false) }
                    var showCloudRestoreConfirm by remember { mutableStateOf(false) }

                    // 标题行 + 小配置按钮：点击弹出 WebDAV 配置
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("WebDAV 云端备份")
                            Text(
                                "备份内容：任务数据 + 自定义照片主题；通道：自备 WebDAV（坚果云/群晖/自建等），账号密码仅存本机",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDavCfg = true }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "WebDAV 配置",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "恢复前会自动把当前数据本地兜底备份（files/pre_restore/）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (Prefs.davConfigured()) vm.doBackup() else showDavCfg = true
                        }, enabled = !bk.busy) {
                            Text(if (bk.busy) "处理中…" else "备份到云端")
                        }
                        OutlinedButton(onClick = {
                            if (Prefs.davConfigured()) showCloudRestoreConfirm = true else showDavCfg = true
                        }, enabled = !bk.busy) {
                            Text("从云端恢复")
                        }
                    }
                    if (showDavCfg) {
                        DavConfigDialog { showDavCfg = false }
                    }
                    if (showCloudRestoreConfirm) {
                        AlertDialog(
                            onDismissRequest = { showCloudRestoreConfirm = false },
                            title = { Text("从云端恢复") },
                            text = { Text("将用云端备份覆盖当前全部任务数据与照片主题，完成后应用自动重启。\n\n当前数据会先在本地兜底备份（files/pre_restore/）。确定继续？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showCloudRestoreConfirm = false
                                    vm.doRestore()
                                }) { Text("恢复") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCloudRestoreConfirm = false }) { Text("取消") }
                            }
                        )
                    }
                    bk.message?.let { msg ->
                        if (bk.target == "cloud") {
                            Spacer(Modifier.height(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── 本地备份 ──
            SectionTitle("本地备份", Icons.Outlined.Folder)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
                    val bk by vm.backup.collectAsState()
                    Text(
                        "导出备份 zip 到本机存储（文件管理器可见），或从备份文件恢复；无需网络与账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "恢复前会自动把当前数据本地兜底备份（files/pre_restore/）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { localBackup.launch(BackupManager.localBackupName()) },
                            enabled = !bk.busy) {
                            Text(if (bk.busy) "处理中…" else "备份到本机")
                        }
                        OutlinedButton(onClick = { localRestorePick.launch(arrayOf("application/zip")) },
                            enabled = !bk.busy) {
                            Text("从本机恢复")
                        }
                    }
                    pendingLocalRestore?.let { uri ->
                        AlertDialog(
                            onDismissRequest = { pendingLocalRestore = null },
                            title = { Text("从本机恢复") },
                            text = { Text("将用所选备份文件覆盖当前全部任务数据与照片主题，完成后应用自动重启。\n\n当前数据会先在本地兜底备份（files/pre_restore/）。确定继续？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingLocalRestore = null
                                    vm.doLocalRestore(uri)
                                }) { Text("恢复") }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingLocalRestore = null }) { Text("取消") }
                            }
                        )
                    }
                    bk.message?.let { msg ->
                        if (bk.target == "local") {
                            Spacer(Modifier.height(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }



            // ── 在线更新 ──
            SectionTitle("在线更新", Icons.Outlined.CloudDownload)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
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
            SectionTitle("提醒权限", Icons.Outlined.VerifiedUser)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
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
            SectionTitle("关于", Icons.Outlined.Info)
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = wblCardColor())
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("忘不了 · 待办记事本")
                    Text(
                        "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "待办/已办 · 紧急标注 · 定时提醒 · 语音记事 · 拍照/图片 · 随手记悬浮窗 · 主题 · 在线更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }
}

/**
 * WebDAV 云端配置弹窗：由云端备份卡片右上角 ⚙ 按钮唤起。
 */
@Composable
private fun DavConfigDialog(onDismiss: () -> Unit) {
    var davUrl by remember { mutableStateOf(Prefs.davUrl.value) }
    var davUser by remember { mutableStateOf(Prefs.davUser.value) }
    var davPass by remember { mutableStateOf(Prefs.davPass()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 云端配置") },
        text = {
            Column {
                Text(
                    "自备任意标准 WebDAV 服务（坚果云/群晖/123 网盘等），账号密码仅保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = davUrl, onValueChange = { davUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = davUser, onValueChange = { davUser = it },
                    label = { Text("账号") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = davPass, onValueChange = { davPass = it },
                    label = { Text("密码 / 应用密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Prefs.setDavConfig(davUrl, davUser, davPass)
                    onDismiss()
                },
                enabled = davUrl.isNotBlank() && davUser.isNotBlank() && davPass.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** 当前铃声显示名（null=系统默认通知音，空=静音）；非 Composable，读 Prefs.value */
private fun ringDisplayName(ctx: android.content.Context): String {
    val uriStr = Prefs.ringUri.value
    return try {
        if (uriStr == null) {
            "系统默认通知音"
        } else if (uriStr.isEmpty()) {
            "静音"
        } else {
            RingtoneManager.getRingtone(ctx, Uri.parse(uriStr))?.getTitle(ctx) ?: "自定义铃声"
        }
    } catch (e: Exception) {
        "自定义铃声"
    }
}
