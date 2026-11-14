# Podroid 项目长期笔记

> v0.6.0 起项目由 Docklet 更名为 **Podroid**（包名 `com.midsancode.podroid`，仓库 `luolingy/Podroid`）。
> 注意与第三方项目 `ExTV/Podroid`（Android 播客客户端）同名，讨论时需区分。

## 工程事实
- 包名 `com.midsancode.podroid`（源码目录 `app/src/main/java/com/midsancode/podroid/`），minSdk 26 / target 35，仅 arm64。
- 设备上历史包 `com.dockerroid`（label "Docker-roid"）与 `com.midsancode.docklet` 仍装着，都是旧版；均 debuggable，可用 `run-as`。
- QEMU 以 `libqemu-system-aarch64.so` 形式打进 jniLibs，靠 `useLegacyPackaging=true` 解到 nativeLibraryDir 才能 exec；`assets/qemu-share/*.rom` 需 `-L files/qemu/share/qemu`。
- 底座来源：GitHub Release `luolingy/Podroid`（v0.4.2 资产：`Image` 43.5 MB、`rootfs.squashfs` 44.9 MB、`qemu-bundle.tar.gz` 32.9 MB），下载走 `ghfast.top` / `gh-proxy.com` 加速前缀。**仓库改名后旧 release 与旧 URL 重定向都保留**，不必重传资产。
- guest 设计：Alpine 3.20 + podman 5.2.5 + crun 1.17。

## v0.5.0 之后的工程事实（2026-11-14）
- 版本 `0.5.0 (5)`；release 签名 `podroid-release.jks` + `keystore.properties`（均 gitignore，缺失回退 debug）。同签名可直接 `install -r` 覆盖，底座与容器数据保留。
- **JKS 内部别名不随文件名变**：改名后必须 `keytool -changealias -alias docklet -destalias podroid` + `keytool -storepasswd`，否则签名失败。证书 SHA-256 不变。
- **`VmBundle.BUNDLE_VERSION = "0.1.0"` 是常量**，与 App 版本号无关 —— 升级 App 不会强制重下 88 MB 底座。
- i18n：`values/` 英文（默认）、`values-zh/` 中文，315 个 key，两边必须对齐。语言值存 SharedPreferences（`Prefs`），因为 `attachBaseContext` 需要同步读。
- **取文案必须用 `LocaleHelper.wrap(context)` 包过的 context**：Application / Service 的 configuration 跟随系统语言，应用内语言覆盖只套在 Activity 上。`VmManager`、`VmService` 已按此改。
- 主题色：`BrandPalette`（blue/teal/purple/green/orange），只换 primary/tertiary 系；`PodroidTheme(palette=)` 由 `MainActivity` 从 `settings.themeColor` 注入。
- **明暗模式三态** `system/light/dark` 存 DataStore（`themeMode`），`MainActivity` 解析成 bool 传 `PodroidTheme(darkTheme=)`。语义色必须读 **`LocalDarkTheme`** 而不是 `isSystemInDarkTheme()`——用户手动选深色而系统是浅色时后者会返回浅色版。`values-night/` 放冷启动窗口底色。
- 底栏 6 个页签在 393dp 屏每项约 65dp、可写宽约 43dp；`NavLabel` 用 `TextMeasurer` 自动降字号（11→8sp）。

## release 构建（这台机器必看）
- **不要写 `-keep class androidx.compose.** { *; }`**：seeds.txt 会从 779 KB 涨到 14 MB（icons-extended 上千图标），R8 跑 38 分钟出不了包。Compose 各库自带 consumer rules。
- 本机 7.9 GB 内存、常剩 0.2 GB。`gradle.jvmargs=-Xmx2560m` + `kotlin.daemon.jvmargs=-Xmx1536m` + `org.gradle.workers.max=2` 是上限。
- 即使如此 R8 仍会在 **optimizer 阶段**被系统杀掉（**无 hs_err、进程凭空消失**，daemon 日志停在 `ExecuteBuild`）。已加 **`-dontoptimize`** 规避：保留 tree-shaking + 混淆，跳过优化 pass。换内存宽裕的机器可以删掉。
- 完整 release 构建约 **10~15 分钟**（`-Pkotlin.incremental=false`）。


## 启动链（2026-11-13 第二轮已定型）
`-kernel Image -initrd initrd.img -append "rdinit=/init ..."`，`/dev/vda`=rootfs(squashfs 只读)、`/dev/vdb`=data.ext4。
initramfs 由 `.work/build-initramfs.py` 生成到 `app/src/main/assets/initrd.img`（1.47 MB，含 Alpine 静态 busybox + mke2fs + musl 依赖）。
**资产名不能带 `.gz`**：AAPT2 会把 `.gz` 资产自动解压并去掉扩展名，`assets.open("x.gz")` 直接 FileNotFound。另需 `noCompress += "img"` 才能用 `openFd()` 拿长度做幂等判断。
`/init` 挂 squashfs、tmpfs 覆盖 /var /run /tmp、ext4 挂 /var/lib/containers（失败则 mke2fs）、整个 /etc 换 tmpfs，然后 `chroot /mnt /run/busybox sh /run/podroid-boot.sh`。
`boot.sh` 是 PID 1：cgroup2 → resolv.conf → SLIRP 网络(10.0.2.15/24 gw 10.0.2.2) → binfmt → storage/registries/containers.conf → `podman system service --time=0 tcp://0.0.0.0:2375` → 轮询 `/proc/net/tcp` 2375 state=0A 才打印 `Podroid: READY`。
QEMU 命令行参数前缀也一并改名：`podroid.dns/ram/disk/rootfs/network`，virtio-serial 端口名 `Podroid.hostd`。
串口标记与 `VmStatus.kt` 的 `BootStage` 一一对应，改文案必须同步两边。

## 改 initramfs 的 6 条铁律（都踩过）
1. 脚本行尾必须 LF（CRLF 会让 shebang 参数变成 `sh\r` → `applet not found` → panic）。build 脚本已强制归一化。
2. 用 `chroot` 不用 `switch_root`（后者 bind 新根会遮住子挂载和 initramfs 的 /bin）。
3. binfmt 注册必须写字面转义：`printf '%s\n' ':qemu-x86_64:M::\x7fELF...'`；写成 `printf '\x7f...'` 会让 `\x00` 变成真实 NUL，内核截断注册串，注册出匹配所有 ELF 的坏规则，**之后所有 exec 报 ELOOP**。
4. 只读根上要写 resolv.conf/hosts，必须把整个 `/etc` 换 tmpfs（只覆盖 /etc/containers 不够）。
5. `resolv.conf` 每行要有 `nameserver ` 前缀，裸 IP 会被忽略。首行放 `10.0.2.3`（SLIRP 内建，转发到手机自身 DNS）。
6. `read` 是 shell 内建，不能写 `$BB read`。`eval` 要放子 shell，避免 `exit` 弄死 PID 1。

## 环境限制（真机）
- **桥接网络不可用**：内核 `ip link add type bridge` → `RTNETLINK answers: Not supported`；netavark 报 `create bridge: Netlink error: Not supported (95)`。仓库 `docker-roid.config` 虽写 `CONFIG_BRIDGE=y`，但已发布 Image 显然不是这份 config 编译的。绕过：`/etc/containers/containers.conf` → `[containers] netns = "host"`（不是 `[network] default_network`）。
- **同根因的连带症状（BUG-9）**：凡是会触达网络后端（netlink）的 REST 路由都极慢 —— `/version` 34s(刚开机)→86s(跑久)、`/networks` >45s、`/system/df` >45s、`/info` >150s；不触达的 `/volumes` 0.28s、`/images/json` 5~12s、`/containers/json` 秒回。guest 内 `podman network ls` real 16.5s 但 CPU 仅 4.6s（在等）。默认网络禁止删除（`default network podman cannot be removed`）。**别再把它当死锁/内存/TCG 慢来查**，都排除过了。
- **docker.io DNS 污染**：registry-1.docker.io 解析到境外错误 IP。registries.conf 内置镜像回退 `docker.m.daocloud.io` → `docker.1panel.live` → `hub.rat.dev`，可用 `docklet.mirror=` 覆盖。
- **App UID 出网受待机分组限制**：后台运行时 u0_a958 出网被拦（shell UID 正常）。测试需 `am set-standby-bucket <pkg> active` + `cmd deviceidle whitelist +<pkg>`；产品上靠前台服务。
- 冷启动 podman 要 20~30 秒（TCG）才开始监听，就绪判据必须看监听状态而非进程存活。
- adb server 每次 Bash 调用都会重启 → forward 丢失；`adb forward` 与 `curl` 必须同一次调用内完成。

## 真机测试环境
- Redmi Note 9 5G（cannon，MT6853T），Android 12 / API 31，7.4 GB RAM，4 KB 页，无 root，SELinux enforcing。
- adb：`192.168.1.11:5555`（LAN 192.168.0.102）；adb.exe 在 `F:/android-build-tools/android-sdk/platform-tools/`。
- 设备 `/data` 108 GB 常年 98~99% 满：装 APK 需 ≥1.4 GB 空闲；MIUI 需先开「USB 安装」否则 `INSTALL_FAILED_USER_RESTRICTED`。

## 测试报告索引
- `docs/reports/2026-11-13-real-device-test/REPORT.md` —— 第一轮：真机 UI/服务巡检 + VM 启动链路 7 个缺陷（B1–B7）
- `docs/reports/2026-11-13-container-runtime/REPORT.md` —— 第二轮：容器运行时打通，arm64/amd64 双架构实测 + 6 个 initramfs bug 排障记录
- `docs/reports/2026-11-14-v0.5.0-release/REPORT.md` —— 第三轮：v0.5.0 发布验证（i18n / 主题色 / 更新检测 / 保活）+ R8 构建踩坑

## 待办
1. ~~点 UI 启动走完整链路实测~~ —— 已完成，且 arm64/amd64 双架构在 App 起的 VM 内都跑通（用例 19–23）。
2. 若需容器独立网络，重编内核打开 bridge（并查清现有 Image 的编译配置来源）。**这也是 BUG-9 的唯一根治办法**。
3. 主页降级加载：已完成。
4. 把镜像站/DNS 暴露到设置页（已完成）；补容器/镜像管理 UI（部分完成）。
5. 启动前做 /data 空间预检。
6. 崩溃自重启需要真机触发验证（当前无崩溃入口，只做了代码审查）。
7. `EngineRepository` / `DockerApi` / `Diagnostics` 里仍有中文错误串未纳入 i18n。
8. `VmService.appendSerial` 的 `[host] 阶段：` 前缀仍是中文。

