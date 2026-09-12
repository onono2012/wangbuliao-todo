# 更新日志

## v1.4.0 (10400) — 全屏主题 · 照片定制 · 日历农历 · 底部导航大改版

- 🧭 排版大改版：底部导航栏（待办 / 日历 / 设置），页面切换更顺手；编辑页保持全屏专注输入
- 🎨 主题扩容至十套：炫彩（默认）/ 肖战 / 极光 / 暮山紫 / 鎏金 / 深海蓝 / 清新绿 / 暖阳橙 / 樱花粉 / 极夜黑，全部全屏渐变洗色 + 光斑氛围背景
- 📤 自定义照片主题：相册选图 → 自动按屏幕比例裁剪 → 全屏背景 + 悬浮球圆贴 + 照片主色自动提取，一键做专属主题（可更换/移除）
- 🗓️ 全新日历页：公历 + 农历 + 24 节气（天文级精度 1900–2100）+ 除夕/春节/干支生肖；有事项的日期带圆点标记，点任意日期查看当天事项、一键为当天记一笔
- 🔔 待办列表顶栏新增今日农历/节气副标题
- 🫧 悬浮窗瘦身：悬浮球 60dp→46dp 更小巧不挡视线；主题切换悬浮球即时跟随变色/换图
- ✍ 随手记面板主题化：深色玻璃拟态 + 主题主色按钮；新增 📷拍照 / 🖼相册 / 🎤录音 直达按钮，媒体速记自动入「随手记」（录音 60s 上限、计时显示）
- 📳 震动体系：新增「震动提醒」总开关（设置-提醒铃声区）；到期通知长-短-长震动、完成待办轻震反馈、悬浮窗保存触感，全部受开关控制
- ✅ 待办转已办优化：完成时震动 + 「移入已办」明确去向提示；已办自动沉底按完成时间排序
- 🛡️ 后台保活：设置新增「保活守护服务」开关（前台服务常驻+开机自恢复+START_STICKY），一键申请电池优化白名单，realme/OPPO 自启动指引；最大限度防止杀后台漏提醒
- 📋 保活状态通知：通知栏常驻显示未完成事项清单（紧急/最近提醒优先，最多 5 条+汇总），点通知或「打开管理器」动作直达应用管理页

## v1.3.1 (10301) — UI 全面优化

- 🎨 主题全局生效：主题色贯穿主界面背景（wash 渐变/照片全屏）、卡片半透明、搜索框、顶栏，不再只有顶栏变色
- 📸 肖战主题升级：高清帅照全屏背景（暗色可读性遮罩）+ 圆形头像悬浮球 + 设置页照片预览
- 🏷️ 图标系统：状态筛选（待办/已完成/全部/仅紧急）、分类（工作/生活/学习/随手记/其他）、设置 7 大分区、编辑页 5 分区标题全部配图
- ✨ 排版优化：空状态大图标、随手记对话框图标标题、保存按钮升级为实心主色按钮＋✓、新分类按钮图标化

## v1.3.0 (versionCode=10300, 2026-09-12)

### 主题系统 + 多媒体记事 + 悬浮窗快记
- **主题系统**：内置 6 套主题（默认「炫彩」渐变，含「肖战」主题等），设置页即改即生效并持久保存
- **录音记事**：编辑页一键录音、实时波形显示、内置播放器回放；删除录音时同步清理音频文件
- **拍照/图片记事**：相机拍照或相册选图，多图附件，列表缩略图预览
- **随手记**：列表页与悬浮窗双入口快速记录，自动归入「随手记」分类
- **悬浮窗快记**：任意界面一键弹出快速记事（需悬浮窗权限）
- **提醒增强**：通知常驻置顶（PinNotifService）、自定义提醒铃声（系统铃声选择器）

### 防误删与存储优化
- 编辑页有未保存修改时返回弹确认框（继续编辑 / 放弃修改）
- 保存/放弃时差量清理被移除图片与录音的磁盘文件，相机取消也清残留，不再泄漏存储

### 发布记录
- release APK 9,693,473B，sha256 934d382e94c54c3b8ed729d35484ab6eaa8055b62a3f09980ed715d4fc3defd6
- GitHub Release v1.3.0 + wbl-update.json（检查线路 5/5 通过）+ 123 网盘 WebDAV 备份 + 官网 wbl-latest.apk 已更新
- 真机冒烟通过（realme RMX3888, Android 16）：首页全元素渲染、logcat 无 FATAL

## v1.2.3 (versionCode=10203, 2026-09-12)

### 智能线路测速 + 实时速度显示
- **下载前并行测速**：对所有下载线路同时探测（单条限时 5 秒、采样读取估算速度），按实测速度排序选最快线路下载，探测失败的线路自动排后，大幅提升弱网/被墙环境下的更新速度
- **下载实时速度显示**：进度条同步显示当前速度（如「下载中 45% · 950KB/s」）
- **防缓存检查**：检查更新请求附带 cb 时间戳参数，尽力绕过中转/CDN 旧缓存，第一时间发现新版本
- 说明：v1.2.1 / v1.2.2 为内测版本（测速功能迭代），未对外发布，已由 v1.2.3 取代

### 真机 E2E 验证（realme RMX3888, Android 16）
- v1.2.0 → v1.2.3 强制更新全链路：测速选线 → 9.6MB 约 25 秒下载完成 → sha256 校验通过 → 系统安装器升级成功
- 测速功能复验（v1.2.4 测试版，同 APK 内容）：并行测速 UI「正在测速 5 条线路…」✓、被 DNS 毒化线路 probe 失败（ConnectException）自动排除 ✓、最快线路下载 + 安装成功 ✓；测试版发布物已全部删除、分发清单回滚

## v1.2.0 (versionCode=10200, 2026-09-12)

### 多线路更新通道（抗墙）
- 检查更新扩展为 5 条线路：`raw.githubusercontent.com` → `gh-proxy.com` → `ghproxy.net` → `jsDelivr` → `api.github.com/releases/latest`，单线路失败自动切换；记住上次成功线路，下次优先尝试
- 新增分发清单 `wbl-update.json`（仓库根目录）：含 versionCode / sha256 / size / fileName / routes（下载线路表），更新器按清单下载、失败自动换线
- 下载断点续传**跨线路共用**（切换线路不清进度），sha256 + 文件大小双重校验，失败自动删包重试
- **123 网盘同步备份分发**：每个版本同步上传 APK / wbl-update.json / SHA256SUMS.txt 至 123 网盘 WebDAV（`/app/`），GitHub 渠道整体不可用时的备份获取途径
- 发布工具 `tools/publish.py`：一条命令完成 GitHub Release 创建 + 资产上传 + wbl-update.json 提交 + jsDelivr purge + 123 网盘同步 + 全线路连通性验证；支持 `--commit-json`（清单回滚）与 `--delete-tag`（测试版清理）

### 容灾 E2E 验证
- 真机 iptables REJECT 封锁 GitHub 网段（140.82.112.0/20、20.205.243.0/24、185.199.108.0/22、IPv6 Pages 段）+ DNS 污染模拟断墙：检查更新经中转线路成功，APK 经 ghproxy.net 兜底线路下载完成并安装
- 发布回滚演练：测试 Release/tag 删除、wbl-update.json 回滚、网盘测试文件清理，全流程可逆

## v1.1.0 (versionCode=10100, 2026-09-12)

### 在线更新改造：GitHub Releases 渠道
- 新增 Updater（`update/Updater.kt`）：App 启动自动检查 `api.github.com/repos/onono2012/wangbuliao-todo/releases/latest`，设置页保留手动「检查更新」
- **强制更新**：有新版本时对话框不可取消（无「以后再说」），并同步展示更新内容（= Release 正文 Markdown）
- 移除设置页「更新服务器地址」配置项，改为展示更新来源（GitHub · onono2012/wangbuliao-todo）；旧 `update_url` 偏好废弃
- 下载可靠性：Range 断点续传、最多 6 次自动重试、sha256 完整性校验（Release 资产 digest）、文件大小校验（失败自动删包提示重试）
- 网络适配（实测本设备 github.com:443 被墙）：检查与下载全部走 api.github.com 资产端点（`Accept: application/octet-stream`），手动跟随跨主机 302 重定向（HttpURLConnection 不自动跟随）至 release-assets.githubusercontent.com
- 发布：GitHub Releases v1.1.0（资产 `wbl-v1.1.0-vc10100.apk`，sha256 `0d67ef13…`，同 keystore 签名）

### 真机 E2E 验证（realme RMX3888, Android 16）
- v1.1.0 → GitHub 真实测试 Release v1.1.1(10101)：启动自动弹强制更新框（含标记更新内容、当前版本、仅「立即更新」）→ 下载进度 14%→98% → sha256 校验通过 → 调起系统安装器升级成功（10100→10101，数据保留）→ 同版本启动无框 → 手动检查提示「当前已是最新版本 v1.1.1」——全链路通过
- 验证后：删除测试 Release+tag（latest 回 v1.1.0），`pm install -r -d` 回滚设备至官方 v1.1.0，测试文件全部清理

## v1.0.0 (2026-09-12)

首个版本「忘不了」待办记事本。

### 功能
- **待办管理**：添加 / 编辑 / 删除任务，勾选完成（待办 ↔ 已办），SQLite 本地持久化
- **紧急标注**：任务可标记紧急，列表中紧急事项置顶并高亮显示，支持「仅紧急」筛选
- **分类**：预置 工作 / 生活 / 学习 / 其他，支持用户自定义新增分类（数据库级联删除任务）
- **提醒**：任务可设提醒时间（日期 + 时:分）；AlarmManager 精确闹钟（USE_EXACT_ALARM）+ 通知提醒；开机（BootReceiver）与应用启动（rescheduleAll）自动恢复未触发的提醒，过期未提醒的立即补发通知
- **筛选**：待办 / 已办 / 全部 × 分类 chips 组合筛选
- **主题**：浅色 / 深色 / 跟随系统；设置页提供测试通知入口
- **权限**：Android 13+ 运行时申请 POST_NOTIFICATIONS，拒绝不阻断使用

### 技术栈
- Kotlin 1.9.20 + Jetpack Compose (ui 1.5.4 / material3 1.1.2)，AGP 8.2.0
- 离线构建（本地 Maven 仓库），minSdk 26 / targetSdk 34 / compileSdk 34
- 数据层：SQLiteOpenHelper（tasks / categories 表，WAL），TaskRepo 单例 + Flow
- 提醒：AlarmManager setExactAndAllowWhileIdle（无权限时降级 setAndAllowWhileIdle/set）

### 真机验证（realme RMX3888, ColorOS/Android 16）
- 安装 / 启动 / UI 渲染：通过（截图确认标题、筛选 chips、分类 chips、空态文案）
- 新建任务（标题 + 紧急 + 分类 + 提醒时间）→ 保存 → 列表卡片显示「紧急」徽标与提醒时间：通过
- 精确闹钟注册（dumpsys alarm 确认 RTC_WAKEUP → ReminderReceiver）：通过
- 闹钟触发 → 通知发布（channel=wbl_reminder, importance=3, AUTO_CANCEL）：通过
- 过期补发（rescheduleAll 立即通知 + 标记已提醒）：通过
- 勾选完成 → 卡片移出待办列表：通过
- 进程重启后数据持久（SQLite）：通过
- 全程无 FATAL / AndroidRuntime 崩溃

### 已知事项
- ColorOS 等新装应用可能被系统冻结（cached app freezer）并将精确闹钟延后（app standby adjustment），需用户允许「自启动 / 后台运行」或将应用加入电池优化白名单；开发机已通过 `am set-standby-bucket ... 10` + deviceidle 白名单验证提醒链路正常。

## v1.0.0 (release, versionCode=1)
- 签名 release APK: release/wbl-v1.0.0-release.apk (md5 a7ee8e7a12c9022b5c81283a12c01e1f)
- 签名证书: CN=wangbuliao-todo (keystore/wbl.keystore, 备份于 release/keystore-backup/)
- SHA-256 指纹: 2865adb83df515820c5cc41181b83fddca98ac1487862f1f5a40afbc47cea818
- 注意: 后续所有版本必须用同一 keystore 签名, 否则无法覆盖升级

## 在线更新 E2E 验证（2026-09-12）
- 真机实测全链路通过：同钥构建 versionCode=2/1.0.1 测试包 + proot loopback 本地服务器（127.0.0.1:8788，release/ota-tools/ota_server.py）
- 检查更新 → 弹「发现新版本 v1.0.1」对话框 → 立即下载 → 9618837B（md5 与源包一致）→ 系统安装器升级成功（数据保留）→ 再检查正确提示「当前已是最新版本」
- 验证后 pm install -r -d 原位降级回官方 v1.0.0（数据保留），清除测试注入的更新地址、下载包与临时文件
- 实测确认：更新地址存于 shared_prefs/wbl_prefs.xml（键 update_url）；下载落点 files/Download/wangbuliao_v*.apk
- release/README.md 更新 E2E 记录与自建服务器说明，新增 ota-tools/（服务器脚本 + wbl.json 模板）
