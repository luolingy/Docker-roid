# Docklet 真机测试报告（2026-11-13）

> 设备：Redmi Note 9 5G（M2007J22C / cannon），Android 12（API 31），MT6853T，8 核，7.7 GB RAM，4 KB 页  
> 被测产物：`app/build/outputs/apk/debug/app-debug.apk`（`com.midsancode.docklet` v0.1.0，43 MB，构建于 2026-11-13 07:05）  
> 连接方式：`adb connect 192.168.1.11:5555`（设备实际局域网 IP 为 `192.168.0.102`，MIUI 已开启无线调试）  
> 测试方式：全流程 adb 驱动（安装 → 冷启动 → 六页 UI 巡检 → 底座下载 → 启动 VM → QMP/串口取证）

---

## 一、结论（先看这段）

**UI 层完整可用，VM 启动链路在真机上 100% 跑不通。** 三处硬伤串在一起，任意一处都足以让 `启动 VM` 直接失败：

| 编号 | 级别     | 问题                                                                           | 现象                                                                       |
| -- | ------ | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| B1 | **P0** | `BundleDownloader` 下载的是 `rootfs.ext4`，而 GitHub Release 只提供 `rootfs.squashfs` | 三个镜像源全部 `HTTP 404`，底座永远下载失败                                              |
| B2 | **P0** | `QemuRunner` 内核参数写死 `rootfstype=ext4 rw`，但实际 rootfs 是 squashfs               | `Kernel panic - not syncing: VFS: Unable to mount root fs on "/dev/vda"` |
| B3 | **P0** | 内核参数写死 `init=/usr/local/bin/startvm`，但已发布镜像里没有该文件（`/usr/local/bin` 为空）       | `Kernel panic: Requested init /usr/local/bin/startvm failed (error -2)`  |

**好消息**：QEMU 本身在这台机器上完全跑得起来——QEMU 11.1.3（TCG）在 app 的 SELinux 域内正常启动，4 vCPU/SMP、QMP 通道、squashfs 挂载、guest 用户态全部验证通过；镜像里 `podman` 也在（`/usr/bin/podman`）。**问题不在虚拟化，只在"底座版本 ↔ 启动参数"没对齐。**

---

## 二、测试环境

| 项        | 值                                            |
| -------- | -------------------------------------------- |
| 设备       | Xiaomi M2007J22C（Redmi Note 9 5G），代号 cannon  |
| SoC / 内核 | MediaTek MT6853T（Dimensity 800U），8 × aarch64 |
|          |                                              |
| 系统       | Android 12（API 31），MIUI，SELinux `Enforcing`  |
| 内存       | 7,774,832 kB（约 7.4 GiB）                      |
| 存储       | `/data` 108 GB，测试开始时仅剩 **500 MB（100% 占满）**   |
| 页大小      | 4096（非 16 KB 设备，无需 16 KB 对齐验证）               |
| 权限       | 无 root（`uid=2000(shell)`），仅 adb shell        |
| adb      | Android Debug Bridge 1.0.41 / 37.0.1         |

**环境层面的两个坑（下次复现会再遇到）：**

1. **MIUI 首次安装被拦**：`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，需在开发者选项里打开「USB 安装」。
2. **存储必须留足**：`/data` 剩 500 MB 时 `pm install` 报 `Requested internal only, but not enough space`（PackageInstaller 的 `getStorageBytesUntilLow` 扣掉低存储阈值后判为 0）。腾到 1.4 GB 后一次装成功，安装耗时 26 s。

---

## 三、用例与结果

| #  | 用例                              | 结果       | 说明                                                                                     |
| -- | ------------------------------- | -------- | -------------------------------------------------------------------------------------- |
| 1  | 安装 APK                          | ✅ 通过     | 43 MB，26 s；需 ≥1.4 GB 空闲                                                                |
| 2  | 冷启动                             | ✅ 通过     | `TotalTime` 1664–1902 ms，无白屏无崩溃                                                        |
| 3  | 主页状态渲染                          | ✅ 通过     | 正确显示"已停止" + "底座未就绪…请到设置页点下载底座"                                                         |
| 4  | 六页导航（主页/容器/镜像/资源/终端/设置）         | ✅ 通过     | 全部可进入，无崩溃；引擎未运行时显示"VM 未启动"空态，未死锁                                                       |
| 5  | 终端页                             | ✅ 通过     | 深色终端 + 快捷键条，VM 未运行显示"（等待 VM 输出…）"                                                      |
| 6  | 设置页资源滑块 / 端口 / DNS              | ✅ 通过     | 1024 MB / 512 MB / 2375 / 1.1.1.1,8.8.8.8 正常渲染                                         |
| 7  | 底座下载                            | ❌ **失败** | Image 下载成功（43.5 MB / 20 s，走 `ghfast.top`），`rootfs.ext4` 三源全 404 → 整体失败                 |
| 8  | 启动 VM（使用已发布 v0.4.2 底座）          | ❌ **失败** | 两条 panic 路径（见 B2/B3），App 正确捕获并显示"错误"红点                                                 |
| 9  | 前台服务 + 常驻通知                     | ✅ 通过     | `VmService` 以 FGS 启动，`foregroundId=1001`，通知通道 `vm_status` 存在                           |
| 10 | QEMU 二进制可用性                     | ✅ 通过     | QEMU 11.1.3，`--version` / `-M help` 正常；72 个依赖 .so 全部从 `nativeLibraryDir` 加载成功          |
| 11 | TCG + SMP                       | ✅ 通过     | `-accel tcg,thread=multi -smp 4` 启动成功，QMP 报 `running`，4 个 vCPU 线程就绪                    |
| 12 | QMP 控制通道                        | ✅ 通过     | `run-as` + `nc -U qmp.sock` 可 `query-status` / `info cpus` / `info registers` / `quit` |
| 13 | guest 内核启动                      | ✅ 通过     | 6.12.0 aarch64，squashfs 挂载成功（`device 254:0`），进入用户态                                     |
| 14 | guest 内容完整性                     | ✅ 通过     | `/usr/bin/podman` 存在（3 个 podman 相关文件），`crun` 在                                         |
| 15 | OpenRC 完整启动（已发布镜像）              | ❌ **失败** | 只读根下 `bootmisc` 无限失败重试，卡在 boot 运行级（见 B4）                                               |
| 16 | 旧包 `com.dockerroid`（历史版本，设备上已装） | ❌ 卡死     | 卡在 `* Caching service dependencies ... [ ok ]` 后无任何输出（见 B5）                            |

---

## 四、问题详情

### B1（P0）底座下载文件名与 Release 资产不一致

`app/src/main/java/com/midsancode/docklet/data/vm/BundleDownloader.kt:52`

```kotlin
for (name in listOf("Image", "rootfs.ext4")) {
```

实际 GitHub Release（`luolingy/Docklet` v0.4.2）资产只有：

| 资产                   | 大小           |
| -------------------- | ------------ |
| `Image`              | 43,512,320 B |
| `rootfs.squashfs`    | 44,871,680 B |
| `qemu-bundle.tar.gz` | 32,863,233 B |

而 `SettingsScreen.kt:96` 的按钮文案写的是「下载底座（约 85MB）」、未就绪提示写的是「需要 Image + rootfs.squashfs」——**UI 文案对、下载逻辑错**。

真机日志：

```
I BundleDownloader: 尝试: https://ghfast.top/…/latest/download/rootfs.ext4
W BundleDownloader: …/rootfs.ext4 失败: HTTP 404
W BundleDownloader: …/rootfs.ext4 失败: HTTP 404      (gh-proxy.com)
W BundleDownloader: …/rootfs.ext4 失败: HTTP 404      (直链)
```

**修复**：把 `"rootfs.ext4"` 改为 `"rootfs.squashfs"`（或按 `VmBundle.isReady()` 的顺序 `rootfs.ext4 → rootfs.squashfs` 依次尝试）。

### B2（P0）内核参数 rootfstype 与实际 rootfs 格式不匹配

`app/src/main/java/com/midsancode/docklet/data/vm/QemuRunner.kt:125`

```kotlin
"-append", "console=ttyAMA0 root=/dev/vda rootfstype=ext4 rw " + …
```

但 `VmBundle.resolve()` 在没有 `rootfs.ext4` 时会回退到 `rootfs.squashfs`，`buildCommand` 却仍按 ext4 挂载。

真机现象（截图 `shots/03-kernel-panic-ext4.png`）：

```
[ 5.705286] ---[ end Kernel panic - not syncing: VFS: Unable to mount root fs on "/dev/vda" or unknown-block(254,0) ]---
```

**修复**：按 `b.rootfs.name` 判断——`rootfs.squashfs` 时用 `rootfstype=squashfs ro`，`rootfs.ext4` 时用 `rootfstype=ext4 rw`。

### B3（P0）init 路径在已发布镜像中不存在

同处 `QemuRunner.kt:125-126`，`init=/usr/local/bin/startvm`。

在 guest 内实测（`evidence/guest-console-probe.txt`）：

```
~ # ls /usr/local/bin          ← 空目录，无 startvm
~ # which podman
/usr/bin/podman
```

说明**当前工作副本的 guest 脚本（`build-rootfs/guest/usr/local/bin/startvm`）还没有打进已发布的 v0.4.2 镜像**。boot 会报：

```
[ 7.891046] Kernel panic - not syncing: Requested init /usr/local/bin/startvm failed (error -2).
```

（error -2 = ENOENT）

**修复**：二选一——① 重新构建并发布带 `startvm` 的 `rootfs.squashfs`；② 启动参数按镜像实际 init 走（老镜像用默认 `/sbin/init` + OpenRC，但见 B4）。

### B4（P1）只读 squashfs + 原生 OpenRC：boot 运行级死循环

用已发布镜像 + 默认 init 启动，boot 阶段反复输出同一段错误、永不前进：

```
 * Remounting root filesystem read/write ...mount: mounting /dev/root on / failed: Permission denied
 * Root filesystem could not be mounted read/write   [ !! ]
 * Creating user login records ...can't create /var/log/wtmp: Read-only file system
 * ERROR: bootmisc failed to start
 * / is not writable; unable to clean up underlying /run
（以上 3 行无限循环）
```

原因：镜像按"可写根"或"由 startvm 挂 tmpfs 覆盖"设计，但直接交给 OpenRC 时没人给它可写的 `/var`、`/run`，`bootmisc` 失败后 OpenRC 反复重试。

**修复**：这正是 `startvm` 存在的意义（`build-rootfs/guest/usr/local/bin/startvm:26-35` 会探测可写性并挂 tmpfs）。所以 B3 修好后 B4 自动消失；若继续用 OpenRC 路线，则必须在 `docklet-boot` 里先挂 `/var`、`/run` tmpfs，并保证 `/etc/init.d/dev` 服务存在（否则 `hwdrivers`/`machine-id` 报 "needs non existent service dev"）。

### B5（P1，历史包）旧版 `com.dockerroid` 卡在 "Caching service dependencies"

设备上还装着本项目历史版本 `com.dockerroid`（Docker-roid 0.1.0，2026-11-12 构建，自带一套能跑的 squashfs + 512 MB ext4 底座）。它的启动链路能进 OpenRC，但**卡死**在：

```
 * Caching service dependencies ... [ ok ]
```

取证结论（QMP 直连，非猜测）：

- QMP `query-status` → `running`，2 个 vCPU 线程存在；
- `/proc/<pid>/stat` 采样 30 s 只增长 124 jiffies（≈1.2 s CPU，4%），**vCPU 基本空转**；
- `info registers` 两次快照 PC 完全一致（`ffffa0b0199b6560`），X00 递增 → 卡在某个固定内核循环里，不是"慢"，是"停"；
- 30 s 后仍无任何新串口输出。

旁证：该镜像的 OpenRC 在依赖缓存阶段就报 `Service 'hwdrivers' needs non existent service 'dev'`、`Service 'machine-id' needs non existent service 'dev'`——`/etc/init.d/dev` 缺失，服务图不完整。

> 这条对当前代码的直接价值：**OpenRC 这条老路线在真机上不可靠**，`init=/usr/local/bin/startvm`（绕过 OpenRC 直接跑脚本）的方向是对的，别回退。

### B6（P1）`data.ext4` 建了但从不挂载，白占 512 MB

`VmBundle.kt:47-51` 每次 `resolve()` 都会把 `data.ext4` 扩到 512 MB（真机上实测被撑满 512 MB），但 `QemuRunner.buildCommand()` 只把 `b.rootfs` 加进 `-drive`，`dataImage` 从未出现在命令行里。

在 `/data` 只剩 2 GB 的手机上，这等于**每次启动 VM 白扔掉 512 MB**（本次测试已删除该文件）。

**修复**：要么把 `data.ext4` 作为第二个 `-drive`（`/dev/vdb`）真正用起来，要么在 `resolve()` 里删掉这段创建逻辑。

### B7（P2）下载失败后 UI 状态不刷新、进度文案错乱

- 下载失败后按钮已复位，但进度行残留 `qemu-bundle.tar.gz: 0 / 0 KB`（实际 `QemuBinaries.downloadFrom` 根本没跑）；
- 下载过程中显示 `Image: 0 / 0 KB`，此时其实在下载 rootfs；
- 手工把 `Image` + `rootfs.squashfs` 放进 `files/vm-bundle/0.1.0/` 后，设置页仍显示"未就绪"，**必须强杀重启 App 才刷新**（`bundleReady` 用 `remember(refreshTick, downloading)`，失败路径不递增 `refreshTick`）。

**修复**：失败时清空 `downloadLine`；进度文案用实际 `p.file`；在 `onResume`/`LaunchedEffect` 里重算 `bundleReady`。

---

## 五、已验证可用的部分（可以直接放心的）

1. **QEMU 二进制与依赖库**：72 个 `.so` + `libqemu-system-aarch64.so`（31 MB）经 `useLegacyPackaging=true` 全部解到 `nativeLibraryDir`，在 app 的 `untrusted_app` SELinux 域内可直接 exec 并完成动态链接。
2. **TCG 在 MT6853T 上可用**：`-machine virt -cpu max -accel tcg,thread=multi -smp 4 -m 1024` 初始化成功，QMP 报 4 个 `max-arm-cpu`。
3. **QMP 通道**：`unix:…/qmp.sock,server=on,wait=off` 工作正常，`query-status` / `info cpus` / `info registers` / `quit` 均可用于诊断（本次就是靠它定位 B5）。
4. **guest 内核 6.12.0 能起来**：squashfs 挂载、devtmpfs、virtio-blk、virtio-net 全部 OK；`Docklet` 的 `BootStage` 标记机制与 `startvm` 脚本设计合理。
5. **guest 镜像内容完整**：`/usr/bin/podman`（含 3 个 podman 组件）存在，`crun` 在，Alpine 3.20 基础齐全。
6. **UI/服务层**：Compose 六页导航、空态文案、错误红点、FGS + `vm_status` 通知通道、WakeLock 声明均正常，无崩溃、无 ANR。
7. **资源占用**（VM 未运行、App 前台）：`TOTAL PSS 134 MB / TOTAL RSS 243 MB`（Native Heap 22.8 MB）。QEMU 运行 2 vCPU/1 GB guest 时：QEMU 进程 RSS ≈ 210–230 MB，App 进程 RSS ≈ 250 MB。

---

## 六、复现步骤（下次会话可直接照抄）

```bash
export PATH="/f/android-build-tools/android-sdk/platform-tools:$PATH"
export MSYS_NO_PATHCONV=1                 # 否则 adb 收不到 /f/... 本地路径
A="adb -s 192.168.1.11:5555"
adb connect 192.168.1.11:5555             # 每次新 shell 都要重连（daemon 会被回收）

# 1) 安装（先确认 /data 有 ≥1.5 GB 空闲）
$A install -r -t "F:/exeliang/Docker-roid/app/build/outputs/apk/debug/app-debug.apk"

# 2) 冷启动 + 截图
$A shell "am force-stop com.midsancode.docklet; am start -W -n com.midsancode.docklet/.MainActivity"
$A shell "screencap -p /sdcard/s.png"; $A pull /sdcard/s.png "F:/tmp/s.png"

# 3) 手工塞底座（绕过 404 的下载器）
$A push rootfs.squashfs /data/local/tmp/rootfs.squashfs
$A shell "chmod 644 /data/local/tmp/rootfs.squashfs"
$A shell "run-as com.midsancode.docklet sh -c 'cat /data/local/tmp/rootfs.squashfs > files/vm-bundle/0.1.0/rootfs.squashfs'"

# 4) 以正确参数手工起 QEMU（绕开 B2/B3），串口落文件
$A shell "run-as com.midsancode.docklet sh -c 'sh files/manual-run.sh > files/manual.out 2>&1 &'"
$A shell "run-as com.midsancode.docklet sh -c 'tail -c 900 files/serial.log'"

# 5) QMP 诊断（停 VM / 看寄存器 / 看状态）
$A shell "run-as com.midsancode.docklet sh -c 'printf \"{\\\"execute\\\":\\\"qmp_capabilities\\\"}\n{\\\"execute\\\":\\\"quit\\\"}\n\" | nc -U -w 3 files/vm-bundle/0.1.0/qmp.sock'"
```

关键坑位备忘：

- `adb shell` 里 `run-as` 可用（两个包都是 debuggable），这是唯一能读写 app 私有目录、并能直接读 `qmp.sock` 的通道；
- `pkill -f libqemu-system-aarch64` 会把自己所在的 `run-as sh -c "…libqemu-system-aarch64…"` 一起杀掉（命令行含同名字符串），要用 `pidof` 拿 pid 再 `kill`；
- 设备 `grep` 是 toybox，不支持 `-wE`；`curl` 走本机代理需 `--noproxy "*"`。

---

## 七、修复优先级建议

1. **B1 + B2 + B3 一起改**（都在 VM 启动链路上，改完才能真机跑通）：
   - `BundleDownloader` 下 `rootfs.squashfs`；
   - `QemuRunner.buildCommand` 按 rootfs 后缀决定 `rootfstype` 与 `ro/rw`；


   - `init=` 与镜像版本绑定（镜像里没有 `startvm` 就别写它，或发布带 `startvm` 的新镜像）。
2. **B6**：删掉无用的 `data.ext4` 预分配（对 99% 满的手机是硬伤）。
3. **B7**：UI 状态刷新与进度文案。
4. **B4/B5**：如果还要保留 OpenRC 路线，补齐 `/etc/init.d/dev` + 提前挂 `/var`、`/run` tmpfs；否则彻底走 `init=startvm`。

---

## 八、本次测试对设备的改动（已还原）

| 项 | 状态 |
|---|---|
| 安装 `com.midsancode.docklet` v0.1.0 | **保留**（便于下次直接复测） |
| `files/vm-bundle/0.1.0/` 内 `Image` + `rootfs.squashfs` | **保留**（88 MB，方便复测） |
| `files/vm-bundle/0.1.0/data.ext4`（512 MB，未使用） | 已删除 |
| 临时脚本 / 串口日志 / `/data/local/tmp/docklet`（160 MB）/ `/sdcard` 截图 | 已清理 |
| 历史包 `com.dockerroid` | 未改动（其 QEMU 已通过 QMP `quit` 正常停止） |
| `/data` 空闲 | 500 MB → **2.0 GB** |

---

## 九、证据文件

| 文件 | 内容 |
|---|---|
| `shots/01-new-app-home.png` | 新版主页：底座未就绪提示 |
| `shots/02-bundle-download-failed.png` | 底座下载失败后的设置页（残留 0/0 KB 进度） |
| `shots/03-kernel-panic-ext4.png` | B2 现场：`VFS: Unable to mount root fs` + 错误红点 |
| `shots/04-containers-tab.png` / `05-images-tab.png` / `06-terminal-tab.png` | 引擎未运行时的空态表现 |
| `shots/07-old-bundle-boot.png` | 旧包 guest 内核启动到 OpenRC |
| `shots/08-old-bundle-stuck.png` | B5 现场：卡在 `Caching service dependencies ... [ ok ]` |
| `evidence/guest-console-probe.txt` | guest 内 `init=/bin/sh` 探针原始输出（`/usr/local/bin` 为空、`/usr/bin/podman` 存在） |
