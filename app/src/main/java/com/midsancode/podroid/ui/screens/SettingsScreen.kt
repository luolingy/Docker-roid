package com.midsancode.podroid.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.midsancode.podroid.BuildConfig
import com.midsancode.podroid.R
import com.midsancode.podroid.crash.CrashHandler
import com.midsancode.podroid.data.diag.Diagnostics
import com.midsancode.podroid.data.prefs.Prefs
import com.midsancode.podroid.data.settings.DEFAULT_DNS
import com.midsancode.podroid.data.settings.DEFAULT_MIRRORS
import com.midsancode.podroid.data.settings.SettingsRepository
import com.midsancode.podroid.data.update.UpdateChecker
import com.midsancode.podroid.data.vm.BundleDownloader
import com.midsancode.podroid.data.vm.QemuBinaries
import com.midsancode.podroid.data.vm.VmBundle
import com.midsancode.podroid.data.vm.VmManager
import com.midsancode.podroid.data.vm.VmStatus
import com.midsancode.podroid.ui.components.CommitSlider
import com.midsancode.podroid.ui.components.PodroidDialog
import com.midsancode.podroid.ui.components.PodroidTonalButton
import com.midsancode.podroid.ui.components.HintRow
import com.midsancode.podroid.ui.components.KeyValueRow
import com.midsancode.podroid.ui.components.Preset
import com.midsancode.podroid.ui.components.PresetChips
import com.midsancode.podroid.ui.components.ScreenHeader
import com.midsancode.podroid.ui.components.SectionCard
import com.midsancode.podroid.ui.components.SettingRow
import com.midsancode.podroid.ui.theme.Tone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 底座镜像源：GitHub Release 直链。 */
private const val MIRROR_BASE =
    "https://github.com/luolingy/Podroid/releases/latest/download"

// ---- 预设（纯数字，无需翻译） ----

private val RAM_PRESETS = listOf(
    Preset("768", "768 MB"),
    Preset("1024", "1 GB"),
    Preset("1536", "1.5 GB"),
    Preset("2048", "2 GB"),
)

private val DISK_PRESETS = listOf(
    Preset("2048", "2 GB"),
    Preset("4096", "4 GB"),
    Preset("8192", "8 GB"),
)

private const val CUSTOM = "__custom__"

/** 主题色 key 与文案资源的对应关系，顺序即展示顺序。 */
private val THEME_KEYS = listOf(
    "blue" to R.string.theme_blue,
    "teal" to R.string.theme_teal,
    "purple" to R.string.theme_purple,
    "green" to R.string.theme_green,
    "orange" to R.string.theme_orange,
)

/**
 * 设置页。
 *
 * 交互原则（这次重做的重点）：
 *  1. **离散选项立即生效**（chip / 开关），不用点保存；
 *  2. **滑块只在松手时落盘**，拖动过程零写入、不抖动；
 *  3. **需要手输的项（DNS/镜像站/端口）走草稿 + 底部保存条**，带即时校验和错误提示；
 *  4. 任何改动都会点亮「重启 VM 生效」提示，并提供一键重启，避免"改了没反应"的困惑。
 */
@Composable
fun SettingsScreen(settingsRepo: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    val settings by settingsRepo.current.collectAsState()
    val vmStatus by VmManager.status.collectAsState()

    // 下载态
    var refreshTick by remember { mutableIntStateOf(0) }
    var exportedPath by remember { mutableStateOf<String?>(null) }
    var downloadLine by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val bundleReady = remember(refreshTick) {
        VmBundle.isReady(context) && QemuBinaries.isPresent(context)
    }

    // 草稿（只有文本类需要保存）
    var dnsDraft by remember(settings.dnsServers) { mutableStateOf(settings.dnsServers) }
    var mirrorDraft by remember(settings.mirrors) { mutableStateOf(settings.mirrors) }
    var portDraft by remember(settings.enginePort) { mutableStateOf(settings.enginePort.toString()) }
    var customDns by remember(settings.dnsServers) { mutableStateOf(false) }
    var customMirror by remember(settings.mirrors) { mutableStateOf(false) }

    // 是否有未保存的文本改动
    val dirty = dnsDraft.trim() != settings.dnsServers ||
        mirrorDraft.trim() != settings.mirrors ||
        portDraft.trim() != settings.enginePort.toString()

    val dnsError = validateDns(context, dnsDraft)
    val mirrorError = validateMirrors(context, mirrorDraft)
    val portError = validatePort(context, portDraft)
    val canSave = dirty && dnsError == null && mirrorError == null && portError == null

    // 改动后需要重启才生效
    var needsRestart by remember { mutableStateOf(false) }
    val vmRunning = vmStatus !is VmStatus.Stopped

    // ---- 外观 ----
    var langTag by remember { mutableStateOf(Prefs.language(context)) }
    val themePresets = THEME_KEYS.map { (key, res) -> Preset(key, stringResource(res)) }
    val langPresets = listOf(
        Preset("", stringResource(R.string.lang_system)),
        Preset("zh", stringResource(R.string.lang_zh)),
        Preset("en", stringResource(R.string.lang_en)),
    )

    // ---- 保活 ----
    var keepAlive by remember { mutableStateOf(Prefs.keepAlive(context)) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var batteryOpt by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    // 从系统电池优化页返回时刷新状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    var crashLogText by remember { mutableStateOf<String?>(null) }

    // ---- 更新 ----
    val checker = remember { UpdateChecker() }
    var updateState by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var checking by remember { mutableStateOf(false) }

    fun restartVm() {
        needsRestart = false
        VmManager.stopVm()
        cs.launch {
            val stopped = withTimeoutOrNull(25_000) {
                VmManager.status.first { it is VmStatus.Stopped }
            } != null
            if (stopped) VmManager.startVm()
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                stringResource(R.string.settings_title),
                stringResource(R.string.settings_sub),
            )

            // 重启提示
            AnimatedVisibility(needsRestart) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            stringResource(
                                if (vmRunning) R.string.settings_dirty_saved_restart
                                else R.string.settings_dirty_saved_next
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (vmRunning) {
                            TextButton(onClick = { restartVm() }) {
                                Text(stringResource(R.string.action_restart))
                            }
                        }
                    }
                }
            }

            // ---- 外观 ----
            SectionCard(title = stringResource(R.string.sec_appearance), icon = Icons.Rounded.Palette) {
                SettingRow(
                    title = stringResource(R.string.setting_mode),
                    description = stringResource(R.string.setting_mode_desc),
                ) {}
                PresetChips(
                    presets = listOf(
                        Preset("system", stringResource(R.string.mode_system)),
                        Preset("light", stringResource(R.string.mode_light)),
                        Preset("dark", stringResource(R.string.mode_dark)),
                    ),
                    selectedKey = settings.themeMode,
                    onSelect = { p -> cs.launch { settingsRepo.setThemeMode(p.key) } },
                )

                Spacer(Modifier.height(10.dp))
                SettingRow(
                    title = stringResource(R.string.setting_theme),
                    description = stringResource(R.string.setting_theme_desc),
                ) {}
                PresetChips(
                    presets = themePresets,
                    selectedKey = settings.themeColor,
                    onSelect = { p -> cs.launch { settingsRepo.setThemeColor(p.key) } },
                )

                Spacer(Modifier.height(10.dp))
                SettingRow(
                    title = stringResource(R.string.setting_language),
                    description = stringResource(R.string.setting_language_desc),
                ) {}
                PresetChips(
                    presets = langPresets,
                    selectedKey = langTag,
                    onSelect = { p ->
                        if (p.key != langTag) {
                            Prefs.setLanguage(context, p.key)
                            langTag = p.key
                            // 语言只在 attachBaseContext 里生效，必须重建 Activity
                            context.findActivity()?.recreate()
                        }
                    },
                )
            }

            // ---- 性能 ----
            SectionCard(
                title = stringResource(R.string.sec_perf),
                icon = Icons.Rounded.Speed,
                subtitle = stringResource(R.string.sec_perf_sub),
            ) {
                SettingRow(
                    title = stringResource(R.string.setting_ram),
                    description = stringResource(R.string.setting_ram_desc),
                ) {
                    Text(
                        "${settings.ramMb} MB",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PresetChips(
                    presets = RAM_PRESETS,
                    selectedKey = settings.ramMb.toString(),
                    onSelect = { p ->
                        cs.launch { settingsRepo.setRam(p.key.toInt()) }
                        needsRestart = true
                    },
                )
                CommitSlider(
                    value = settings.ramMb.toFloat(),
                    valueRange = 512f..4096f,
                    steps = 0,
                    onCommit = { v ->
                        cs.launch { settingsRepo.setRam((v / 128).toInt() * 128) }
                        needsRestart = true
                    },
                )

                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.setting_disk),
                    description = stringResource(R.string.setting_disk_desc),
                ) {
                    Text(
                        if (settings.diskMb >= 1024) "%.0f GB".format(settings.diskMb / 1024f)
                        else "${settings.diskMb} MB",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PresetChips(
                    presets = DISK_PRESETS,
                    selectedKey = settings.diskMb.toString(),
                    onSelect = { p ->
                        cs.launch { settingsRepo.setDisk(p.key.toInt()) }
                        needsRestart = true
                    },
                )
                CommitSlider(
                    value = settings.diskMb.toFloat(),
                    valueRange = 1024f..8192f,
                    steps = 0,
                    onCommit = { v ->
                        cs.launch { settingsRepo.setDisk((v / 512).toInt() * 512) }
                        needsRestart = true
                    },
                )
            }

            // ---- 网络 ----
            SectionCard(
                title = stringResource(R.string.sec_network),
                icon = Icons.Rounded.Router,
                subtitle = stringResource(R.string.sec_network_sub),
            ) {
                val dnsPresets = listOf(
                    Preset(DEFAULT_DNS, stringResource(R.string.dns_preset_cn)),
                    Preset("223.5.5.5", stringResource(R.string.dns_preset_ali)),
                    Preset("119.29.29.29", stringResource(R.string.dns_preset_tencent)),
                    Preset("8.8.8.8,1.1.1.1", stringResource(R.string.dns_preset_google)),
                )
                val mirrorPresets = listOf(
                    Preset("", stringResource(R.string.preset_auto)),
                    Preset("docker.m.daocloud.io", "DaoCloud"),
                    Preset("docker.1panel.live", "1Panel"),
                    Preset("hub.rat.dev", "Rat.dev"),
                )

                // DNS
                SettingRow(
                    title = stringResource(R.string.setting_dns),
                    description = stringResource(R.string.setting_dns_desc),
                ) {}
                PresetChips(
                    presets = dnsPresets,
                    selectedKey = if (!customDns && dnsPresets.any { it.key == dnsDraft }) dnsDraft else CUSTOM,
                    onSelect = { p ->
                        customDns = false
                        dnsDraft = p.key
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (customDns) {
                    OutlinedTextField(
                        value = dnsDraft,
                        onValueChange = { dnsDraft = it },
                        label = { Text(stringResource(R.string.setting_dns_list)) },
                        placeholder = { Text("223.5.5.5,119.29.29.29") },
                        supportingText = {
                            Text(dnsError ?: stringResource(R.string.setting_dns_hint))
                        },
                        isError = dnsError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CurrentValue(
                        label = stringResource(R.string.current_label),
                        value = dnsDraft,
                        onCustom = { customDns = true },
                    )
                }

                Spacer(Modifier.height(8.dp))
                // 镜像站
                SettingRow(
                    title = stringResource(R.string.setting_mirror),
                    description = stringResource(R.string.setting_mirror_desc),
                ) {}
                PresetChips(
                    presets = mirrorPresets,
                    selectedKey = if (!customMirror && mirrorPresets.any { it.key == mirrorDraft }) mirrorDraft else CUSTOM,
                    onSelect = { p ->
                        customMirror = false
                        mirrorDraft = p.key
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (customMirror) {
                    OutlinedTextField(
                        value = mirrorDraft,
                        onValueChange = { mirrorDraft = it },
                        label = { Text(stringResource(R.string.setting_mirror_label)) },
                        placeholder = { Text("docker.m.daocloud.io,registry.example.com") },
                        supportingText = {
                            Text(mirrorError ?: stringResource(R.string.setting_mirror_hint))
                        },
                        isError = mirrorError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CurrentValue(
                        label = stringResource(R.string.current_effective),
                        value = mirrorDraft.ifBlank { DEFAULT_MIRRORS },
                        onCustom = { customMirror = true },
                    )
                }

                Spacer(Modifier.height(8.dp))
                // 端口
                SettingRow(
                    title = stringResource(R.string.setting_port),
                    description = stringResource(R.string.setting_port_desc),
                ) {}
                OutlinedTextField(
                    value = portDraft,
                    onValueChange = { portDraft = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.setting_port_label)) },
                    supportingText = {
                        Text(portError ?: stringResource(R.string.setting_port_range))
                    },
                    isError = portError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                // 容器网络模式
                SettingRow(
                    title = stringResource(R.string.setting_netmode),
                    description = stringResource(R.string.setting_netmode_desc),
                ) {}
                PresetChips(
                    presets = listOf(
                        Preset("host", stringResource(R.string.setting_netmode_host)),
                        Preset("bridge", stringResource(R.string.setting_netmode_bridge)),
                    ),
                    selectedKey = settings.networkMode,
                    onSelect = { p ->
                        cs.launch { settingsRepo.setNetworkMode(p.key) }
                        needsRestart = true
                    },
                )
                if (settings.networkMode == "bridge") {
                    Spacer(Modifier.height(8.dp))
                    HintRow(
                        stringResource(R.string.setting_bridge_warning),
                        tone = Tone.Warning,
                    )
                }
            }

            // ---- 启动 ----
            SectionCard(title = stringResource(R.string.sec_startup), icon = Icons.Rounded.PowerSettingsNew) {
                SettingRow(
                    title = stringResource(R.string.setting_autostart),
                    description = stringResource(R.string.setting_autostart_desc),
                ) {
                    Switch(
                        checked = settings.autoStart,
                        onCheckedChange = { b -> cs.launch { settingsRepo.setAutoStart(b) } },
                    )
                }
            }

            // ---- 后台保活 ----
            SectionCard(title = stringResource(R.string.sec_keepalive), icon = Icons.Rounded.RestartAlt) {
                SettingRow(
                    title = stringResource(R.string.keepalive_switch),
                    description = stringResource(R.string.keepalive_switch_desc),
                ) {
                    Switch(
                        checked = keepAlive,
                        onCheckedChange = { b ->
                            keepAlive = b
                            Prefs.setKeepAlive(context, b)
                        },
                    )
                }

                Spacer(Modifier.height(4.dp))
                SettingRow(
                    title = stringResource(R.string.battery_opt),
                    description = stringResource(
                        if (batteryOpt) R.string.battery_opt_on else R.string.battery_opt_off
                    ),
                ) {
                    if (!batteryOpt) {
                        TextButton(onClick = { requestIgnoreBattery(context) }) {
                            Text(stringResource(R.string.battery_opt_action))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                val crashFile = remember { runCatching { CrashHandler.crashLogFile(context.app()) }.getOrNull() }
                SettingRow(
                    title = stringResource(R.string.crash_log),
                    description = stringResource(
                        if (crashFile?.exists() == true) R.string.crash_log_view
                        else R.string.crash_log_none
                    ),
                ) {
                    TextButton(
                        enabled = crashFile?.exists() == true,
                        onClick = {
                            crashLogText = runCatching { crashFile?.readText().orEmpty() }
                                .getOrDefault("")
                        },
                    ) { Text(stringResource(R.string.crash_log_view)) }
                }
            }

            // ---- 底座 ----
            SectionCard(
                title = stringResource(R.string.sec_bundle),
                icon = Icons.Rounded.CloudDownload,
                subtitle = stringResource(R.string.sec_bundle_sub),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (bundleReady) Icons.Rounded.Info else Icons.Rounded.CloudDownload,
                        null,
                        tint = if (bundleReady) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (bundleReady) R.string.bundle_ready else R.string.bundle_not_ready
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (!bundleReady) {
                    Button(
                        onClick = {
                            downloading = true
                            downloadLine = context.getString(R.string.bundle_prepare)
                            cs.launch {
                                var err: String? = null
                                try {
                                    BundleDownloader(context).downloadAll(MIRROR_BASE).collect { p ->
                                        downloadLine = progressText(context, p)
                                        if (p.error != null) err = p.error
                                    }
                                    if (err == null) {
                                        QemuBinaries.downloadFrom(context, MIRROR_BASE).collect { p ->
                                            downloadLine = progressText(context, p)
                                            if (p.error != null) err = p.error
                                        }
                                    }
                                } catch (e: Exception) {
                                    err = e.message ?: e.javaClass.simpleName
                                } finally {
                                    downloading = false
                                    refreshTick++
                                    val ready = VmBundle.isReady(context) && QemuBinaries.isPresent(context)
                                    downloadLine = when {
                                        err != null -> context.getString(R.string.bundle_failed, err.orEmpty())
                                        ready -> context.getString(R.string.bundle_done)
                                        else -> context.getString(R.string.bundle_incomplete)
                                    }
                                }
                            }
                        },
                        enabled = !downloading,
                    ) {
                        Text(
                            stringResource(
                                if (downloading) R.string.bundle_downloading else R.string.bundle_download
                            )
                        )
                    }
                    if (downloading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    downloadLine?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- 更新 ----
            SectionCard(title = stringResource(R.string.sec_update), icon = Icons.Rounded.SystemUpdate) {
                KeyValueRow(
                    stringResource(R.string.update_current),
                    checker.currentVersion,
                    mono = true,
                )
                Spacer(Modifier.height(8.dp))
                PodroidTonalButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        cs.launch {
                            updateState = checker.check()
                            checking = false
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (checking) R.string.update_checking else R.string.update_check
                        )
                    )
                }

                val st = updateState
                if (st != null) {
                    Spacer(Modifier.height(8.dp))
                    when (st) {
                        is UpdateChecker.Result.UpToDate -> HintRow(
                            stringResource(R.string.update_latest),
                            tone = Tone.Success,
                        )
                        is UpdateChecker.Result.Failed -> HintRow(
                            stringResource(R.string.update_failed, st.message),
                            tone = Tone.Danger,
                        )
                        is UpdateChecker.Result.Newer -> {
                            HintRow(
                                stringResource(R.string.update_available, st.release.version),
                                tone = Tone.Info,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    openUrl(context, st.release.url.ifBlank {
                                        "https://github.com/${UpdateChecker.REPO}/releases"
                                    })
                                },
                            ) { Text(stringResource(R.string.update_go)) }
                        }
                    }
                }
            }

            // ---- 诊断 ----
            SectionCard(title = stringResource(R.string.sec_diag), icon = Icons.Rounded.BugReport) {
                Text(
                    stringResource(R.string.diag_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PodroidTonalButton(onClick = {
                    exportedPath = Diagnostics.export(context).absolutePath
                }) { Text(stringResource(R.string.diag_export)) }
                exportedPath?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.diag_exported, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 关于 ----
            SectionCard(title = stringResource(R.string.sec_about), icon = Icons.Rounded.Info) {
                KeyValueRow(
                    stringResource(R.string.kv_version),
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    mono = true,
                )
                KeyValueRow(
                    stringResource(R.string.about_form),
                    "QEMU (TCG) + Alpine + Podman/crun",
                    stacked = true,
                )
                KeyValueRow(
                    stringResource(R.string.about_arch),
                    "arm64 · amd64 (binfmt + qemu-x86_64)",
                    stacked = true,
                )
            }
        }

        // 底部保存条：只有文本类改动会点亮
        AnimatedVisibility(dirty) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_unsaved),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!canSave) {
                            Text(
                                stringResource(R.string.settings_fix_errors),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            dnsDraft = settings.dnsServers
                            mirrorDraft = settings.mirrors
                            portDraft = settings.enginePort.toString()
                            customDns = false
                            customMirror = false
                        },
                    ) { Text(stringResource(R.string.settings_discard)) }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            cs.launch {
                                settingsRepo.setDns(dnsDraft.trim())
                                settingsRepo.setMirrors(mirrorDraft.trim())
                                settingsRepo.setPort(portDraft.trim().toInt())
                                needsRestart = true
                            }
                        },
                    ) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }

    // 崩溃日志查看
    crashLogText?.let { text ->
        PodroidDialog(
            title = stringResource(R.string.crash_log),
            onDismiss = { crashLogText = null },
            footer = {
                TextButton(onClick = { crashLogText = null }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        ) {
            Text(
                text.ifBlank { stringResource(R.string.crash_log_none) },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "当前值 + 自定义入口" 展示块。
 *
 * 不用「左标签右值」那一行：镜像站列表很长，右对齐会被省略号截掉，
 * 用户看不到到底用了哪几个站。改成整行等宽文本，最多三行。
 */
@Composable
private fun CurrentValue(label: String, value: String, onCustom: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onCustom,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) { Text(stringResource(R.string.common_custom), style = MaterialTheme.typography.labelLarge) }
    }
    Text(
        value,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---- 外部动作 ----

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.app(): android.app.Application =
    (applicationContext as? android.app.Application)
        ?: error("Application context expected")

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 申请加入电池优化白名单；厂商砍掉该页时回退到列表页。 */
private fun requestIgnoreBattery(context: Context) {
    val pkg = context.packageName
    val direct = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    if (direct.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---- 校验 ----

private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
private val HOST = Regex("""^[A-Za-z0-9]([A-Za-z0-9\-.]*[A-Za-z0-9])?(:\d{1,5})?$""")

private fun validateDns(context: Context, raw: String): String? {
    val list = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (list.isEmpty()) return context.getString(R.string.setting_dns_empty)
    list.forEach {
        if (!IPV4.matches(it) || it.split('.').any { p -> p.toIntOrNull()?.let { v -> v > 255 } ?: true }) {
            return context.getString(R.string.setting_dns_invalid, it)
        }
    }
    return null
}

private fun validateMirrors(context: Context, raw: String): String? {
    if (raw.isBlank()) return null // 空 = 自动
    val list = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    list.forEach {
        if (it.contains("://")) return context.getString(R.string.setting_mirror_scheme, it)
        if (!HOST.matches(it)) return context.getString(R.string.setting_mirror_invalid, it)
    }
    return null
}

private fun validatePort(context: Context, raw: String): String? {
    val v = raw.trim().toIntOrNull() ?: return context.getString(R.string.setting_port_nan)
    return if (v in 1024..65535) null else context.getString(R.string.setting_port_range)
}

/** 下载进度文案：统一按 MB 显示，避免 KB 数值刷屏。 */
private fun progressText(context: Context, p: BundleDownloader.DownloadProgress): String {
    val mb = 1024.0 * 1024.0
    return when {
        p.error != null -> context.getString(R.string.bundle_failed, p.error)
        p.done -> context.getString(R.string.bundle_file_done, p.file)
        p.bytesTotal > 0 -> context.getString(
            R.string.bundle_progress, p.file, p.bytesDone / mb, p.bytesTotal / mb,
        )
        else -> context.getString(R.string.bundle_progress_nolen, p.file, p.bytesDone / mb)
    }
}
