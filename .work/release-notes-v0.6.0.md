## Podroid v0.6.0

项目由 **Docklet** 更名为 **Podroid**，并加上明暗模式。

### 破坏性变更

- **包名 `com.midsancode.docklet` → `com.midsancode.podroid`**
  在设备上算一个全新应用：旧的 Docklet 需要手动卸载，已下载的 88 MB 底座和容器数据**不会迁移**（重装后重新下载即可）。
- **guest 串口标记 `Docklet: xxx` → `Podroid: xxx`**
  initramfs 已重新生成，App 与底座必须成对升级；旧 APK 配新底座会一直停在启动中。
- 远程仓库 `luolingy/Docklet` → `luolingy/Podroid`，GitHub 会保留旧地址重定向，v0.4.2 的底座下载链接继续可用。
- 内部标识同步改名（`podroid_prefs` / `podroid_settings` / WakeLock tag / Intent action），
  升级用户的语言、保活等本地设置会重置为默认值。
- 签名密钥库重建（别名 `podroid`，主体 `CN=Podroid`），新证书 SHA-256 `6A:D3:4E:4D:…:04:FA`。
  因为包名已变，与旧 Docklet 的签名不构成升级关系。

### 新功能：明暗模式

设置 → 外观 → 明暗模式，三选一：**跟随系统 / 浅色 / 深色**，切换立即生效。

- 不再只依赖 `isSystemInDarkTheme()`，改由 `LocalDarkTheme` 注入；语义色（成功/警告/危险/信息）跟着用户选择走，
  手动选深色而系统是浅色时不会出现"浅底深字"的对比度事故。
- 新增 `values-night/` 窗口底色，冷启动不会先闪一帧不匹配的背景色。
- 修复窗口主题：原来用的是 `android:Theme.Material`（深色变体），浅色模式下冷启动会先黑一下。
- 终端页保持恒定深色（终端本就该这样），其余页面全部走主题 token。

### 改名清单

| 位置 | 旧 | 新 |
|---|---|---|
| 包名 / applicationId | `com.midsancode.docklet` | `com.midsancode.podroid` |
| 应用显示名 | Docklet | Podroid |
| Gradle rootProject | DockerDroid | Podroid |
| 入口类 | `DockletApp` / `DockletRoot` | `PodroidApp` / `PodroidRoot` |
| 主题 | `DockletTheme` / `DockletDialog` / `DockletUi.kt` | `PodroidTheme` / `PodroidDialog` / `PodroidUi.kt` |
| 签名 | `docklet-release.jks`（alias `docklet`） | `podroid-release.jks`（alias `podroid`） |
| 串口标记 | `Docklet: READY` | `Podroid: READY` |
| 内核 cmdline | `docklet.dns=` 等 | `podroid.dns=` 等 |
| virtio-serial 端口 | `Docklet.hostd` | `Podroid.hostd` |

### 已验证（Redmi Note 9 5G / Android 12 / arm64）

- 明暗三态切换即时生效，深浅两套下六个页面均无对比度问题。
- 底座从新仓库地址下载成功（`ghfast.top` / `gh-proxy.com` 加速前缀，88 MB 完整）。
- VM 启动链路端到端通过：`Podroid: binfmt amd64 ready` → `storage driver=overlay network=host` →
  `engine starting` → READY，podman 5.2.5 / arm64，Docker API `127.0.0.1:2375`。

### 未改名

`build-rootfs/`、`build-kernel/` 保留 Docklet 命名 —— 它们产出的是已发布的二进制资产，本次不重建。
