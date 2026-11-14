# Docklet

在 Android 上跑容器。原生 App + QEMU 虚拟机 + Podman，arm64 与 amd64 镜像都能跑，通过标准 Docker REST API（`127.0.0.1:2375`）对外暴露。

> 完整设计与取舍见 [DESIGN.md](DESIGN.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

## 当前版本：v0.5.0

- **容器运行时**：podman 5.2.5 + crun 1.17，跑在 Alpine 3.20 guest 里；arm64 原生、amd64 经 binfmt + `qemu-x86_64` 透明转译。
- **Docker REST 全通**：`/version`、`/containers`、`/images`、`/volumes`、`/events`、`/exec`、`/build` 均可从宿主直连。
- **界面**：主页 / 容器 / 镜像 / 资源 / 终端 / 设置六页；容器详情有日志流、资源占用、inspect 摘要、容器内执行。
- **多语言**：简体中文 / English，切换即时生效。
- **主题色**：蓝 / 青 / 紫 / 绿 / 橙五套强调色。
- **更新检测**：读 GitHub Releases 最新 tag 做语义化比较。
- **崩溃自重启 + 后台保活**：崩溃后自动拉起（带防循环节流），划掉最近任务不停容器，支持开机自启。

## 架构

```
App (Kotlin/Compose)
  └─ 前台服务 VmService ── WakeLock ── QEMU 进程 (TCG, virt)
                                        ├─ /dev/vda  rootfs.squashfs（只读根）
                                        ├─ /dev/vdb  data.ext4（镜像与容器）
                                        └─ 串口 stdio ── 启动阶段标记
                                              │
                          SLIRP 10.0.2.15 ────┘
                                 │
                     podman system service tcp://0.0.0.0:2375
                                 │
                     hostfwd 127.0.0.1:2375 ← App / 其他 App
```

启动链路：`-kernel Image -initrd initrd.img -append "rdinit=/init"` → initramfs 挂 squashfs、tmpfs 覆盖 `/var` `/run` `/tmp` 与整个 `/etc`、ext4 挂 `/var/lib/containers` → `chroot /mnt /run/busybox sh /run/docklet-boot.sh`（PID 1）→ cgroup2 / DNS / SLIRP 网络 / binfmt / storage.conf → `podman system service` → 轮询 `/proc/net/tcp` 等 2375 进入 LISTEN 才打印 `Docklet: READY`。

## 目录

```
app/src/main/java/com/midsancode/docklet/
├── DockletApp.kt              Application（Koin + CrashHandler）
├── MainActivity.kt            语言包装 + 主题注入 + 崩溃恢复提示
├── crash/CrashHandler.kt      全局异常捕获 + AlarmManager 重启 + 崩溃日志
├── i18n/LocaleHelper.kt       attachBaseContext 语言覆盖
├── di/AppModule.kt            Koin 模块
├── data/
│   ├── prefs/Prefs.kt         必须同步读的开关（语言 / 保活 / 崩溃节流）
│   ├── settings/              DataStore 配置（内存 / 磁盘 / 网络 / 主题色 / 自启）
│   ├── update/UpdateChecker.kt GitHub Releases 版本比较
│   ├── vm/                    VmStatus / VmManager / QemuRunner / VmBundle / QemuBinaries / QmpClient
│   ├── docker/                DockerApi / EngineRepository / 模型
│   └── diag/Diagnostics.kt    串口日志 + QEMU 命令行 + 设备信息导出
├── service/
│   ├── VmService.kt           前台服务（START_STICKY + 异常自动重拉 + onTaskRemoved 保活）
│   └── BootReceiver.kt        开机自启
└── ui/
    ├── DockletRoot.kt         底部导航 + 容器详情路由
    ├── theme/Theme.kt         5 套 BrandPalette + 深浅色
    ├── components/DockletUi.kt 统一组件库
    └── screens/               Home / Containers / ContainerDetail / Images / Resources / Terminal / Settings
```

## 构建

需要 Android SDK 35、JDK 17。

```bash
./gradlew :app:assembleDebug     # debug
./gradlew :app:assembleRelease   # 签名 release（需 keystore.properties，缺失回退 debug 签名）
```

> 底座（内核 `Image`、`rootfs.squashfs`、`qemu-bundle.tar.gz`，约 88 MB）不随 APK 打包，首次启动按需从 GitHub Release 下载。

## 后端约定

- Docker/Podman socket 经 SLIRP + hostfwd 暴露到宿主 `127.0.0.1:2375`（REST）。
- 串口启动标记（guest 侧打印，见 `BootStage`，与 App 内 `boot_*` 文案一一对应）：
  ```
  Docklet: kernel up
  Docklet: bootstrap done
  Docklet: network up
  Docklet: engine starting
  Docklet: READY
  ```
- 容器网络：已发布内核没有 bridge 支持（`RTNETLINK: Not supported`），默认 `netns = "host"`。

## 已知限制

- 容器独立网络需要重编内核打开 `CONFIG_BRIDGE`。
- 触达网络后端的 REST 路由（`/version`、`/networks`、`/system/df`、`/info`）在 TCG 下极慢（数十秒），非死锁。
- `docker.io` 在国内常被 DNS 污染，内置 `docker.m.daocloud.io` → `docker.1panel.live` → `hub.rat.dev` 三级回退。
