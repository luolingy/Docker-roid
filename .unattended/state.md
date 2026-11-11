# 账本 — Docker-roid

## 目标
Android 原生 App，在无 root 的 Android 上通过均衡档 VM 跑真实容器，并提供图形化管理（容器/镜像/网络/卷/终端）。

## DoD 验证结果（2026-11-10，全部达成 ✅）
1. ✅ DockerApi 覆盖 containers/images/networks/volumes/system/events，含流式 logs（multiplexed 帧解析）/stats/pull
2. ✅ EngineRepository → StateFlow → Compose UI，全部 IO 线程，无阻塞调用
3. ✅ Containers/Images/Resources/RunDialog/ContainerDetail 全部接真实 REST
4. ✅ `:app:assembleDebug` BUILD SUCCESSFUL（35 tasks, exit 0），app-debug.apk = 18.23MB
5. ✅ `:app:testDebugUnitTest` BUILD SUCCESSFUL — BootStageTest 5/5（failures=0, errors=0）
   另 verify.ps1 结构验收 35/35 PASS

## 已完成
- VM 基座 GHA 构建闭环 ✅：kernel Image 41MB + rootfs.squashfs 29.3MB 已发布到 Release v0.1.0
  （调试修 5 问题：Alpine URL 版本剥错/chroot sudo/trace 定位/permissions write/GPG 签名→本地关闭）
- P4前段补充：可编辑设置页（内存/盘滑条+DNS+自启，DataStore）+ PortForwardDialog(QMP 动态转发) + VmBundle 读设置 + QemuRunner 传 DNS
- 真机测试（Redmi Note 9 4G/Android 12）：安装✅ 启动✅ UI✅ 诊断导出✅ 修 16KB 页检测 bug✅
- MIRROR_BASE 已指向 GitHub Releases latest/download
- P0 设计文档 DESIGN.md（三路线对比/架构/瘦身策略）
- P1 骨架：Gradle 结构、Manifest、VmManager/VmService/QemuRunner/VmBundle/QemuBinaries、串口状态机、底部导航+Home
- 构建脚手架：build-qemu / build-kernel(+裁剪config) / build-rootfs(+guest init 标记脚本)
- P2: DockerApi + EngineRepository(轮询+事件流+StateFlow) + Containers/Images 真实界面 + Snackbar
- P3: ResourcesScreen(网络/卷) + ContainerDetailScreen(日志/stats/inspect) + RunDialog(docker run 图形化)
- P4前段: SettingsRepository(DataStore) + SettingsScreen(诊断导出+底座下载) + QmpClient(动态端口转发) + BundleDownloader + Diagnostics + QEMU stdin 串口终端 + 事件流即时刷新
- 验收: gradlew wrapper、单测、verify.ps1；修复编译错误 3 轮(IconButton/类型推断/LocalSocket/Compose 参数)

## 进行中
-（无 —— DoD 全部达成）

## 阻塞
（无）

## 假设（A 档自决记账）
- 引擎 API 走 http://127.0.0.1:2375（SLIRP 转发 Podman system service），前缀 /v1.41
- kotlinx.serialization + OkHttp 手写轻客户端（不引入 docker-java）
- 单 Activity + Compose + Koin；错误统一 Snackbar，不抛 UI
- Gradle 依赖走阿里/腾讯镜像（services.gradle.org 直连不稳）
- C:\Users\Administrator\.gradle 无写权限 → GRADLE_USER_HOME 指 F 盘（关键！）
- FAB enabled 参数 m3 该版本不可用 → onClick 内条件判断代替

## 待决（F 档，均有默认不阻塞）
- MIRROR_BASE = "https://mirror.example.com/..." 占位，需换真实底座镜像源
- AVF/pKVM 后端未实现（均衡档默认 QEMU TCG）

## 下一步（待人，一起测试时）
1. 等 GHA 编出 qemu-system-aarch64 → Release 补上该资产
2. 手机重装 APK → 设置点"下载底座"（会同时下 qemu）→ 启动 VM
3. 看 BootStage 走到 READY → 终端 `podman run hello-world`
4. 若无线调试端口变化：重新 adb connect IP:PORT

## 真机测试记录（2026-11-11，无线调试 192.168.1.11:41809）
- ✅ 配对+连接成功；APK 安装启动无崩溃
- ✅ **应用内下载底座真实工作**：Image 43MB + rootfs 30.7MB 下载完成，大小与 Release 一致，UI 正确翻"已就绪"
- ✅ 启动 VM 链路：UI→VmService→Settings→VmBundle→QemuRunner 全跑通
- ❌ 卡在最后一步：APK 未含 qemu-system-aarch64（预期，待 GHA 编好后可下载）
- 错误 UI 反馈正确：显示"错误：qemu-system-aarch64"
- 修复 GHA QEMU job 4 轮问题：NDK 版本不存在 → JDK11 不满足 sdkmanager(需17) → setup-android action 自身 bug → 改用预装 SDK+手动 sdkmanager

## 验收环境（F 盘，勿删）
- F:\android-build-tools\gradle-8.9\ + gradle-home\（GRADLE_USER_HOME 必须指 F 盘）
- F:\android-build-tools\android-sdk\（platform-35 + build-tools 35.0.0 + platform-tools，许可已全部接受）
- JAVA_HOME=E:\JavaRunTime\jdk21 (JDK 21.0.4)
- 编译：`$env:GRADLE_USER_HOME='F:\android-build-tools\gradle-home'; $env:JAVA_HOME='E:\JavaRunTime\jdk21'; .\gradlew.bat :app:assembleDebug --no-daemon`
- 构建/测试日志：F:\android-build-tools\build-log-*.txt / test-log-*.txt

## 2026-11-11 下午进展
- ✅ QEMU 方案定案：NDK 交叉编译不可行（QEMU 硬依赖 glib，bionic 无）→ 改用 Termux runtime bundle（bin+32依赖so）32.9MB
- ✅ Release v0.2.0 发布：Image 43MB + rootfs 30.7MB + qemu-bundle.tar.gz 32.9MB
- ✅ App 新增：TarExtractor(纯Kotlin tar解压) + QemuBinaries下载解压 + QemuRunner 设 LD_LIBRARY_PATH/PREFIX/QEMU_DATADIR
- ✅ 新功能：容器暂停/恢复/重命名/提交镜像；Home 系统信息卡(版本/容器/镜像/镜像占用)+prune；ContainerDetail 操作栏
- ⚠️ 未真机复测（用户要求测试前许可）；编译已绿(BUILD SUCCESSFUL)
- 待测清单：装APK→下载底座(约107MB)→启动VM→看READY→podman run hello-world

## 2026-11-12 真机联调进展（已暂停，继续写功能）
- ✅ QEMU 在真机跑通：QEMU 11.1.3（Termux runtime + patchelf 重写 SONAME/NEEDED，70个库全部纳入 jniLibs）
- ✅ 库闭包完整：libp11-kit/libelf/libargp/libdw/libzstd 等深层依赖全解决
- ✅ jniLibs 方案：nativeLibraryDir exec（Android 唯一可 exec 位置），APK 41MB
- ✅ rom 数据：qemu-share(efi-*.rom 7个1.1MB) 打进 assets，首启解压到 filesDir/qemu/share/qemu
- ⏳ 待测：完整 VM boot 到 READY（刚装含 rom 的 APK，下一步验证）
- 已知待修：下载进度 UI 在 chunked 响应下显示 0/0（加速器无 Content-Length）
- 镜像加速已加：ghfast.top + gh-proxy.com + 直链，自动回退
