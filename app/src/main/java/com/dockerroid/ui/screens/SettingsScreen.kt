package com.dockerroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dockerroid.data.diag.Diagnostics
import com.dockerroid.data.settings.SettingsRepository
import com.dockerroid.data.vm.BundleDownloader
import com.dockerroid.data.vm.VmBundle
import kotlinx.coroutines.launch

/** 底座镜像源：GitHub Release 直链。 */
private const val MIRROR_BASE =
    "https://github.com/luolingy/Docker-roid/releases/latest/download"

/**
 * 设置页：可编辑资源（内存/盘/端口/DNS/自启）+ 底座下载 + 诊断导出。
 */
@Composable
fun SettingsScreen(settingsRepo: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    val settings by settingsRepo.current.collectAsState()
    val bundleReady = VmBundle.isReady(context) && com.dockerroid.data.vm.QemuBinaries.isPresent(context)
    var exportedPath by remember { mutableStateOf<String?>(null) }
    var downloadLine by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var dnsInput by remember(settings.dnsServers) { mutableStateOf(settings.dnsServers) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        // ---- VM 底座 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("VM 底座", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (bundleReady) "已就绪（内核 + rootfs + QEMU 齐备）"
                    else "未就绪：需要 Image + rootfs.squashfs + qemu-system-aarch64",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!bundleReady) {
                    Button(
                        onClick = {
                            downloading = true
                            cs.launch {
                                // 先下 kernel+rootfs，再下 qemu
                                BundleDownloader(context).downloadAll(MIRROR_BASE).collect { p ->
                                    downloadLine = if (p.error != null) "失败：${p.error}"
                                    else "${p.file}: ${p.bytesDone / 1024} / ${p.bytesTotal / 1024} KB"
                                    if (p.error != null) { downloading = false; return@collect }
                                }
                                com.dockerroid.data.vm.QemuBinaries.downloadFrom(context, MIRROR_BASE).collect { p ->
                                    downloadLine = if (p.error != null) "失败：${p.error}"
                                    else "${p.file}: ${p.bytesDone / 1024} / ${p.bytesTotal / 1024} KB"
                                    if (p.error != null || p.done) downloading = false
                                }
                            }
                        },
                        enabled = !downloading,
                    ) { Text(if (downloading) "下载中…" else "下载底座（约 85MB）") }
                    downloadLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // ---- 资源（可编辑，重启 VM 生效）----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("资源（重启 VM 生效）", style = MaterialTheme.typography.titleMedium)

                Text("guest 内存：${settings.ramMb} MB", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.ramMb.toFloat(),
                    onValueChange = { v -> cs.launch { settingsRepo.setRam((v / 128).toInt() * 128) } },
                    valueRange = 256f..4096f,
                )

                Text("可写盘：${settings.diskMb} MB", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.diskMb.toFloat(),
                    onValueChange = { v -> cs.launch { settingsRepo.setDisk((v / 256).toInt() * 256) } },
                    valueRange = 512f..8192f,
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("随开机自动启动 VM", modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.autoStart,
                        onCheckedChange = { b -> cs.launch { settingsRepo.setAutoStart(b) } },
                    )
                }
            }
        }

        // ---- 网络 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络", style = MaterialTheme.typography.titleMedium)
                Text("引擎端口：${settings.enginePort}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    label = { Text("DNS（逗号分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { cs.launch { settingsRepo.setDns(dnsInput.trim()) } },
                    enabled = dnsInput.isNotBlank() && dnsInput != settings.dnsServers,
                ) { Text("保存 DNS") }
            }
        }

        // ---- 诊断 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("诊断", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    exportedPath = Diagnostics.export(context).absolutePath
                }) { Text("导出诊断日志") }
                exportedPath?.let {
                    Text("已导出：$it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- 关于 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text("Docker-roid 0.1.0", style = MaterialTheme.typography.bodySmall)
                Text("均衡档：QEMU(TCG) + Alpine + Podman/crun", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
