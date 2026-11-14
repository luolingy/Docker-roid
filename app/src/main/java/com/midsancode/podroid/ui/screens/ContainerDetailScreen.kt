package com.midsancode.podroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.podroid.R
import com.midsancode.podroid.data.docker.DockerApi
import com.midsancode.podroid.data.docker.EngineRepository
import com.midsancode.podroid.ui.components.PodroidDialog
import com.midsancode.podroid.ui.components.EmptyState
import com.midsancode.podroid.ui.components.HintRow
import com.midsancode.podroid.ui.components.KeyValueRow
import com.midsancode.podroid.ui.components.SectionCard
import com.midsancode.podroid.ui.components.StatusPill
import com.midsancode.podroid.ui.components.ThinUsageBar
import com.midsancode.podroid.ui.theme.Tone
import com.midsancode.podroid.ui.theme.stateTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 容器详情：日志（流式）/ 资源占用（stats 流）/ 挂载与端口（inspect 摘要）/ 执行命令。
 *
 * 重做要点：
 *  1. 顶部先给状态胶囊，一眼看清这个容器在跑还是停了；
 *  2. 操作从一排文字按钮换成带图标的小按钮，暂停/重启/重命名/提交不再靠读字；
 *  3. 日志区自动跟到最新一行，并补了复制按钮（原来只能导出到文件再翻）；
 *  4. 资源页用占用条，百分比不用自己算。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContainerDetailScreen(
    repo: EngineRepository,
    containerId: String,
    modifier: Modifier = Modifier,
    runningState: String? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.detail_tab_logs,
        R.string.detail_tab_stats,
        R.string.detail_tab_info,
        R.string.detail_tab_exec,
    )
    var renameOpen by remember { mutableStateOf(false) }
    var commitOpen by remember { mutableStateOf(false) }
    val isRunning = runningState == "running"
    val isPaused = runningState == "paused"

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                when {
                    isRunning -> stringResource(R.string.state_running)
                    isPaused -> stringResource(R.string.state_paused)
                    runningState == "exited" -> stringResource(R.string.state_exited)
                    else -> runningState ?: stringResource(R.string.state_unknown)
                },
                stateTone(runningState ?: ""),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                containerId.take(12),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 操作栏：图标 + 短文案，横向可换行，窄屏也不会被裁掉
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallAction(
                label = stringResource(
                    if (isPaused) R.string.action_resume else R.string.action_pause
                ),
                icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                onClick = {
                    if (isPaused) repo.unpauseContainer(containerId)
                    else repo.pauseContainer(containerId)
                },
            )
            SmallAction(
                stringResource(R.string.action_restart),
                Icons.Rounded.RestartAlt,
            ) { repo.restartContainer(containerId) }
            SmallAction(
                stringResource(R.string.action_rename),
                Icons.Rounded.Terminal,
            ) { renameOpen = true }
            SmallAction(
                stringResource(R.string.action_commit),
                Icons.Rounded.Save,
            ) { commitOpen = true }
        }

        Spacer(Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = tabs.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(stringResource(t), style = MaterialTheme.typography.labelLarge) }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (tab) {
            0 -> LogsPane(repo, containerId, Modifier.weight(1f))
            1 -> StatsPane(repo, containerId, Modifier.weight(1f))
            2 -> InspectPane(repo, containerId, Modifier.weight(1f))
            3 -> CommandPane(repo, containerId, Modifier.weight(1f))
        }
    }

    if (renameOpen) {
        SimpleInputDialog(
            title = stringResource(R.string.detail_rename_title),
            label = stringResource(R.string.detail_rename_label),
            onDismiss = { renameOpen = false },
        ) { name ->
            repo.renameContainer(containerId, name); renameOpen = false
        }
    }
    if (commitOpen) {
        SimpleInputDialog(
            title = stringResource(R.string.detail_commit_title),
            label = stringResource(R.string.detail_commit_label),
            onDismiss = { commitOpen = false },
        ) { spec ->
            val parts = spec.split(":", limit = 2)
            repo.commitContainer(containerId, parts[0], parts.getOrElse(1) { "latest" })
            commitOpen = false
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 6.dp,
        ),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** 等宽日志/输出面板。 */
@Composable
private fun MonoPane(
    text: String,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        )
    }
}

@Composable
private fun CommandPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    var cmd by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                cmd, { cmd = it },
                label = { Text(stringResource(R.string.detail_exec_label)) },
                placeholder = { Text(stringResource(R.string.detail_exec_ph)) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(
                enabled = cmd.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        // 简单空格分词；引号场景建议用 sh -c
                        val parts = cmd.split(" ").filter { it.isNotBlank() }
                        out = repo.execCommand(id, parts)
                        busy = false
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (busy) "…" else stringResource(R.string.action_execute)) }
        }
        MonoPane(
            out.ifEmpty { stringResource(R.string.detail_exec_empty) },
            Modifier.weight(1f),
            color = if (out.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SimpleInputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var v by remember { mutableStateOf("") }
    PodroidDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(enabled = v.isNotBlank(), onClick = { onConfirm(v.trim()) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
    ) {
        OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LogsPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    val lines = remember { mutableStateOf(listOf<String>()) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 导出结果用独立状态，不再靠中文前缀猜成功与否（英文文案没有「已」）
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var exportOk by remember { mutableStateOf(false) }
    val logScroll = rememberScrollState()

    DisposableEffect(id) {
        val job = scope.launch {
            repo.logs(id, follow = true)
                .catch { e ->
                    val fmt = ctx.getString(R.string.detail_logs_stream_broken, e.message ?: "")
                    lines.value = lines.value + fmt
                }
                .collect { line ->
                    lines.value = (lines.value + line).takeLast(500)
                }
        }
        onDispose { job.cancel(); scope.cancel() }
    }

    // 有新日志就滚到底
    LaunchedEffect(lines.value.size) { logScroll.animateScrollTo(logScroll.maxValue) }

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_logs_lines, lines.value.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = lines.value.isNotEmpty(),
                onClick = {
                    clipboard.setText(AnnotatedString(lines.value.joinToString("\n")))
                    exportOk = true
                    exportMsg = ctx.getString(R.string.detail_logs_copied)
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.action_copy), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = {
                scope.launch {
                    try {
                        val dir = ctx.getExternalFilesDir(null) ?: ctx.cacheDir
                        val f = repo.exportLogs(id, java.io.File(dir, "container-$id.log"))
                        withContext(Dispatchers.Main) {
                            exportOk = true
                            exportMsg = ctx.getString(R.string.detail_logs_exported, f.absolutePath)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            exportOk = false
                            exportMsg = ctx.getString(R.string.detail_logs_export_failed, e.message ?: "")
                        }
                    }
                }
            }) {
                Icon(Icons.Rounded.Download, null, Modifier.size(15.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.action_export), style = MaterialTheme.typography.labelMedium)
            }
        }

        exportMsg?.let {
            HintRow(it, tone = if (exportOk) Tone.Success else Tone.Danger)
        }

        if (lines.value.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Terminal,
                title = stringResource(R.string.detail_logs_empty_title),
                message = stringResource(R.string.detail_logs_empty_msg),
            )
        } else {
            MonoPane(
                lines.value.joinToString("\n"),
                Modifier.weight(1f),
                logScroll,
            )
        }
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

    val s = sample
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s == null) {
            EmptyState(
                icon = Icons.Rounded.Terminal,
                title = stringResource(R.string.detail_stats_empty_title),
                message = stringResource(R.string.detail_stats_empty_msg),
            )
            return@Column
        }

        val memPct = if (s.memLimitMb > 0) (s.memUsedMb / s.memLimitMb * 100) else 0.0
        SectionCard(
            title = stringResource(R.string.detail_cpu),
            subtitle = stringResource(R.string.detail_cpu_sub),
            icon = Icons.Rounded.Speed,
        ) {
            Text(
                stringResource(R.string.detail_cpu_value, s.cpuPercent),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        SectionCard(title = stringResource(R.string.detail_mem), icon = Icons.Rounded.Memory) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.detail_mem_used_mb, s.memUsedMb),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.detail_mem_total, s.memLimitMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ThinUsageBar(
                fraction = (memPct / 100).toFloat(),
                color = if (memPct > 85) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.detail_mem_used, memPct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InspectPane(repo: EngineRepository, id: String, modifier: Modifier = Modifier) {
    val detail by repo.containerDetail(id).collectAsState(null)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val d = detail
        if (d == null) {
            EmptyState(
                icon = Icons.Rounded.Terminal,
                title = stringResource(R.string.detail_loading_title),
                message = stringResource(R.string.detail_loading_msg),
            )
            return@Column
        }

        SectionCard(
            title = stringResource(R.string.detail_section_basic),
            icon = Icons.Rounded.Terminal,
        ) {
            KeyValueRow(stringResource(R.string.kv_id), d.Id.take(19), mono = true)
            KeyValueRow(stringResource(R.string.kv_name), d.Name.removePrefix("/"))
            KeyValueRow(
                stringResource(R.string.kv_state),
                d.State?.Status ?: stringResource(R.string.state_unknown),
            )
            // 镜像全名很长，走上下两行，避免把标签挤坏
            KeyValueRow(
                stringResource(R.string.kv_image),
                d.Config?.Image ?: "",
                mono = true, stacked = true,
            )
            d.State?.StartedAt?.takeIf { it.startsWith("2") }?.let {
                KeyValueRow(stringResource(R.string.kv_started), it.take(19).replace('T', ' '))
            }
        }

        if (d.HostConfig?.PortBindings?.isNotEmpty() == true || d.Mounts.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.detail_section_ports),
                icon = Icons.Rounded.Download,
            ) {
                d.HostConfig?.PortBindings?.forEach { (containerPort, hostMaps) ->
                    hostMaps.firstOrNull()?.let { m ->
                        KeyValueRow(
                            stringResource(R.string.kv_port),
                            "${m.HostPort} → $containerPort",
                            mono = true,
                        )
                    }
                }
                d.Mounts.forEach { m ->
                    // 挂载路径同样很长，必须上下排
                    KeyValueRow(
                        stringResource(R.string.kv_mount),
                        "${m.Source} → ${m.Destination} (${m.Type}${if (m.RW) ", rw" else ""})",
                        mono = true,
                        stacked = true,
                    )
                }
            }
        }
    }
}
