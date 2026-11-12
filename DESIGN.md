# Docklet — 在 Android 上实现 Docker 常用功能的原生应用

> 目标：一个 Android 原生 App，让你在手机上完成 `docker run / ps / images / pull / exec / logs / network / port / volume` 等绝大部分日常操作，且**不需要 root**。

---

## 0. 先说结论：有没有现成的项目？

**有，而且不止一个。** 在动手写之前，这一节必须先讲清楚——否则你很可能会重复造一个已经存在、且做得相当成熟的车轮。

| 项目 | 形态 | 能否跑真 Docker/Podman | 说明 |
|---|---|---|---|
| **[ExTV/Podroid](https://github.com/ExTV/Podroid)** ⭐3.8k, GPL-2.0 | **原生 Android App**，内置 Alpine VM | ✅ Podman + Docker + LXC，**开箱即用** | 目前最接近你需求的项目。真虚拟机（QEMU TCG 或 AVF/pKVM 硬件加速），内置终端、X11、端口转发、SSH、容器备份。 |
| [oslook/docker-on-android](https://github.com/oslook/docker-on-android) ⭐1 | APK 打包 | ✅ 可 `docker run hello-world` | 极简/早期原型，几乎无界面，仅证明可行。 |
| [Nwokike/proot-distro](https://github.com/Nwokike/proot-distro) ⭐3 | Flet(Python) 套壳 | ⚠️ 只能跑 OCI 镜像，靠 proot，非完整 Docker 守护进程 | 用 proot-distro 在无 root 下跑发行版，容器能力受限。 |
| Termux + proot-distro + `podman` | 命令行（非专门 App） | ⚠️ 需大量手动配置，proot 下网络/存储/性能问题多 | 事实上的"民间方案"，体验差。 |
| UserLAnd / Andronix | 原生 App | ❌ 只跑 Linux 发行版，不含容器引擎 | 目标不同。 |

### 结论
- **要"直接拿来用"** → 用 **Podroid**。它已经实现了你想做的东西，而且是真 VM、真 Podman。
- **要"自己写一个原生的、可定制的 Docklet"** → 完全可以，但核心技术路线应该是 **复用 Podroid 证明可行的那套架构**（VM + Alpine + 容器引擎），而不是从零发明。下面就是这条路线。

> 换句话说：这个项目的价值不在于"能不能做"（能，Podroid 证明过了），而在于**你做一个自己的实现/精简版/教学版**，或者针对某些需求做差异化（更轻、更聚焦 Docker 而非 Podman、更好的 UI 等）。

---

## 1. 根本约束：为什么不能"直接在 Android 上跑 dockerd"？

Android 是 Linux 内核，但和你想象的 Linux 不一样，这是设计的核心前提：

1. **没有 root**：普通 App 是 unprivileged 进程，不能 `mount`、不能建 network namespace、不能操作 cgroup。
2. **Android 内核裁剪了容器所需能力**：
   - `CONFIG_USER_NS` 多数厂商**关闭**（rootless 容器的基石）。
   - `CONFIG_CGROUP_*`、`CONFIG_NET_NS` 对 unprivileged 进程不可用或受限。
   - SELinux 强制（enforcing），App 域（`untrusted_app`）几乎禁止一切容器操作。
3. **`dockerd` 需要 root + 完整 namespace + cgroup v2**。在无 root 的 Android 用户态下，**不可能**原生启动 dockerd。

因此只有三条技术路线：

| 路线 | 原理 | 能否跑真 Docker | 结论 |
|---|---|---|---|
| **A. 真 VM（推荐）** | App 内跑 QEMU/AVF 启动完整 Linux 内核，在 guest 里跑真 dockerd/Podman | ✅ 完整支持 | **Podroid 的选择**，正确路线 |
| B. proot + 用户态 | ptrace 拦截 syscall 模拟 rootfs | ⚠️ 只能跑部分 OCI 镜像，无真守护进程、无网络隔离 | 体验差，不推荐做主力 |
| C. 远程 Docker client | App 只是"遥控器"，连远程 dockerd | ✅ 但机器不在手机上 | 可作为附加功能，非本需求 |

> **本设计选择路线 A**：App 内嵌一个轻量 Linux 虚拟机，在 guest 里运行真实的 Docker/Podman 引擎。这样 `docker run nginx -p 8080:80` 的行为和服务器上**完全一致**。

---

## 2. 总体架构

```
┌──────────────────────────────────────────────────────────────┐
│                     Android App (Kotlin, Jetpack Compose)      │
│                                                                │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌─────────┐  │
│  │  Containers│  │  Images    │  │  Network   │  │ Terminal│  │
│  │  Screen    │  │  Screen    │  │  & Ports   │  │  (xterm)│  │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └────┬────┘  │
│        └───────────────┴───────────────┴──────────────┘       │
│                          │                                     │
│              ┌───────────▼────────────┐                       │
│              │   Engine Repository     │  ← 统一状态层（Flow）  │
│              │   (Docker API 客户端)    │                       │
│              └───────────┬────────────┘                       │
│                          │  HTTP over vsock / TCP (localhost)   │
│  ┌───────────────────────▼────────────────────────────────┐   │
│  │            VM Manager (Foreground Service)              │   │
│  │  - 启动/停止 QEMU 或 AVF                                │   │
│  │  - WakeLock + 常驻通知                                   │   │
│  │  - 串口日志解析（boot 阶段）                             │   │
│  └───────────────────────┬────────────────────────────────┘   │
│                          │ virtio-console / vsock              │
│  ┌───────────────────────▼────────────────────────────────┐   │
│  │              Guest: Alpine Linux (aarch64)               │   │
│  │   squashfs (只读底座) + ext4 overlay (可写持久层)        │   │
│  │   ┌──────────────────────────────────────────────────┐  │   │
│  │   │ dockerd (或 podman system service)                │  │   │
│  │   │  + containerd + runc/crun                         │  │   │
│  │   │  /var/run/docker.sock                             │  │   │
│  │   └──────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

**关键设计决策**：
- **App 不自己实现容器运行时**。App 是"控制面 + UI"，真正的"数据面"是 guest 里的 Docker Engine。
- **App ↔ 引擎通信走 Docker Engine REST API**（`/var/run/docker.sock`，通过 vsock 或 guest 内 TCP 暴露给 App）。这样 App 端可以用现成的 Docker API 客户端，不用 SSH 解析文本。
- **终端**也要有：复杂操作（`docker build`、`compose`）交给终端，简单高频操作（启停/日志/看状态）用图形化 UI。

---

## 3. Android 侧技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | **Kotlin** | 原生、协程方便 |
| UI | **Jetpack Compose + Material 3** | 现代声明式 UI，Podroid 也用它 |
| 架构 | MVVM + Repository + Flow | 状态单向流，适合实时容器状态 |
| 终端 | **Termux 的 `terminal-emulator` + `terminal-view` 库**（Apache-2.0） | 直接复用成熟的 xterm-256color 实现，省一大块工作量 |
| JNI/原生 | NDK，arm64-v8a，**16KB 页对齐** (`-Wl,-z,max-page-size=16384`) | Android 13+ / 16KB 页设备强制要求 |
| 后台 | **Foreground Service + WakeLock（partial）** | 保活 VM，防被系统杀 |
| 网络 | vsock（AVF）/ virtio-console（QEMU） | 低开销的 guest↔host 通道 |
| 构建 | Gradle + CMake | 编译 QEMU、内核、busybox、terminal JNI |

> 最低支持 **Android 8 (API 26)**，**仅 arm64**（不做 x86/32 位，与 Podroid 一致）。

---

## 4. Guest 侧：轻量 Linux 虚拟机

### 4.1 Hypervisor 后端（二选一）

| 后端 | 说明 | 适用 |
|---|---|---|
| **QEMU (TCG 软件模拟)** | 纯用户态、无需 root、无需特殊权限；用 TCG 翻译执行 ARM64 指令 | **默认**，任何 arm64 Android 8+ |
| **AVF / pKVM（硬件虚拟化）** | Android Virtualization Framework，接近原生速度 | 支持的 Pixel 设备（出厂 pKVM），需一次性 adb 授权 |

> 设计上把两者抽象成同一个 `VmBackend` 接口，guest 镜像完全一样，只有 host 侧不同。这样 QEMU 保底可用，AVF 在支持设备上加速。

### 4.2 文件系统

- **`/dev/vdb` — 只读 squashfs**：完整的 Alpine 系统 + Docker/Podman 二进制，**打包进 APK**，永不修改。升级 = 发新 APK。
- **`/dev/vda` — 可写 ext4 镜像**：overlay 的上层，存放 `apk add` 结果、**容器镜像、容器层、volume、用户数据**。首次启动由用户指定大小（比如 4/8/16 GB）。
- **initramfs** 先启动：挂载两块盘 → `overlayfs` 叠加（squashfs 为 lower，ext4 为 upper）→ **`switch_root`** 进真正的 Alpine root。

> 为什么要 `switch_root` 而不是 `chroot`？——因为 `docker exec -it` 依赖 `setns(MNT)` 进入容器 namespace，`chroot` 会被 namespace 重置，exec 出来的进程看到的是原始内核路径（`/mnt/overlay/proc` 而非 `/proc`）。`switch_root` 重构内核挂载树，任何 namespace fork 都看到干净的 `/`。这是 Podroid 踩过的坑，直接借鉴。

### 4.3 内核（自编译 arm64 guest kernel）

必须打开：`overlayfs`、`cgroups v2`、`netfilter/iptables(nft)`、`veth`、`bridge`、`FUSE`、`tun`（端口转发）、`vsock`、`virtio-*`、`CONFIG_USER_NS`、`CONFIG_NET_NS`、`CONFIG_PID_NS`、`CONFIG_IPC_NS`、`CONFIG_UTS_NS`。`ZRAM` 做压缩 swap。

### 4.4 容器引擎

- **优先 Docker Engine**（符合"实现 Docker 功能"的需求）：guest 里装 `dockerd` + `containerd` + `runc`。
- **或 Podman**（更轻、rootless 更自然，Podroid 就用它；`podman system service` 暴露兼容 Docker 的 API）。
- 两者都暴露 `/var/run/docker.sock`（Podman 用 `podman.socket` 或 Docker 兼容 socket）。

### 4.5 init 与启动编排（OpenRC 或 systemd）

自定义服务按顺序拉起，App 通过解析串口日志追踪阶段：

1. `Docklet-bootstrap`：加载模块、挂 cgroup v2、ZRAM、devpts/shm、sysctl、hostname。
2. `Docklet-network`：eth0 起 `10.0.2.15/24`，设默认路由/DNS（SLIRP 提供 NAT）。
3. `Docklet-engine`：`dockerd` 起，监听 socket。
4. `Docklet-ready`：打印 `Ready!` 标记 → App 解锁终端、更新通知。

---

## 5. App ↔ Guest 通信与网络

### 5.1 控制通道
- QEMU：**virtio-console** 端口传一行制命令（供 `hostd` 等辅助守护用）。
- AVF：**vsock**。
- Docker API 通道：guest 内把 dockerd 的 unix socket 通过 socat 转到 vsock/TCP，App 直接发 REST 请求。

### 5.2 端口转发（让容器暴露给手机和局域网）
- guest 内 `docker run -p 8080:80 nginx` 只监听 guest 的 `10.0.2.15`。
- 需要一个 **host→guest 转发规则**（QEMU 用 `-netdev user,hostfwd=tcp::8080-:8080` 或运行时通过 QMP 动态加；AVF 类似）。
- 提供 CLI/图形命令：`Docklet-forward add 8080 8080 tcp` / `list` / `remove` / `clean`。
- 这样手机浏览器访问 `http://127.0.0.1:8080`，同一局域网设备访问 `http://<手机IP>:8080` 即可看到 nginx。

### 5.3 Guest-to-Android 桥
一个小守护 `Docklet-hostd`：guest 内进程通过 guest 本地 unix socket 发一行请求，App 侧代为执行：
- `Docklet-notify` → 弹 Android 通知（比如"容器构建完成"）。
- `Docklet-forward` → 增删端口规则。

---

## 6. 功能清单（"Docker 大部分常用功能"具体是什么）

App 端做成几个 Tab / 屏幕，映射到 Docker 常用命令：

### 6.1 容器 Containers
| UI 操作 | 对应 Docker | Docker API |
|---|---|---|
| 列表 + 状态灯 | `docker ps -a` | `GET /containers/json?all=1` |
| 启动/停止/重启/暂停 | `docker start/stop/restart/pause` | `POST /containers/{id}/start` 等 |
| 删除 | `docker rm -f` | `DELETE /containers/{id}` |
| 创建/运行 | `docker run`（表单：镜像、端口、卷、env、重启策略） | `POST /containers/create` + `/start` |
| 实时日志 | `docker logs -f` | `GET /containers/{id}/logs?follow=1`（流式） |
| 进入终端 | `docker exec -it sh` | `POST /containers/{id}/exec` + `/exec/{id}/start`（升级为 WebSocket/hijack → 复用终端组件） |
| 统计 | `docker stats` | `GET /containers/{id}/stats?stream=1` |
| 导出/提交 | `docker export` / `commit` | `/export`、`/commit` |

### 6.2 镜像 Images
- 列表 `docker images` → `GET /images/json`
- 拉取 `docker pull`（带进度条）→ `POST /images/create`（流式）
- 删除 `docker rmi` → `DELETE /images/{name}`
- 导入/构建：优先跳终端（`docker build`），或简单 Dockerfile 输入框走 `POST /build`
- 标签 `docker tag`、检查 `inspect`

### 6.3 网络 Networks
- 列表/创建/删除 → `/networks`
- 容器连网 → `/networks/{id}/connect`

### 6.4 卷 Volumes
- 列表/创建/删除 → `/volumes`
- 卷浏览（只读查看文件）可选

### 6.5 其他
- **系统信息** `docker info` / `df`（磁盘占用）→ `/info`、`/system/df`
- **事件流** `docker events` → `GET /events`（实时刷新 UI）
- **Compose**：优先在终端里跑 `docker compose up`；进阶可做 YAML 编辑器
- **镜像仓库登录** `docker login` → `/auth`

### 6.6 终端（兜底 + 高级）
任何上面没覆盖的（`build`、`compose`、`system prune`、脚本），都交给内置 xterm 终端。**这是保证"Docker 大部分功能"全覆盖的关键**——图形 UI 覆盖高频操作，终端覆盖长尾。

---

## 7. 状态与数据流

```
VM boot ──serial log──▶ VmState(Flow)  ──▶ UI 顶部状态条 (Stopped/Booting/Ready)
                                          │
Docker Engine ──REST/events──▶ Repository ──Flow──▶ Compose UI
   /events (SSE)  ──▶ 实时增删改 ──▶ 容器/镜像列表自动刷新
   /stats stream  ──▶ 每容器 CPU/内存小图
```

- 用 `GET /events` 单一长连接驱动 UI 增量刷新，避免轮询。
- 终端 I/O 走 exec hijack 的双向流。
- 全部状态封装在 `Repository`，`ViewModel` 暴露 `StateFlow<UiState>`。

---

## 8. 关键工程难点 & 规避

| 难点 | 规避方案 |
|---|---|
| **无 root 无法原生 dockerd** | 走 VM 路线（本文核心） |
| QEMU TCG **慢** | 默认可用；支持设备用 AVF/pKVM 硬件加速；限制 CPU/内存默认值；ZRAM |
| **系统杀后台进程**（QEMU 被杀） | Foreground Service + partial WakeLock + 常驻通知；必要时引导用户关闭电池优化 |
| **首次启动慢**（生成 SSH host key、解压） | 首次 boot 显示阶段进度；之后秒级 |
| **16KB 页对齐**（Android 13+/新 Pixel） | 所有 native 二进制编译加 `-Wl,-z,max-page-size=16384` |
| **DNS 解析失败**（Android 无 host `/etc/resolv.conf`） | guest 内显式设 DNS（最多两个设备 DNS + 8.8.8.8/1.1.1.1 兜底），不走 SLIRP 的 10.0.2.3 |
| **容器镜像/层占用磁盘大** | ext4 overlay 由用户指定大小；提供 `docker system prune`；监控 `/system/df` |
| **`exec -it` 进 namespace 路径错乱** | 用 `switch_root` 而非 `chroot` |
| **APK 体积**（含内核+QEMU+Alpine） | squashfs 压缩；可选首次启动下载底座而非全打包；分 abi 构建 |
| **Android 版本/SELinux 差异** | 明确最低 API 26、仅 arm64；兼容性矩阵 + 诊断日志导出 |
| **端口转发在 AVF 上不通** | AVF 与 QEMU 各自实现转发；提供 `forward clean` 一键清理 |

---

## 9. 目录结构（建议）

```
Docklet/
├── app/                          # Android 应用 (Kotlin + Compose)
│   ├── src/main/java/.../
│   │   ├── ui/                   # Compose 屏幕：containers/images/network/terminal
│   │   ├── data/
│   │   │   ├── docker/           # Docker Engine REST 客户端 (Ktor/Retrofit)
│   │   │   ├── vm/               # VmManager, VmBackend(Qemu/Avf)
│   │   │   └── repo/             # Repository
│   │   ├── service/              # Foreground VM Service, WakeLock
│   │   └── terminal/             # Termux terminal-view 集成 + exec hijack
│   ├── cpp/                      # JNI: terminal bridge, QEMU 启动器
│   └── src/main/assets/          # boot 镜像、qemu 二进制、squashfs 底座(或按需下载)
├── build-rootfs/                 # 构建 Alpine + dockerd/podman 的 squashfs 镜像
├── build-kernel/                 # arm64 guest kernel 配置与编译
├── build-qemu/                   # 交叉编译 QEMU (arm64, TCG)
├── build-tools/                  # NDK/CMake 工具链
└── docs/
```

---

## 10. 分阶段实施路线

**阶段 0 — 验证可行性（1~2 周）**
- 跑通 "QEMU + Alpine + 手动 `docker run hello-world`"，先不写 UI。

**阶段 1 — 最小可用 App（3~4 周）**
- Foreground Service 启动/停止 VM；串口日志 → 状态条。
- 内置终端；App 内手动 `docker run` 成功。
- Docker API 客户端连上 socket。

**阶段 2 — 图形化高频操作（4~6 周）**
- Containers / Images 列表 + 启停删 + 实时日志 + stats。
- `exec` 进入容器终端。

**阶段 3 — 网络与持久化（3~4 周）**
- 端口转发（hostfwd / QMP 动态）；Networks / Volumes 管理。
- 可写 ext4 持久层 + 大小设置 + prune。

**阶段 4 — 硬件加速与打磨（持续）**
- AVF/pKVM 后端；16KB 对齐；兼容性矩阵；诊断日志；多语言。
- 可选：Compose 支持、镜像仓库登录、容器备份。

---

## 11. 参考与出处

- [ExTV/Podroid](https://github.com/ExTV/Podroid) — 最接近的成熟实现，GPL-2.0，⭐3.8k
- [Podroid — How it works](https://extv.github.io/Podroid/guide/how-it-works.html) — VM/squashfs+ext4 overlay/switch_root/OpenRC/双后端 的官方说明
- [Podroid — Backends: QEMU & AVF](https://extv.github.io/Podroid/guide/backends.html)
- [Podroid — Networking & ports](https://extv.github.io/Podroid/guide/networking.html)
- [oslook/docker-on-android](https://github.com/oslook/docker-on-android) — 早期最小原型
- [Termux](https://github.com/termux/termux-app) — 终端模拟器引擎（JNI 复用）
- [Alpine Linux](https://alpinelinux.org)、[QEMU](https://www.qemu.org)
- Android [Virtualization Framework (AVF/pKVM)](https://source.android.com/docs/core/virtualization)

---

## 12. 一句话建议

> 如果目标是"拥有这个功能"，去装 Podroid；
> 如果目标是"自己做一个原生 Docklet"（学习/定制/教学/差异化），
> 那就照本文第 2~10 节走：**App 做控制面 + UI，后台跑一个 QEMU/AVF 轻量 Linux VM，VM 里跑真 Docker Engine，App 通过 Docker REST API 驱动它**。这条路 Podroid 已经用真实产品验证过可行，你是在它铺好的轨道上换自己的车厢。
