package com.dockerroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dockerroid.R
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.data.vm.QemuBinaries
import com.dockerroid.data.vm.VmBundle
import com.dockerroid.data.vm.VmManager
import com.dockerroid.data.vm.VmStatus

@Composable
fun HomeScreen(repo: EngineRepository, modifier: Modifier = Modifier) {
    val status by VmManager.status.collectAsState()
    val serial by VmManager.serialLog.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    val info by repo.systemInfo.collectAsState()
    val df by repo.diskUsage.collectAsState()
    val freed by repo.lastPruneFreed.collectAsState()
    val context = LocalContext.current
    // 状态变化时重算底座就绪（vm 状态变化即重组）
    val bundleReady = remember(status) {
        VmBundle.isReady(context) && QemuBinaries.isPresent(context)
    }
    val running = status !is VmStatus.Stopped

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Docker-roid", style = MaterialTheme.typography.headlineMedium)

        // VM 状态卡
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status)
                    Spacer(Modifier.size(8.dp))
                    Text(statusText(status), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { VmManager.startVm() }, enabled = !running && bundleReady) {
                        Text(stringResource(R.string.action_start_vm))
                    }
                    OutlinedButton(onClick = { VmManager.stopVm() }, enabled = running) {
                        Text(stringResource(R.string.action_stop_vm))
                    }
                }
                if (!bundleReady) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "底座未就绪（内核 + rootfs + QEMU）。请到设置页点“下载底座”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // 引擎系统信息（就绪时）
        if (engineOk && info != null) {
            // 三宫格统计
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("运行中", "${info!!.ContainersRunning}", Modifier.weight(1f), Color(0xFF2E7D32))
                StatTile("镜像", "${info!!.Images}", Modifier.weight(1f), Color(0xFF1D63ED))
                StatTile("全部容器", "${info!!.Containers}", Modifier.weight(1f), Color(0xFF6750A4))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("引擎", style = MaterialTheme.typography.titleMedium)
                    InfoLine("版本", info!!.ServerVersion.ifBlank { "—" })
                    InfoLine("容器", "${info!!.ContainersRunning} 运行 / ${info!!.Containers} 总")
                    InfoLine("镜像", "${info!!.Images}")
                    InfoLine("架构", info!!.Architecture.ifBlank { "—" })
                    df?.let { d ->
                        val imgSize = d.Images.sumOf { it.Size }
                        val volSize = 0L
                        val total = (imgSize + volSize).coerceAtLeast(1)
                        InfoLine("磁盘占用", formatSize(imgSize))
                        UsageBar("镜像", imgSize, total, Color(0xFF1D63ED))
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { repo.prune() }) { Text("清理未使用数据 (prune)") }
                    if (freed > 0) {
                        Text("已释放 ${formatSize(freed)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 串口日志
        Text("启动日志", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Text(
                text = if (serial.isEmpty()) "（尚无输出）" else serial.takeLast(60).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 简单占用条：label + 比例条 + 数值。 */
@Composable
private fun UsageBar(label: String, value: Long, total: Long, color: Color) {
    val frac = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(formatSize(value), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(color = Color(0xFFE0E0E0), size = size, cornerRadius = r)
            drawRoundRect(color = color, size = androidx.compose.ui.geometry.Size(size.width * frac, size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun StatusDot(status: VmStatus) {
    val color = when (status) {
        is VmStatus.Ready -> Color(0xFF2E7D32)
        is VmStatus.Booting -> Color(0xFFF9A825)
        is VmStatus.Error -> Color(0xFFC62828)
        is VmStatus.Stopped -> Color(0xFF757575)
    }
    Spacer(Modifier.size(12.dp).background(color, CircleShape))
}

@Composable
private fun statusText(status: VmStatus): String = when (status) {
    is VmStatus.Stopped -> stringResource(R.string.vm_status_stopped)
    is VmStatus.Booting -> status.bootStage ?: stringResource(R.string.vm_status_booting)
    is VmStatus.Ready -> stringResource(R.string.vm_status_ready)
    is VmStatus.Error -> "错误：${status.message}"
}
