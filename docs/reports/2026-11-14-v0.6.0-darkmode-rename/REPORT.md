# v0.6.0 真机验证报告 —— 明暗模式 + 项目更名 Podroid

- **日期**：2026-11-14
- **版本**：0.6.0 (versionCode 6)
- **设备**：Redmi Note 9 5G（cannon / MT6853T），Android 12 (API 31)，7.4 GB RAM，1080×2340 @440dpi，无 root
- **连接**：`adb connect 192.168.1.11:5555`
- **验证包**：先 debug（`com.midsancode.podroid`），后 release（R8 + 新签名）

---

## 一、本轮改了什么

1. **明暗模式**：设置 → 外观 → 明暗模式，三态（跟随系统 / 浅色 / 深色），立即生效并持久化。
2. **项目更名**：Docklet → Podroid，含包名、源码目录、类名、资源、串口标记、内核 cmdline、签名、GitHub 仓库。

---

## 二、明暗模式

### 实现要点

| 点 | 说明 |
|---|---|
| 持久化 | DataStore `theme_mode`，值 `system`/`light`/`dark`，非法值回落 `system` |
| 注入 | `MainActivity` 把三态解析成 bool，传给 `PodroidTheme(darkTheme=)` |
| **语义色** | 新增 `LocalDarkTheme`（`staticCompositionLocalOf`），`toneColors()` 读它而不是 `isSystemInDarkTheme()` |
| 冷启动 | 新增 `values-night/colors.xml` + `values-night/themes.xml`，窗口底色深色 `#0F1115` / 浅色 `#F6F8FC` |
| 窗口主题 | 浅色基类由 `android:Theme.Material` 改为 `android:Theme.Material.Light.NoActionBar`（原来浅色下冷启动会先黑一帧） |

**为什么必须用 `LocalDarkTheme`**：明暗是用户可选三态。若用户手动选「深色」而系统是「浅色」，
`isSystemInDarkTheme()` 仍返回 `false`，`toneColors()` 会给出浅色版语义色（浅绿底 + 深绿字），
在深色卡片上就是一块刺眼的白斑。`LocalDarkTheme` 由主题自身提供，与用户选择严格一致。

### 验证结果

在深色与浅色两套下逐页扫过主页 / 容器 / 镜像 / 资源 / 终端 / 设置，**未发现对比度事故**。

- 设置页：背景 `#0F1115`、卡片 `#16181D`、正文 `#E2E4E9`，层次清晰；选中芯片用品牌蓝填充。
- 主页：状态英雄卡走 `primaryContainer → secondaryContainer` 渐变（深色下为 `#0B4BA8`），白字可读；
  三个统计卡与引擎卡用 `surfaceContainerLow`，与背景拉开一档。
- 终端页：保持恒定深色（`#0B0F14` 底 / `#CDD6DC` 字），终端本就该这样，不跟主题走。
- **全仓只有 `TerminalScreen` 保留硬编码颜色**，其余全部走主题 token（`grep "Color(0x"` 核对过）。

### 已知的观感取舍（非缺陷）

- 深色下 `Button`（filled）是浅蓝 `#AEC6FF` + 深字，`PodroidTonalButton` 是深蓝 `#0B4BA8` + 浅字，
  于是"次级按钮"反而比"主按钮"更重。这是 Material 3 深色规范的固有表现（primary 取 tone 80、
  primaryContainer 取 tone 30），不是对比度问题，未改。

---

## 三、更名验证

### 覆盖面

- 61 个文件批量替换 `docklet→podroid` / `Docklet→Podroid` / `DOCKLET→PODROID`。
- 目录 `app/src/main/java/com/midsancode/docklet/` → `.../podroid/`（`git mv`，保留 blame）。
- 文件改名：`DockletApp.kt`→`PodroidApp.kt`、`DockletRoot.kt`→`PodroidRoot.kt`、
  `DockletUi.kt`→`PodroidUi.kt`、`docklet-release.jks`→`podroid-release.jks`。
- `applicationId` / `namespace` → `com.midsancode.podroid`；`rootProject.name` `DockerDroid`→`Podroid`。
- GitHub 仓库 `luolingy/Docklet` → `luolingy/Podroid`（`gh api -X PATCH`），旧 URL 自动重定向。

### 三个容易漏的点（都踩了）

1. **JKS 内部别名不随文件名变**。`keystore.properties` 改了口令和 `storeFile`，但库内别名仍是 `docklet`，
   签名会失败。需 `keytool -changealias` + `keytool -storepasswd`。
2. **证书主体 DN 改不掉**。`changealias` 后 `apksigner --print-certs` 仍显示 `CN=Docklet`。
   因为 `com.midsancode.podroid` 尚未发过版，最终选择重建整个 JKS（`-dname "CN=Podroid,..."`），
   新指纹 `6A:D3:4E:4D:17:06:F9:8E:03:BF:24:45:9A:5F:B7:9D:2B:54:4D:2D:71:A5:43:28:EB:A9:A9:0A:52:F2:04:FA`。
   包名已变，与旧 Docklet 不构成升级关系，换签名无副作用。旧库备份在 `.work/podroid-release.jks.docklet-cn.bak`。
3. **`DESIGN.md` 里的 "Podroid" 原本指第三方项目 `ExTV/Podroid`**（Android 播客客户端）。
   全局替换会把"要自己写一个原生 Docklet"改成"要自己写一个原生 Podroid"，语义完全错乱。
   已 `git checkout` 还原原文，只在开头加更名说明并注明区分。

### 串口标记联动（关键）

guest 侧 `Podroid: xxx` 与 App 侧 `VmStatus.BootStage.marker` 必须成对改，否则启动检测永远匹配不上，
表现为一直停在「启动中」。initramfs 已重新生成（`.work/build-initramfs.py` → `assets/initrd.img`，1,471,394 字节），
校验结果：`Podroid` 出现 19 次、`Docklet` 0 次；`podroid-boot.sh` 5 次、`docklet-boot.sh` 0 次。

**真机实测串口输出**：

```
Podroid: binfmt amd64 ready
Podroid: storage driver=overlay network=host
Podroid: engine starting
→ 状态卡推进到「就绪 · 容器引擎已就绪 · Docker API 127.0.0.1:2375」
   引擎信息：版本 5.2.5 / 架构 arm64
```

QEMU 命令行同步改名：`podroid.dns/ram/disk/rootfs/network`，virtio-serial 端口 `Podroid.hostd`。

---

## 四、底座下载链路

仓库改名后，`Mirrors.RELEASE` 指向 `https://github.com/luolingy/Podroid/releases/latest/download`，
走 `ghfast.top` → `gh-proxy.com` → 直链三级回退。

真机实测：88 MB 完整落地。

```
Image            43,512,320 B
rootfs.squashfs  44,871,680 B
```

### ⚠️ 一个需要记住的设计缺陷

用 `releases/latest/download/<asset>` 意味着**每个 App release 都必须重传 121 MB 底座资产**，
否则新版本一发布，`latest` 指向它、底座立刻下不到。

本次 v0.6.0 已把 `Image` / `rootfs.squashfs` / `qemu-bundle.tar.gz` 三件套重传到该 release，
`latest/download` 两个底座均返回 206。

**根治办法**：把 URL 钉到固定 tag（`releases/download/v0.4.2/`），或建一个永不移动的 `bundle` release。
代价是改常量 + 重新构建，本次未做。

---

## 五、构建

| 场景 | 耗时 | 说明 |
|---|---|---|
| 全量 `assembleRelease -Pkotlin.incremental=false` | 29m50s | 本机内存压力，比常规 10~15 分钟慢一倍 |
| 只改签名配置的增量重打包 | 10m56s | 仅 `packageRelease` 重跑，R8 产物复用 |

产物：`app/build/outputs/apk/release/app-release.apk`，27.9 MB，
签名主体 `CN=Podroid, OU=Dev, O=MidsanCode, L=Beijing, ST=Beijing, C=CN`。

已知唯一编译警告：`TerminalScreen.kt:196` 的 `KeyboardOptions` 构造器 deprecated（无害）。

---

## 六、未完成项

- **release 包的装机冒烟没做**。MIUI 返回 `INSTALL_FAILED_USER_RESTRICTED`，
  `settings put global adb_install_need_confirm 0` 无效，必须在「开发者选项 → USB 安装」手动打开。
  设备当前无任何 `podroid` / `docklet` / `dockerroid` 包（debug 已卸载）。
  缓解：release 与已通过端到端验证的 debug 是同一份代码，差异仅 R8 与签名，而 R8 链路在 v0.5.0 已验证过。
- `build-rootfs/`、`build-kernel/` 仍是 Docklet 命名。它们产出的是已发布的二进制资产，本次不重建。
- `VmService.appendSerial` 的 `[host] 阶段：` 前缀仍是中文硬编码（v0.5.0 遗留待办）。

---

## 七、截图

`shots/` 目录：

| 文件 | 内容 |
|---|---|
| `podroid_dark.png` | 设置页 · 深色 · 明暗三芯片 |
| `light_settings.png` | 设置页 · 浅色 · 明暗三芯片 |
| `dark_home.png` | 主页 · 深色 · VM 已停止 |
| `light_home.png` | 主页 · 浅色 · VM 就绪 + 引擎卡 |
| `boot2.png` | 主页 · 深色 · VM 就绪（Podroid 标记识别成功） |

---

## 八、给后续会话的提示

- 改明暗相关代码，**不要**把 `toneColors()` 改回 `isSystemInDarkTheme()`。
- 改串口标记，App 与 initramfs 必须同一次提交内改完，并重新生成 `assets/initrd.img`。
- 发新版本时，**记得把 121 MB 底座资产一起传到该 release**，否则老用户下不到底座。
- 真机装包前先确认「开发者选项 → USB 安装」是开的。
