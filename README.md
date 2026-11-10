# Docker-roid

在 Android 上跑容器（均衡档 VM 方案）的原生 App 骨架。

> 完整设计与取舍见 [DESIGN.md](DESIGN.md)。

## 当前阶段：P1 骨架（VM 生命周期 + 状态 + 基础 GUI 导航）

已完成（可编译运行、可演示状态流转）：
- Gradle 工程结构、版本目录、ProGuard、Manifest（前台服务/WakeLock 权限）
- `VmManager`：全局状态单例（`StateFlow<VmStatus>` + 串口日志缓冲）
- `VmService`：前台服务 + WakeLock + 常驻通知，托管 QEMU 生命周期
- `QemuRunner`：拉起 `qemu-system-aarch64`（virt/TCG），读取 stdio 控制台日志
- `VmBundle` / `QemuBinaries`：底座路径解析、QEMU 二进制从 assets 解出
- Compose UI：底部导航（主页 / 容器 / 镜像 / 终端 / 设置），主页展示 VM 状态与启动日志

尚未实现（后续阶段）：
- Docker REST 客户端（P1 后半 / P2）
- 容器 / 镜像 / 网络 / 卷 的完整管理界面（P2/P3）
- Termux 真终端（P4）
- 端口转发 QMP 动态规则（P3）
- AVF 后端、底座下载、多语言、诊断导出（P4）

## 目录

```
app/src/main/java/com/dockerroid/
├── DockerDroidApp.kt          Application（Koin 初始化）
├── MainActivity.kt            Compose 入口
├── di/AppModule.kt            Koin 模块
├── data/
│   ├── vm/
│   │   ├── VmStatus.kt        VM 状态 + BootStage 解析
│   │   ├── VmManager.kt       全局状态单例
│   │   ├── QemuRunner.kt      QEMU 进程管理
│   │   ├── VmBundle.kt        底座路径解析
│   │   └── QemuBinaries.kt    assets 二进制解出
│   └── docker/
│       └── DockerEngineFactory.kt  Docker REST 客户端工厂
├── service/VmService.kt       前台服务
└── ui/
    ├── DockerDroidRoot.kt     底部导航
    ├── theme/Theme.kt
    └── screens/               Home / Containers / Images / Terminal / Settings
```

## 构建

需要 Android SDK 35、JDK 17、NDK（后续编译 QEMU/terminal 时）。

```bash
# 需先生成 gradle wrapper（首次）：gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

> 注：`app/src/main/assets/qemu-system-aarch64`（裁剪版 QEMU）与 `filesDir/vm-bundle/0.1.0/{Image,rootfs.squashfs}`
> 需由 `build-qemu` / `build-kernel` / `build-rootfs` 脚本产出后放入。P1 阶段 UI 会提示"底座未就绪"。

## 后端约定

- Docker/Podman socket 经 SLIRP 暴露到宿主 `127.0.0.1:2375`（REST）。
- 串口启动标记（guest 侧打印，见 `BootStage`）：
  ```
  docker-roid: kernel up
  docker-roid: bootstrap done
  docker-roid: network up
  docker-roid: engine starting
  docker-roid: READY
  ```
