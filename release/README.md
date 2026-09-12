# 忘不了 发布与分发手册（当前正式版 v1.2.3 / versionCode 10203）

## 更新渠道架构（v1.2.0 起：多线路 + 测速）

### 检查更新（5 条线路，失败自动切换，记住上次成功线路优先）
1. `https://raw.githubusercontent.com/onono2012/wangbuliao-todo/main/wbl-update.json`
2. `https://gh-proxy.com/<同上raw地址>`
3. `https://ghproxy.net/<同上raw地址>`
4. `https://cdn.jsdelivr.net/gh/onono2012/wangbuliao-todo@main/wbl-update.json`
5. `https://api.github.com/repos/onono2012/wangbuliao-todo/releases/latest`（兜底，解析 Release 元数据）

- 请求附带 `cb=<时间戳>` 防缓存参数（对 raw/jsDelivr 有效；**ghproxy.net 按路径缓存、忽略 query，TTL 分钟级**）
- 清单 `wbl-update.json` 字段：versionCode / versionName / force / desc（更新内容 Markdown）/ sha256 / size / fileName / routes（下载线路表）/ publishedAt

### 下载（routes 线路表 + 智能测速，v1.2.3）
- 下载前**并行测速**所有线路（单条限时 5 秒），按实测速度排序，选最快线路下载；失败线路自动排后
- routes 顺序：api 资产端点 → gh-proxy → ghproxy.net → ghfast.top → github.com 直链
- 断点续传（Range）跨线路共用；sha256 + 文件大小双重校验，不符自动删包报错
- 下载落点：`Android/data/com.wangbuliao.todo/files/Download/wangbuliao_v<版本名>.apk`
- 123 网盘备份：APK / wbl-update.json / SHA256SUMS.txt 同步存于网盘 WebDAV `/app/` 目录（GitHub 渠道整体不可用时的备份途径）

## ⚠️ 密钥安全（最重要）
- `keystore/wbl.keystore`（备份 `release/keystore-backup/`）是**唯一签名密钥**，所有版本必须同 keystore 签名，否则用户无法覆盖升级。
- 请自行离线备份 keystore + 密码 properties；**切勿**分发或推送到任何仓库（.gitignore 已排除）。
- 凭据一律走环境变量：`GITHUB_TOKEN`（repo scope PAT）、`WBL_WEBDAV_USER`、`WBL_WEBDAV_PASS`。勿写入脚本/日志/仓库。

## 发布新版本流程（tools/publish.py，proot ext4 权威树）
```bash
cd /root/projects/wbl
# 1. 改 app/build.gradle：versionCode（主*10000+次*100+修订）/ versionName
# 2. 构建（约1分钟）
gradle assembleRelease --no-daemon -Dorg.gradle.vfs.watch=false \
  -Djava.io.tmpdir=$PWD/.gradle-tmp
cp app/build/outputs/apk/release/app-release.apk release/dist/wbl-v<版本名>-vc<versionCode>.apk
# 3. 写更新内容 release/notes-v<版本名>.md（会展示在用户端强更对话框，用 Markdown）
# 4. 一键发布（Release+资产+json提交+jsDelivr purge+网盘同步+全线路验证）
python3 tools/publish.py release/dist/wbl-v<版本名>-vc<versionCode>.apk \
  -v <版本名> -c <versionCode> -n release/notes-v<版本名>.md
```
publish.py 其他模式：
- `--commit-json wbl-update.json`：重新提交/回滚分发清单（含网盘同步与线路验证）
- `--delete-tag v<版本名>`：删除 Release + tag（测试版清理）
- 网盘凭据经 `WBL_WEBDAV_USER/PASS`；上传资产必须用 `uploads.github.com` 主机（api.github.com 会 404）

### 发布注意事项（实测经验）
- **测试版发布要谨慎**：清单 json 前进后，即使立即删除 Release 并回滚 json，中转代理缓存（ghproxy.net TTL 分钟级、jsDelivr @main 12 小时需 purge）仍会造成「幽灵版本」窗口——用户可能短暂看到已删除版本的强更框（点下载会 404 失败，自愈但体验差）。正式发版 json 只前进，无此问题。
- jsDelivr purge：`https://purge.jsdelivr.net/gh/onono2012/wangbuliao-todo@main/wbl-update.json`（publish.py 自动调用；proot 内 urllib 偶发 SSL 握手超时，可 curl 重试）
- raw.githubusercontent 提交后 CDN 传播约 1~5 分钟；验证时带 `cb` 参数取新值
- 同 versionCode 重发：GitHub 不允许同名资产覆盖，publish.py 会先删旧资产；**同版本号重发的 APK 内容若变化，务必同步 json 的 sha256/size**

## 产物（release/dist/）
- `wbl-v1.2.3-vc10203.apk`（9.6MB，sha256 `b40c981e8897382e459a48c479992cbaba63c04d489ddbae3ea3049f8486c1a8`）← **当前正式版**
- `wbl-v1.2.0-vc10200.apk`（sha256 `96574b615d…46468cb1`）
- `wbl-v1.1.0-vc10100.apk`（sha256 `0d67ef137d8fc0fbb95d591112e082e96c1046cfd31800efaad9a373eb2243c7`）
- minSdk 26（Android 8.0+），targetSdk 34；与 debug 版签名不同，装过 debug 需先卸载

## E2E 验证记录（真机 realme RMX3888 / ColorOS Android 16，全部通过）

### v1.1.0：GitHub 渠道首验（2026-09-12）
- v1.1.0 → 测试 Release v1.1.1：启动自动弹强更框（更新内容/当前版本/仅「立即更新」）→ api 资产端点下载 14%→98% → sha256 校验 → 安装器升级 10100→10101 ✓ → 同版本启动无框、手动检查「已是最新」✓ → 测试版删除、`pm install -r -d` 回滚官方 v1.1.0 ✓
- 网络结论（本设备运营商）：github.com:443 直链超时；api.github.com 可达；release-assets 下载 ~50KB/s；HttpURLConnection 不跟随跨主机 302 → 代码手动循环跟随（≤5 跳）；资产端点须带 `Accept: application/octet-stream`

### v1.2.x：多线路 + 测速（2026-09-12）
- **封锁切线**：iptables REJECT GitHub 网段（140.82.112.0/20、20.205.243.0/24、185.199.108.0/22、ip6tables 2606:50c0:8000::/46）+ DNS 污染模拟断墙 → 检查更新经中转线路成功、APK 经 ghproxy.net 兜底下载完成并安装 ✓（封锁规则与 proot 共享 netns，发布前须先解除）
- **v1.2.0 → v1.2.3 强更**：快线路（api 资产端点）9.6MB 约 25 秒下载 → sha256 校验 → 升级成功 ✓；设置页手动检查「当前已是最新版本 v1.2.3」✓
- **测速复验**（v1.2.4 测试版，复用 v1.2.3 APK 字节）：并行测速 UI「正在测速 5 条线路…」✓；被 DNS 毒化的 gh-proxy.com probe 失败（ConnectException）自动排除 ✓；最快线路下载+安装完成 ✓；测试版 Release/tag/网盘文件全删、json 回滚 10203 ✓
- 教训：ghproxy.net 忽略 cb 按路径缓存 json（TTL 分钟级）→ 回滚后有短暂幽灵窗口，自愈

## 官网主页
GitHub Pages：**https://onono2012.github.io/wangbuliao-todo/** （源文件 `docs/index.html`，分支 main、路径 /docs；真机实测可达，页面动态拉取 wbl-update.json 展示最新版本与加速直链）

## 旧版自建 OTA（v1.0.x 方案，已废弃仅存档）
- v1.0.x 曾支持「设置 → 更新服务器地址」自定义 OTA 端点（`wbl_prefs.xml/update_url`），v1.1.0 起移除，残留旧配置无影响。
