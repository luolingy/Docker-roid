# 更新日志

## v0.5.0 — 2026-11-14

这一版把 Docklet 从"能跑容器"推到"能日常用"：界面重做、双语、主题色、崩溃自恢复、后台保活、更新检测。

### 新功能

- **多语言（简体中文 / English）**
  - 设置 → 外观 → 语言，三选一：跟随系统 / 简体中文 / English，切换后立即生效。
  - 默认英文（`values/`），中文在 `values-zh/`，共 315 条文案；设备语言非中文时自动落英文。
  - 语言值存 SharedPreferences 而非 DataStore —— `attachBaseContext` 需要同步读取。

- **主题色切换**
  - 5 套强调色：蓝 / 青 / 紫 / 绿 / 橙。只替换 primary / tertiary 及其容器色，中性表面色保持不变，深浅色模式都跟随系统。
  - 设置 → 外观 → 强调色，点选即生效，无需重启。

- **更新检测**
  - 设置 → 更新 → 检查更新，读 GitHub Releases 最新 tag，与本地 `versionName` 做语义化比较（`0.10.0 > 0.9.9`）。
  - 仓库还没有 release 时（HTTP 404）按"已是最新"处理，不误报失败。
  - 有新版本时给下载入口，直接跳 Release 页。

- **崩溃自重启**
  - 全局 `UncaughtExceptionHandler` 捕获后写 `files/crash/last_crash.txt`，1.5 秒后用 `AlarmManager.setAndAllowWhileIdle` 重新拉起。
  - 用 AlarmManager 而不是 `Handler.postDelayed`：主线程崩了 Looper 已经死了。
  - 10 分钟内最多重启 3 次，防止"一开就崩"的死循环；恢复后首屏 Snackbar 提示"上次异常退出已自动恢复"。
  - 设置 → 后台 → 最近一次崩溃日志，可原地查看。

- **后台保活**
  - 设置 → 后台 → 后台保活开关（默认开）。划掉最近任务不再停容器。
  - 前台服务 `START_STICKY` + 空 Intent 恢复；`onTaskRemoved` 不清服务。
  - VM 异常退出后按 4s / 8s 退避自动重拉，最多 2 次。
  - 开机自启：`BootReceiver` 监听 `BOOT_COMPLETED` / `QUICKBOOT_POWERON`，仅当"随开机启动 + 底座就绪 + 保活开启"三者都满足才拉 VM。
  - 电池优化白名单状态检测 + 一键跳转申请。

### 界面

- 设计系统重建：统一卡片、状态胶囊、分段按钮、设置行、提示条、空状态；五个对话框收敛到同一套 `DockletDialog`。
- 容器详情重做：状态胶囊 + 带图标操作栏 + 四个分段页签（日志 / 资源 / 信息 / 执行），日志自动跟到最新一行并支持一键复制。
- 主页降级加载：先出 `/_ping` + 镜像 + 容器，慢接口（`/info`、`/system/df`）后到再刷新，首屏不再转几十秒。
- 设置页新增三个分区：外观、后台、更新。

### 修复

- `KeyValueRow` 在窄屏把中文标签逐字折行（"镜"和"像"分列值两侧）——改为长值强制上下排。
- `ComposeScreen` / `PortForwardDialog` / `ContainerDetailScreen` 用 `startsWith("已")` 判断操作成功，英文文案下永远判失败——改为独立布尔状态。
- 容器详情页镜像全名、挂载路径过长时挤压标签。

### 构建

- 版本号 `0.1.0 (1)` → `0.5.0 (5)`。
- release 签名：`keystore.properties` + `docklet-release.jks`（均 gitignore）；缺失时自动回退 debug 签名，clone 下来直接 `assembleRelease` 也能出包。
- `buildConfig = true`（关于页与更新检测需要 `VERSION_NAME` / `VERSION_CODE`）。
- `isShrinkResources = true`。

---

## v0.4.2

- 底座发布：`Image`（内核）+ `rootfs.squashfs` + `qemu-bundle.tar.gz`。
- guest：Alpine 3.20 + podman 5.2.5 + crun 1.17。

## v0.4.1

- initramfs 由 `.work/build-initramfs.py` 生成并内置到 assets（1.47 MB）。
- 修复 `.gz` 资产名被 AAPT2 自动解压去扩展名的问题，改为 `noCompress += "img"`。

## v0.4.0

- 容器运行时打通：arm64 / amd64 双架构实测通过（binfmt + qemu-x86_64）。
- `/dev/vda` = rootfs（squashfs 只读），`/dev/vdb` = data.ext4。
- 容器网络走 `netns = "host"`：已发布内核无 bridge 支持（`RTNETLINK: Not supported`）。

## v0.3.0

- Docker REST 客户端 + 容器 / 镜像 / 资源 / 终端界面。

## v0.2.0

- VM 生命周期：前台服务 + WakeLock + 常驻通知，串口启动标记驱动状态机。

## v0.1.0

- 初始骨架：Gradle 工程、Compose 底部导航、QEMU 拉起链路。
