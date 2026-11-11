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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dockerroid.data.docker.DockerApi
import com.dockerroid.data.docker.EngineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 容器详情：日志（流式）/ 资源占用（stats 流）/ 挂载与端口（inspect 摘要）。
 */
@Composable
fun ContainerDetailScreen(
    repo: EngineRepository,
    containerId: String,
    modifier: Modifier = Modifier,
    runningState: String? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("日志", "资源", "详情")
    var renameOpen by remember { mutableStateOf(false) }
    var commitOpen by remember { mutableStateOf(false) }
    val isRunning = runningState == "running"
    val isPaused = runningState == "paused"

    Column(modifier.fillMaxSize()) {
        // 操作栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { if (isPaused) repo.unpauseContainer(containerId) else repo.pauseContainer(containerId) }) {
                Text(if (isPaused) "恢复" else "暂停", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { repo.restartContainer(containerId) }) { Text("重启", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { renameOpen = true }) { Text("重命名", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { commitOpen = true }) { Text("提交镜像", style = MaterialTheme.typography.bodySmall) }
        }
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> LogsPane(repo, containerId, Modifier.weight(1f))
            1 -> StatsPane(repo, containerId, Modifier.weight(1f))
            2 -> InspectPane(repo, containerId, Modifier.weight(1f))
        }
    }

    if (renameOpen) {
        SimpleInputDialog("重命名容器", "新名称", onDismiss = { renameOpen = false }) { name ->
            repo.renameContainer(containerId, name); renameOpen = false
        }
    }
    if (commitOpen) {
        SimpleInputDialog("提交为镜像", "镜像名:tag（如 myimg:latest）", onDismiss = { commitOpen = false }) { spec ->
            val parts = spec.split(":", limit = 2)
            repo.commitContainer(containerId, parts[0], parts.getOrElse(1) { "latest" })
            commitOpen = false
        }
    }
}

@Composable
private fun SimpleInputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onConfirm(v.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LogsPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    val lines = remember { mutableStateOf(listOf<String>()) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    DisposableEffect(id) {
        val job = scope.launch {
            repo.logs(id, follow = true)
                .catch { e -> lines.value = lines.value + "（流中断: ${e.message}）" }
                .collect { line ->
                    lines.value = (lines.value + line).takeLast(500)
                }
        }
        onDispose { job.cancel(); scope.cancel() }
    }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val cs2 = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
            var exportMsg by remember { mutableStateOf<String?>(null) }
            exportMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = {
                cs2.launch {
                    try {
                        val dir = ctx.getExternalFilesDir(null) ?: ctx.cacheDir
                        val f = repo.exportLogs(id, java.io.File(dir, "container-$id.log"))
                        withContext(Dispatchers.Main) { exportMsg = "已导出: ${f.absolutePath}" }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { exportMsg = "导出失败: ${e.message}" }
                    }
                }
            }) { Text("导出日志", style = MaterialTheme.typography.labelMedium) }
        }
        Text(
            lines.value.joinToString("\n").ifEmpty { "（暂无日志）" },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        )
    }
}

@Composable
private fun StatsPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    var sample by remember { mutableStateOf<DockerApi.StatsSample?>(null) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    DisposableEffect(id) {
        val job = scope.launch {
            repo.stats(id)
                .catch { /* 静默：容器未运行时报 409 */ }
                .collect { sample = it }
        }
        onDispose { job.cancel(); scope.cancel() }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = sample
        if (s == null) {
            Text("容器未运行，无统计数据。")
        } else {
            StatRow("CPU", "${s.cpuPercent} %")
            StatRow("内存", "%.1f MB / %.1f MB".format(s.memUsedMb, s.memLimitMb))
            val pct = if (s.memLimitMb > 0) s.memUsedMb / s.memLimitMb * 100 else 0.0
            StatRow("内存占比", "%.1f %%".format(pct))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InspectPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    val detail by repo.containerDetail(id).collectAsState(null)
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val d = detail
        if (d == null) {
            Text("加载中…")
            return@Column
        }
        InfoRow("ID", d.Id.take(19))
        InfoRow("名称", d.Name.removePrefix("/"))
        InfoRow("状态", d.State?.Status ?: "unknown")
        InfoRow("镜像", d.Config?.Image ?: "")
        d.State?.StartedAt?.takeIf { it.startsWith("2") }?.let {
            InfoRow("启动于", it.take(19).replace('T', ' '))
        }
        d.HostConfig?.PortBindings?.forEach { (containerPort, hostMaps) ->
            hostMaps.firstOrNull()?.let { m ->
                InfoRow("端口", "${m.HostPort} → ${containerPort}")
            }
        }
        d.Mounts.forEach { m ->
            InfoRow("挂载", "${m.Source} → ${m.Destination} (${m.Type}${if (m.RW) ", rw" else ""})")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
