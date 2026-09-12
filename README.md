# 忘不了（wangbuliao-todo）

简洁可靠的 Android 待办 / 记事本。包名 `com.wangbuliao.todo`，minSdk 26（Android 8.0+）。

## 功能
- **待办管理**：添加 / 编辑 / 删除任务，勾选完成（待办 ↔ 已办），SQLite 本地持久化
- **紧急标注**：紧急事项置顶高亮，支持「仅紧急」筛选
- **提醒**：日期 + 时间提醒，精确闹钟（USE_EXACT_ALARM）+ 通知；开机与启动自动恢复未触发提醒，过期补发
- **分类**：预置 工作 / 生活 / 学习 / 其他，支持自定义新增（级联删除）
- **筛选**：待办 / 已办 / 全部 × 分类组合
- **主题**：浅色 / 深色 / 跟随系统
- **在线更新**：多线路更新通道（raw / gh-proxy / ghproxy / jsDelivr 中转 + GitHub API 兜底），下载前并行测速选最快线路，断点续传，sha256 + 大小双重校验；123 网盘同步备份分发

## 下载
- **Releases**：https://github.com/onono2012/wangbuliao-todo/releases/latest （资产 `wbl-v<版本名>-vc<versionCode>.apk`）
- 当前正式版：**v1.2.3 (10203)**
- 官网主页（GitHub Pages）：https://onono2012.github.io/wangbuliao-todo/

## 构建
```bash
# 需 JDK 17 + Gradle 8.5 + Android SDK 34（本地 Maven 仓库离线构建）
gradle assembleRelease --no-daemon -Dorg.gradle.vfs.watch=false -Djava.io.tmpdir=$PWD/.gradle-tmp
```
技术栈：Kotlin 1.9.20 + Jetpack Compose（material3）、AGP 8.2.0、SQLiteOpenHelper（WAL）。

## 仓库结构
- `app/` — 应用源码（`update/Updater.kt` 为在线更新器）
- `tools/publish.py` — 一键发布（GitHub Release + wbl-update.json + 123 网盘同步 + 全线路验证）
- `release/` — 发布说明与手册（`release/README.md`）
- `docs/` — GitHub Pages 官网主页
- `wbl-update.json` — 更新分发清单（由 publish.py 维护，勿手改）
- `CHANGELOG.md` — 版本历史

签名 keystore 与构建产物不入库（见 .gitignore）；发布细节见 `release/README.md`。
