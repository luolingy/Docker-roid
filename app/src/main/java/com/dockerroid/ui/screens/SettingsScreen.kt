package com.dockerroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dockerroid.data.diag.Diagnostics
import com.dockerroid.data.vm.BundleDownloader
import com.dockerroid.data.vm.VmBundle
import kotlinx.coroutines.launch
import java.io.File

/** 底座镜像源：GitHub Release 直链（objects.githubusercontent.com 重定向）。 */
private const val MIRROR_BASE =
    "https://github.com/luolingy/Docker-roid/releases/latest/download"

/**
 * 设置页：VM 底座状态 / 资源摘要（P4 后段开放编辑）/ 诊断导出 / 关于。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bundleReady = VmBundle.isReady(context)
    var exportedPath by remember { mutableStateOf<String?>(null) }
    var downloadLine by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val cs = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("VM 底座", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (bundleReady) "已就绪（内核 + rootfs 齐备）"
                    else "未就绪：需要 Image + rootfs.squashfs（filesDir/vm-bundle/0.1.0/）",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!bundleReady) {
                    Button(
                        onClick = {
                            downloading = true
                            cs.launch {
                                BundleDownloader(context).downloadAll(MIRROR_BASE).collect { p ->
                                    downloadLine = if (p.error != null) "失败：${p.error}"
                                    else "${p.file}: ${p.bytesDone / 1024} / ${p.bytesTotal / 1024} KB"
                                    if (p.error != null || p.done) downloading = false
                                }
                            }
                        },
                        enabled = !downloading,
                    ) { Text("下载底座") }
                    downloadLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("资源", style = MaterialTheme.typography.titleMedium)
                Text("guest 内存 1024 MB / 可写盘 2 GB / 引擎端口 2375", style = MaterialTheme.typography.bodySmall)
                Text("（数值编辑 P4 后段开放）", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("诊断", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val f = Diagnostics.export(context)
                    exportedPath = f.absolutePath
                }) { Text("导出诊断日志") }
                exportedPath?.let {
                    Text("已导出：$it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text("Docker-roid 0.1.0", style = MaterialTheme.typography.bodySmall)
                Text("均衡档：QEMU(TCG) + Alpine + Podman/crun", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
