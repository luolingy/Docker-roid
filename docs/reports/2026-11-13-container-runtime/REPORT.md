# Docklet 容器运行时打通报告（2026-11-13 第二轮）

> 设备：Redmi Note 9 5G（cannon / M2007J22C），Android 12（API 31），MT6853T，7.4 GiB RAM，4 KB 页  
> 连接：`adb connect 192.168.1.11:5555`（手机 Wi-Fi 192.168.1.11，宿主机 192.168.0.102）  
> 目标：让 Docklet 在真机上支持**完整的容器功能**，并能跑 **arm64 与 amd64** 两类镜像  
> 结论时间：2026-11-13 16:20 UTC

---

## 一、结论（先看这段）

**目标达成。** 真机上已经可以：拉起 VM → 通过 Docker 兼容 REST API 管理 → 拉取镜像 → 运行容器；arm64 原生镜像与 amd64 跨架构镜像都能跑起来。

| 能力 | 状态 | 证据 |
| --- | --- | --- |
| VM 启动到就绪 | ✅ | 串口依次输出 `kernel up` → `bootstrap done` → `network up` → `storage driver=overlay` → `engine starting` → `READY` |
| Docker 兼容 API | ✅ | `GET /_ping`→`OK`；`/version`→`Podman Engine 5.2.5 linux/arm64/alpine-3.20.10`；`/containers/json`、`/images/json`→`200` |
| 拉取 arm64 镜像 | ✅ | `POST /images/create?fromImage=alpine` → HTTP 200，4.19 MB 层落盘 |
| 运行 arm64 容器 | ✅ | 容器输出 `aarch64` / Alpine `3.24.4`，`ExitCode=0` |
| 拉取 amd64 镜像 | ✅ | `fromImage=busybox&platform=linux/amd64` → 镜像 `Architecture=amd64` |
| 运行 amd64 容器 | ✅ | 容器输出 `amd64-ok`，ELF 头 `e_machine=0x003e`（x86-64），`ExitCode=0` —— x86-64 二进制经 binfmt_misc + qemu-x86_64 真实执行 |
| 持久化存储 | ✅ | `/dev/vdb` ext4 挂在 `/var/lib/containers`，`graphDriverName=overlay`，`Backing Filesystem: extfs` |
| 容器默认网络 | ✅（host） | `[containers] netns="host"` 后，不指定 `NetworkMode` 也能正常 `start`（204） |
| **App 内点「启动 VM」全链路** | ✅ | 装 APK → 点按钮 → QEMU pid 出现 → UI 状态变「就绪」→ 宿主机 `curl /_ping` 返回 OK → 容器输出 `e2e-ok` / `aarch64`，Exit 0 |
| 桥接网络 | ❌ | 内核不支持建 bridge 设备（详见「已知限制 L1」） |

**上一轮报告的 7 个缺陷全部关闭**，其中 3 个 P0 的修法是**用 initramfs 接管启动**，而不是改镜像或改参数去迁就镜像。

---

## 二、guest 侧最难缠的 6 个 bug

按出现顺序记录，每个都附根因与判定依据。这些是下次改动 initramfs 时最容易再踩的坑。

### BUG-1｜`sh: applet not found` → 立刻 `Kernel panic`

- **现象**：`Run /init as init process` 之后仅一行 `sh: applet not found`，随即 `Attempted to kill init! exitcode=0x00007f00`。
- **根因**：`/init` 的 shebang 是 `#!/bin/busybox sh\r\n` —— **CRLF 行尾**。内核解析 shebang 时把解释器参数取成 `sh\r`，busybox 找不到名为 `sh\r` 的 applet，返回 127，PID 1 退出即 panic。
- **修复**：`build-initramfs.py` 打包时强制 `data.replace(b"\r\n", b"\n")`。**不要依赖编辑器/工具链保证行尾**。
- **判定依据**：解包 cpio 直接看 `/init` 前 20 字节，可见 `sh\r\n`。

### BUG-2｜`/bin/busybox: Symbolic link loop`（镜像内的 applet 全废）

- **现象**：`switch_root` 后任何命令都报 `Symbolic link loop`，包括 `/bin/sh`。
- **根因**：`switch_root` 会 bind-mount 新根，把 initramfs 的 `/bin` 一起遮掉；镜像里的 `/bin/busybox` 在那一刻不可用，软链解析成环。
- **修复**：改用 `chroot`（不 bind），并把 initramfs 的**静态 busybox 拷进新根**，用 `chroot /mnt /run/busybox sh /run/docklet-boot.sh` 启动，彻底不碰镜像的 applet 软链。

### BUG-3｜`printf '\x7f...'` 注册 binfmt → **所有 exec 变 ELOOP**

这是本轮最隐蔽的一个。

- **现象**：binfmt 注册「成功」（`/proc/sys/fs/binfmt_misc/qemu-x86_64` 存在），但**紧接着所有 exec 全部失败**，报 `Symbolic link loop`，连 `/run/busybox` 这种普通文件也报。跳过 binfmt 注册就一切正常。
- **根因**：用 `printf ':qemu-x86_64:M::\x7fELF\x02...\x00...'` 时，`printf` 把 `\x00` 解释成**真实 NUL 字节**。内核的 `register` 接口按 C 字符串解析，遇到 NUL 直接截断 → 注册串不完整 → 内核注册出一条 magic 过短、**匹配所有 ELF** 的规则。于是任何 exec 都被判定为 x86-64 并改由 qemu-x86_64 执行，qemu 拒绝 aarch64 ELF，exec 以错误码返回，busybox 打印成 `Symbolic link loop`。
- **修复**：必须写**字面转义序列**，由内核自己 unescape：
  ```sh
  BINFMT_LINE=':qemu-x86_64:M::\x7fELF\x02\x01\x01\x00...\x02\x00\x3e\x00:\xff...\xfe\xff\xff\xff:'"$QX"':F'
  printf '%s\n' "$BINFMT_LINE" > /proc/sys/fs/binfmt_misc/register
  ```
  关键是用 `printf '%s'`（不解释参数里的转义），而不是把转义写进格式串。
- **判定依据**：加 `docklet.nobinfmt=1` 启动，一切恢复正常 → 因果锁定在注册这一步。

### BUG-4｜`/etc` 在只读 squashfs 上，`resolv.conf` 写不进去

- **现象**：拉镜像报 `bad address`，容器内 DNS 全废。
- **根因**：squashfs 只读；只覆盖 `/etc/containers` 不够，`/etc/resolv.conf`、`/etc/hosts` 同样需要可写。
- **修复**：init 里把**整个 `/etc` 换成 tmpfs**：先 `mount --bind /mnt/etc /etcref` 留参照，再 `mount -t tmpfs tmpfs /mnt/etc`，`cp -a /etcref/. /mnt/etc/` 拷回，最后 umount 参照点（参照点放在新根之外，避免被容器看到）。

### BUG-5｜`resolv.conf` 缺 `nameserver ` 前缀

- **现象**：`nslookup: write to '127.0.0.1': Connection refused`。
- **根因**：把 `1.1.1.1,8.8.8.8` 用 `tr ',' '\n'` 直接落盘，得到裸 IP 行。解析器不认，回退到 127.0.0.1。
- **修复**：逐条拼 `nameserver <ip>`。

### BUG-6｜`$BB read -r line` —— `read` 是 shell 内建，不是 applet

- **现象**：终端提示符之后刷 `read: applet not found`，串口交互不可用。
- **根因**：为了规避镜像软链问题，脚本里统一用 `$BB <applet>` 调外部命令；但 `read` 没有对应 applet。
- **修复**：`while read -r line`（内建）。并把 `eval` 放进子 shell `( eval "$line" )`，避免用户敲 `exit` 或单行报错把 PID 1 弄死。

## 三、新启动链（本次架构变更）

```
App(QemuRunner)
  └─ qemu-system-aarch64
       -kernel  Image
       -initrd  initrd.img        ← 新增：随 APK 打包在 assets
       -append  "console=ttyAMA0 rdinit=/init
                 docklet.dns=... docklet.disk=/dev/vdb docklet.rootfs=squashfs"
       -drive   rootfs.squashfs,readonly=on   → /dev/vda
       -drive   data.ext4                     → /dev/vdb
       -no-reboot
          │
          ├─ /init（initramfs，静态 busybox）
          │    mount proc/sys/dev
          │    mount -t squashfs -o ro /dev/vda /mnt
          │    tmpfs 覆盖 /mnt/var /mnt/run /mnt/tmp
          │    mount /dev/vdb → /mnt/var/lib/containers
          │       └─ 失败则 mke2fs -t ext4 -F -m 0 后重挂，再失败回退 tmpfs
          │    /etc 整体换 tmpfs（bind 参照 + 拷回）
          │    拷 busybox 到 /run /var /tmp 三处（互为兜底）
          │    mount --move proc/sys/dev
          │    chroot /mnt /run/busybox sh /run/docklet-boot.sh
          │
          └─ docklet-boot.sh（PID 1）
               hostname / cgroup2 / resolv.conf / hosts
               ip link+addr+route（SLIRP 10.0.2.15/24 gw 10.0.2.2）
               binfmt_misc 注册 qemu-x86_64（字面转义！）
               storage.conf（overlay/vfs）+ registries.conf（含国内镜像）+ containers.conf（netns=host）
               podman system service --time=0 tcp://0.0.0.0:2375
               轮询 /proc/net/tcp 直到 2375 state=0A（LISTEN）→ 打印 "Docklet: READY"
               串口 read-eval 终端 + 保活
```

**为什么用 initramfs 而不是改镜像**：已发布的 `rootfs.squashfs` 里没有 `startvm`，也没有任何能接管启动的脚本。initramfs 让「挂载/可写层/引擎拉起」全部掌握在 App 侧，镜像可以保持原样，升级底座不再需要重新发布 rootfs。

**initramfs 组成**（`app/src/main/assets/initrd.img`，约 1.47 MB）：
- Alpine aarch64 **静态** busybox 1.36.1（1.1 MB）
- `mke2fs` + musl loader + `libblkid/libuuid/libcom_err/libe2p/libeconf`（用于首次格式化 `/dev/vdb`）
- `/init`、`/boot.sh`

构建：`python .work/build-initramfs.py`（自写 newc cpio 打包器，注意 4 字节对齐与**路径分隔符必须写 `/`**，不能用 `os.path.join`，否则 Windows 下会打出 `\`）。

---

## 四、代码修复清单

| 文件 | 改动 |
| --- | --- |
| `data/vm/QemuRunner.kt` | `buildCommand` 重写：新增 `-initrd` + `rdinit=/init`；`rootfstype` 按 rootfs 后缀推导；squashfs 加 `readonly=on`；**把 `data.ext4` 真正挂成 `/dev/vdb`**（原来从不挂载）；去掉写死的 `init=/usr/local/bin/startvm`；新增 `-no-reboot` |
| `data/vm/VmBundle.kt` | 新增 `initrd` 字段与 `ensureInitrd()`（assets → filesDir，按大小幂等更新）；新增 `rootfsType` / `rootfsReadOnly`；默认盘 512 MB → **4096 MB** 稀疏文件；默认 DNS 改 `223.5.5.5,119.29.29.29` |
| `data/vm/BundleDownloader.kt` | 资产名修正：`rootfs.ext4` → **`rootfs.squashfs`**（Release 实际资产），并保留 ext4 作为兜底候选 |
| `ui/screens/SettingsScreen.kt` | 下载流程 `try/finally` 收口：失败/异常也会复位 `downloading` 并刷新就绪状态（原来失败会永久卡「下载中…」）；进度文案统一为 MB；区分「就绪 / 失败 / 未完成」 |
| `data/settings/SettingsRepository.kt` | 默认 DNS 改国内可用地址 |
| `assets/initrd.img` | 新增（构建产物，**不能叫 .gz**，见 BUG-7） |
| `app/build.gradle.kts` | `androidResources { noCompress += "img" }`，让 `assets.openFd()` 可用 |

编译校验：`./gradlew :app:compileDebugKotlin --offline` 通过。

---

## 五、真机验证矩阵

| # | 用例 | 结果 | 关键输出 |
| --- | --- | --- | --- |
| 1 | 冷启动 VM | ✅ | 内核 6.12.0，串口六段标记齐全 |
| 2 | squashfs 只读根挂载 | ✅ | `SQUASHFS error: Xattrs ... ignored`（无害警告） |
| 3 | `/etc` 换 tmpfs | ✅ | `Docklet: /etc 已切换为可写 tmpfs` |
| 4 | ext4 持久盘首次格式化 | ✅ | `mke2fs` → `EXT4-fs (vdb): mounted` |
| 5 | cgroup v2 | ✅ | `podman info` → `cgroupVersion: v2`, `cgroupManager: cgroupfs` |
| 6 | SLIRP 网络 | ✅ | `10.0.2.15/24`，默认路由 `10.0.2.2`，ping 网关 33 ms |
| 7 | 出网 | ✅ | ping `223.5.5.5` 0% 丢包；`wget http://223.5.5.5/` 拿到 404 |
| 8 | binfmt amd64 | ✅ | `Docklet: binfmt amd64 ready` |
| 9 | overlay 存储驱动 | ✅ | `graphDriverName: overlay`, `Native Overlay Diff: "true"` |
| 10 | podman API 监听 | ✅ | `/proc/net/tcp` `00000000:0947 state=0A` |
| 11 | Docker REST `_ping` | ✅ | `OK` HTTP 200 |
| 12 | Docker REST `/version` | ✅ | `Podman Engine 5.2.5`，`linux/arm64/alpine-3.20.10` |
| 13 | 拉取 alpine（arm64） | ✅ | HTTP 200，4.19 MB |
| 14 | 运行 arm64 容器 | ✅ | `aarch64` / `3.24.4` / Exit 0 |
| 15 | 拉取 busybox（amd64） | ✅ | `Architecture=amd64` |
| 16 | 运行 amd64 容器 | ✅ | `amd64-ok`，ELF `e_machine=0x003e`，Exit 0 |
| 17 | 默认网络（不指定 NetworkMode） | ✅ | start 204，日志 `default-net-ok`，Exit 0 |
| 18 | 建 bridge 设备 | ❌ | `ip: RTNETLINK answers: Not supported` |
| 19 | App 内点「启动 VM」全链路 | ✅ | 装 APK → 点按钮 → QEMU pid 出现 → UI 状态「就绪」→ 宿主机 `curl /_ping` OK |
| 20 | App 起的 VM 内跑容器 | ✅ | `e2e-ok` / `aarch64`，Exit 0；UI 显示「引擎 5.2.5 / 镜像 2 / 架构 arm64」 |
| 21 | 装含 `coerceInputValues` 修复的新 APK 后进「容器」页 | ✅ | 标题「容器 (7)」，7 条记录正常渲染，**不再弹 `$[0].Labels` 解析错误** |
| 22 | 重装 APK 后跑 arm64 容器 | ✅ | `verify-arm64` → `aarch64`，`StatusCode: 0` |
| 23 | 重装 APK 后跑 amd64 容器 | ✅ | `Architecture=amd64`，日志 `x86_64` + `amd64-ok`，`StatusCode: 0` |
| 24 | 第二次冷启动复现性 | ✅ | 点「启动 VM」后 **30 s** 就绪（`/_ping` OK） |
| 25 | 慢端点耗时测量 | ⚠️ | `/version` 34~86 s、`/info` >150 s、`/networks` >45 s、`/system/df` >45 s；而 `/volumes` 0.28 s、`/images/json` 5~12 s、`/containers/json` 秒回 —— 见 BUG-9 |

---

## 五之二、App 侧的两个 bug（BUG-7 / BUG-8）

**BUG-7｜AAPT 把 `.gz` 资产改名**（详见上文）—— 表现为点「启动 VM」立刻失败，`[host] 启动失败：initrd.cpio.gz`。

**BUG-8｜`/containers/json` 的 `"Labels": null` 让解析崩溃**
- 现象：UI 弹出 `Unexpected JSON token at offset 438: Expected start of the object '{', but had 'n' instead at path: $[0].Labels`。
- 根因：podman 兼容层把空字段序列化为 `null`，而 `ContainerSummary.Labels` 是 `Map<String,String>`（非空 + 默认值）。kotlinx.serialization 对**显式 null** 不会套用默认值，直接抛异常。
- 修复：`DockerApi` 的 `Json` 配置加 `coerceInputValues = true`（一处改动覆盖所有此类字段）。

---

## 六、已知限制（不是 Docklet 代码问题，但会咬人）

**L1｜桥接网络不可用（内核限制）**
`ip link add ... type bridge` 返回 `RTNETLINK answers: Not supported`，netavark 因此报 `create bridge: Netlink error: Not supported (os error 95)`。
仓库里的 `build-kernel/kernel/docker-roid.config` 写着 `CONFIG_BRIDGE=y`，但**已发布的 `Image` 显然不是用这份配置编译的**。
绕过方式（已内置为默认）：`/etc/containers/containers.conf` 写 `[containers] netns = "host"`，容器直接复用 VM 的 SLIRP 网络。对手机场景反而更合适（无需端口映射、省内存）。要恢复桥接需要重新编译内核。
> 注意：`[network] default_network` 是「新建网络的默认名」，**不是**容器默认网络模式；写错位置不生效。正确键是 `[containers] netns`。

**L2｜docker.io 在国内被 DNS 污染**
`registry-1.docker.io` 实测解析到 `69.171.229.11`（境外错误 IP），直连必超时。
已内置 `registries.conf` 镜像回退（默认 `docker.m.daocloud.io` → `docker.1panel.live` → `hub.rat.dev`），可用 `docklet.mirror=host1,host2` 覆盖。实测 daocloud / 1panel / rat.dev 三个都可达。

**L3｜App UID 的联网受「待机分组」限制**
用 `adb shell run-as` 起 QEMU 时，App 处于后台，Android 会限制该 UID 出网：`shell` UID ping 通，`u0_a958` 100% 丢包。
处理：`am set-standby-bucket com.midsancode.docklet active` + `cmd deviceidle whitelist +com.midsancode.docklet`，或把 App 拉到前台。
**产品侧结论**：VM 要联网，App 必须以**前台服务**运行（现有 `VmService` + `FOREGROUND_SERVICE_SPECIAL_USE` 已满足），并且建议引导用户把 App 加入电池优化白名单。

**L4｜冷启动 podman 绑定端口较慢**
TCG 下 `podman system service` 冷启动要 20~30 秒（guest 时间）才开始监听，期间进程在但不 LISTEN。所以就绪判据必须是「监听状态」，不能是「进程存活」。

**L5｜`/data` 空间紧张**
测试机 `/data` 108 GB 常年 98% 占用。`data.ext4` 是稀疏文件，4 GB 名义大小首次格式化只占 70~80 MB；但装满镜像后会真实增长，空间不足时 init 会回退到 tmpfs（重启丢镜像）。

**L6｜`/version`、`/info`、`/networks`、`/system/df` 极慢（BUG-9 的表现面）**
见下节 BUG-9。根因是内核缺 bridge 支持导致 netlink 操作阻塞；这四条路由都会走到网络后端。核心 CRUD 不受影响，但 `docker version` / `docker info` / `docker network ls` 体验很差，App 主页也因依赖 `/info` + `/system/df` 而首屏要等几十秒。

---

## 七、复现命令

```bash
export MSYS_NO_PATHCONV=1
ADB=/f/android-build-tools/android-sdk/platform-tools/adb.exe
$ADB connect 192.168.1.11:5555

# 0) 让 App UID 能出网（否则 guest 拉不到镜像）
$ADB shell "am set-standby-bucket com.midsancode.docklet active"
$ADB shell "cmd deviceidle whitelist +com.midsancode.docklet"

# 1) 重建并推送 initramfs
python .work/build-initramfs.py
$ADB push app/src/main/assets/initrd.img /data/local/tmp/initrd.img
$ADB shell run-as com.midsancode.docklet cp /data/local/tmp/initrd.img files/initrd.img

# 2) 启动（vmtest.sh 与 QemuRunner.buildCommand 参数等价）
$ADB push .work/vmtest.sh /data/local/tmp/vmtest.sh
$ADB shell run-as com.midsancode.docklet cp /data/local/tmp/vmtest.sh files/vmtest.sh
$ADB shell "run-as com.midsancode.docklet sh files/vmtest.sh" &   # 加 docklet.diag=1 开诊断

# 3) 观察串口
$ADB shell "run-as com.midsancode.docklet tail -f files/v.out"

# 4) 通过 Docker 兼容 API 操作（adb forward 必须与 curl 在同一次 shell 调用里，否则 adb server 重启会丢 forward）
$ADB forward tcp:23750 tcp:2375
curl -s --noproxy "*" http://127.0.0.1:23750/_ping
curl -s --noproxy "*" -X POST "http://127.0.0.1:23750/images/create?fromImage=alpine&tag=latest"
curl -s --noproxy "*" -X POST -H 'Content-Type: application/json' \
     -d '{"Image":"alpine:latest","Cmd":["sh","-c","uname -m"]}' \
     http://127.0.0.1:23750/containers/create
# 然后 POST /containers/<id>/start，GET /containers/<id>/logs?stdout=1
```

---

## 八、下一步（按价值排序）

1. ~~App 内跑通完整链路~~ —— 已完成（见用例 19/20）。
2. **重编内核打开 bridge**：若确实需要容器独立网络，按仓库 config 重编 `Image`，并补 `CONFIG_BRIDGE=y` 的确认（现有 Image 与 config 不一致，应查清是谁编译的）。这也是 BUG-9 的唯一根治办法。
3. **镜像/网络设置 UI**：把 `docklet.mirror` 与 DNS 暴露到设置页，而不是写死。
4. **容器管理 UI**：目前只有引擎，没有容器/镜像的界面（API 已可用，纯前端工作）。
5. **`/data` 空间预检**：启动前检查可用空间，不足时提示用户而不是静默回退 tmpfs。
6. **主页降级加载**：`/info` 与 `/system/df` 慢时不要阻塞首屏，先用 `/_ping` 头（含 `Libpod-Api-Version`）+ `/images/json` + `/containers/json` 出图，慢数据后到再刷新。

---

## 九、第三轮复验（2026-11-13 夜，BUG-8 收尾 + BUG-9 新发现）

### 9.1 本轮做了什么

1. 重新构建并安装 APK（含 `coerceInputValues = true` 修复），真机复验容器列表 UI。
2. 重装后重新验证 arm64 / amd64 两条运行路径。
3. 顺手发现并定位了一个新缺陷 **BUG-9**（部分 REST 路由极慢）。

### 9.2 BUG-8 复验结论：已修复

进「容器」页后标题显示「容器 (7)」，7 条记录（`created` / `exited` 状态、镜像名、`Exited (0) About an hour ago`）全部正常渲染，**没有再出现 `Unexpected JSON token ... $[0].Labels`**。修复有效，可关闭。

### 9.3 BUG-9｜部分 REST 路由极慢（新发现）

**现象**（同一台机、同一 VM，宿主机 curl 计时）：

| 端点 | 耗时 | 结果 |
| --- | --- | --- |
| `/_ping` | 0.10~0.26 s | 200 |
| `/volumes` | 0.28 s | 200 |
| `/containers/json` | 秒回 | 200 |
| `/images/json` | 5~12 s | 200 |
| `/version` | **34 s（刚开机）→ 86 s（跑久了）** | 200，内容正确 |
| `/networks` | **> 45 s** | 超时 |
| `/system/df` | **> 45 s** | 超时 |
| `/info` | **> 150 s** | 超时 |

**排除掉的假设**（都实测过，别再重复踩）：

1. **不是死锁** —— `/version` 给到 120 s 超时会返回 200，内容完全正确（含 conmon 2.1.12、crun 1.17 组件信息）。
2. **不是内存压力** —— 并行跑 Gradle 时确实把 qemu 页换出（`SwapCached` 917 MB），但构建结束后复测 `/version` 仍 86 s，`/images/json` 仍只要 4.9 s。
3. **不是 exec 慢、不是磁盘慢、不是 TCG 整体慢** —— guest 内实测：`time true` → `real 0m0.26s`；`time crun --version` → `real 0m0.44s`；`podman version` CLI 秒回。
4. **不是 DNS / 不是 API 服务挂了** —— `/_ping` 与 `/volumes` 都在亚秒级。

**定位到的证据链**：

- guest 内 `time podman network ls` → `real 0m16.46s`，但 `user 0m2.35s` + `sys 0m2.26s`，**只用了 4.6 s CPU，其余 12 s 在等**。等待型阻塞，不是计算。
- `podman network rm -f podman` → 先卡 20 s，再报 `Error: default network podman cannot be removed`。
- 与 L1 完全咬合：内核 `ip link add ... type bridge` → `RTNETLINK answers: Not supported`。netavark / podman 的网络后端任何一次 netlink dump 或 bridge 操作都会卡住直到超时重试。
- 而 `/version`、`/info`、`/networks`、`/system/df` **恰好是四条会触达网络后端的路由**；`/volumes`、`/images/json`、`/containers/json` 不触达，所以快。

**结论**：BUG-9 与 L1 是同一个根因（**内核缺 bridge / netlink 支持**），不是两件事。根治只能重编内核；配置层绕不过去（默认网络 `podman` 明确不允许删除）。

**副作用（要在产品上处理）**：App 主页 `info()` + `systemDf()` 正好命中两条最慢的路由，且 `DockerEngineFactory` 的 `readTimeout = 0`（为流式端点设计），所以首屏会转几十秒才填充。改善方向见「下一步」第 6 条。

### 9.4 重装后的双架构复验（原始输出）

```
# arm64
POST /containers/create?name=verify-arm64  {"Image":"alpine","Cmd":["uname","-m"]}
POST /containers/verify-arm64/start        http=204
POST /containers/verify-arm64/wait         {"StatusCode":0,"Error":null}
GET  /containers/verify-arm64/logs         aarch64

# amd64（Arch=amd64 拉取）
POST /v4.0.0/libpod/images/pull?reference=docker.io/library/busybox:latest&Arch=amd64&OS=linux
GET  /images/busybox:latest/json           Arch=amd64  Os=linux
POST /containers/create?name=verify-amd64  {"Image":"busybox","Cmd":["sh","-c","uname -m; echo amd64-ok"]}
POST /containers/verify-amd64/start        http=204
POST /containers/verify-amd64/wait         {"StatusCode":0,"Error":null}
GET  /containers/verify-amd64/logs         x86_64 / amd64-ok
```

### 9.5 真机操作小抄（这轮踩的坑）

- **adb server 每次 Bash 调用都会重启**，`adb forward` 必然丢。`connect` + `forward` + `curl` 必须写在**同一次** shell 调用里。
- App 内终端的输入框坐标**随键盘开合变化**：键盘收起时 EditText 在 `[83,1842][892,1974]`、发送键 `[892,1841][1052,1973]`；键盘展开时整体上移到 `[83,1354][892,1486]`、发送键 `(972,1420)`。硬编码坐标前先用 `uiautomator dump` 确认。
- `input text` 里的 `;` `|` 会被**设备端 shell** 当分隔符吃掉，必须用单引号包住：`adb shell "input text 'a;b'"`；空格用 `%s`。
- **不要随手 `input keyevent 4`**：键盘已收起时它就是 BACK，会把 App 退出、VM 一起杀掉（这轮因此重装/重启过两次）。
- `podman network rm -f podman` 删不掉默认网络，别在这上面浪费时间。

