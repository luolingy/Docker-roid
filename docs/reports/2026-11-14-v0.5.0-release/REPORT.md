# Docklet v0.5.0 发布验证报告

- 日期：2026-11-14
- 版本：`0.5.0 (versionCode 5)`
- 设备：Redmi Note 9 5G（cannon / MT6853T），Android 12 / API 31，7.4 GB RAM，无 root
- 连接：`adb connect 192.168.1.11:5555`
- 产物：`app/build/outputs/apk/release/app-release.apk`（27.98 MB，release 签名）

---

## 1. 本版交付内容

| 功能 | 状态 | 说明 |
|---|---|---|
| 多语言（zh / en） | ✅ 已验证 | 315 条文案，切换即时生效并持久化 |
| 主题色切换（5 套） | ✅ 已验证 | 只换 primary/tertiary 系，中性表面色不变 |
| 更新检测 | ✅ 已验证 | GitHub Releases `releases/latest` + 语义化比较 |
| 崩溃自重启 | ⚠️ 仅代码验证 | 无可用触发路径，未做真机触发 |
| 后台保活 | ✅ 部分验证 | 前台服务存活 / 通知 / 电池白名单状态；划卡测试被 MIUI 手势阻塞 |

---

## 2. 验证过程与结果

### 2.1 安装与启动

```
adb uninstall com.midsancode.docklet          # 旧包是 debug 签名，必须先卸
adb install -r app-release.apk                # Success
adb shell am set-standby-bucket com.midsancode.docklet active
adb shell cmd deviceidle whitelist +com.midsancode.docklet
```

- `versionCode=5 versionName=0.5.0` ✅
- 启动后 `mResumedActivity = com.midsancode.docklet/.MainActivity`，logcat 无 `FATAL` ✅
- **R8 混淆后 UI 正常渲染，无资源缺失、无 Compose 崩溃** ✅

> 同签名升级（后续 0.5.x）可直接 `install -r` 覆盖，`filesDir/vm-bundle` 保留，不用重下底座。
> 底座目录是常量 `VmBundle.BUNDLE_VERSION = "0.1.0"`，与 App 版本号无关。

### 2.2 多语言

- 设置 → 外观 → 语言：`跟随系统 / 简体中文 / English`
- 点 English → Activity 重建 → 全界面英文（`shots/04_home_en.png`）
- 点简体中文 → 恢复中文（`shots/10_settings_zh_final.png`）
- **页签保持在设置页**（`rememberSaveable`），不再跳回首页
- 语言值存 SharedPreferences（`Prefs`），`attachBaseContext` 同步读取

**修掉的两个真 bug：**

1. **启动阶段文案不跟随应用语言**
   现象：英文界面下状态胶囊显示中文「容器引擎启动」。
   原因：`VmManager` 用 Application context 调 `getString`，Application 的 configuration 跟随**系统**语言，而应用内语言覆盖只套在 Activity 上。
   修法：`VmManager.i18n = LocaleHelper.wrap(appContext)`，`VmService` 同样处理（通知文案）。修复后启动日志显示 `[host] 阶段：Starting container engine`。

2. **底栏英文标签断词 / 截断**
   现象：`Containers` 在 11sp 下断成 `Contain / ers`；写死 10sp 又被截成 `Containe`。
   原因：6 个页签在 393dp 屏上每项约 65dp，扣掉左右内边距只剩约 43dp。
   修法：`NavLabel` 用 `BoxWithConstraints` + `TextMeasurer` 从 11sp 逐档降到 8sp，取第一个放得下的字号。对任意语言成立，不写死数字。

### 2.3 主题色

- 5 套：蓝 / 青 / 紫 / 绿 / 橙
- 点「橙」→ 按钮、滑块、选中 chip、数值、底栏图标全部即时变橙（`shots/03_theme_orange.png`）
- 重建 Activity（切语言）后主题色保持 ✅
- 杀进程重进后仍保持（DataStore 持久化）✅

### 2.4 更新检测

- 设置 → 更新：当前版本 `0.5.0`，点「检查更新」
- 结果：绿色提示 `You are on the latest version`（`shots/06_update.png`）
- 仓库最新 release 是 `v0.4.2`，本地 `0.5.0` 更新 → 判定正确 ✅
- 404（仓库无 release）按「已是最新」处理，不误报失败

### 2.5 底座下载

- 设置 → VM 底座 → 「下载底座」
- 进度行格式正确：`Image: 23.2 / 41.5 MB`（`shots/07_download.png`）
- 三个文件（Image / rootfs.squashfs / qemu-bundle）全部下完 → 状态变 `Ready` ✅
- 镜像站回退链生效（`docker.m.daocloud.io` → `docker.1panel.live` → `hub.rat.dev`）

### 2.6 VM 启动与容器

- 主页点「Start VM」→ 30 秒左右到 `Ready`，引擎卡片显示：
  - Version `5.2.5`，Arch `arm64`，Containers `0 running / 0 total`，Images `1`，Disk `8.5 MB`
- 终端页自动连上 guest root shell（`root@docklet:~#`）
- 实跑容器：

```
podman run --rm alpine echo hello-v050
→ Trying to pull docker.io/library/alpine:latest...
→ hello-v050
```

**R8 混淆后的 release 包端到端跑通** ✅（`shots/09_container_run.png`）

### 2.7 后台保活

- `dumpsys activity services com.midsancode.docklet` → `isForeground=true foregroundId=1001` ✅
- QEMU 进程 `libqemu-system-aarch64.so` 独立存在、未被回收 ✅
- 设置 → 后台：
  - 「后台保活」开关默认开 ✅
  - 「忽略电池优化」显示 `Whitelisted`（由 `cmd deviceidle whitelist` 注入后被正确识别）✅
  - 「最近一次崩溃日志」入口存在 ✅
- `onTaskRemoved` 不清服务；`START_STICKY` + 空 Intent 恢复；`BootReceiver` 监听开机广播

> **未完成**：MIUI 的最近任务卡片用 `adb shell input swipe` 划不掉（手势被卡片内容滚动吃掉），
> 所以「划卡后容器是否继续跑」没做成真机验证，只能以服务状态 + 代码路径为准。

### 2.8 崩溃自重启

代码路径：
`CrashHandler.install(app)` → `uncaughtException` → `Prefs.recordCrash` 节流 → 写 `files/crash/last_crash.txt`
→ `AlarmManager.setAndAllowWhileIdle` 1.5s 后拉 `MainActivity`（带 `EXTRA_AFTER_CRASH`）→ 首屏 Snackbar 提示。

- 用 AlarmManager 而非 `Handler.postDelayed`：主线程崩了 Looper 已死
- 10 分钟内最多 3 次，防「一开就崩」死循环
- **未做真机触发验证**（release 包无可用崩溃入口）

---

## 3. 构建踩坑记录（重要，会再遇到）

### 3.1 `-keep class androidx.compose.** { *; }` 是灾难

`app/proguard-rules.pro` 原来有这条。后果：

- `seeds.txt` 涨到 **14 MB**（`material-icons-extended` 上千个图标类全部被保留）
- R8 优化阶段跑 **38 分钟**出不了包

Compose 各库自带 consumer rules（`META-INF/com.android.tools/r8`），这条规则完全不需要。
删掉后 `seeds.txt` 降到 **779 KB**。

### 3.2 内存预算

本机 7.9 GB 总内存、空闲 0.2 GB（IDE + WSL + 多个 WorkBuddy 进程）。
原配置 `org.gradle.jvmargs=-Xmx4096m` + `kotlin.daemon.jvmargs=-Xmx2048m` ≈ 6 GB，
R8 阶段 daemon 被系统直接杀掉（**无 `hs_err` 文件，进程凭空消失**，daemon 日志停在 `ExecuteBuild`）。

现配置：

```properties
org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
org.gradle.workers.max=2
kotlin.daemon.jvmargs=-Xmx1536m
```

### 3.3 `-dontoptimize`

即使降了内存，R8 仍在 **optimizer 阶段**被杀三次。最终加 `-dontoptimize`：

- 保留 tree-shaking + 混淆（dex 体积的主要来源，`classes.dex` 5.59 MB）
- 跳过优化 pass（主要省运行时开销，对 TCG 慢速 VM 场景影响可忽略）

> 换到内存宽裕的机器可以删掉这行。

### 3.4 排障手法

```bash
tasklist //FI "IMAGENAME eq java.exe" //FO CSV     # Git Bash 里要双斜杠
ls -la app/build/outputs/mapping/release/          # seeds/usage/mapping 判断 R8 走到哪步
tail ~/.gradle/daemon/8.9/daemon-<pid>.out.log     # daemon 崩溃现场
```

---

## 4. 已知限制

1. **容器独立网络不可用**：已发布内核无 bridge 支持（`RTNETLINK: Not supported`），默认 `netns = "host"`。设置页选 bridge 会给出警告。
2. **触达网络后端的 REST 路由极慢**：`/version`、`/networks`、`/system/df`、`/info` 在 TCG 下几十秒，非死锁。
3. **data 层仍有少量中文错误串**：`EngineRepository`（"执行失败:"）、`DockerApi`（"错误：构建上下文不存在"）、`Diagnostics` 导出内容。这些是诊断文本，未纳入 i18n。
4. **`VmService` 串口日志里的 `[host] 阶段：` 前缀**仍是中文（host 侧调试行，与 guest 英文输出混排）。
5. 主题色 chip 在英文下「Orange」会换到第二行（`FlowRow` 自然行为，不影响功能）。

---

## 5. 复现命令速查

```bash
# 构建
./gradlew :app:assembleRelease -Pkotlin.incremental=false

# 装（旧包是 debug 签名时先卸）
adb uninstall com.midsancode.docklet
adb install -r app/build/outputs/apk/release/app-release.apk

# 让 App UID 能出网（后台时受待机分组限制）
adb shell am set-standby-bucket com.midsancode.docklet active
adb shell cmd deviceidle whitelist +com.midsancode.docklet

# 坐标换算：截图 500×1080 ↔ 设备 1080×2400（x×2.16，y×2.22）
adb shell uiautomator dump /sdcard/ui.xml
adb exec-out cat //sdcard/ui.xml > .work/ui.xml
```
