package com.midsancode.docklet.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.vm.QemuBinaries
import com.midsancode.docklet.data.vm.VmBundle
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.VmStatus
import com.midsancode.docklet.ui.components.DockletTonalButton
import com.midsancode.docklet.ui.components.KeyValueRow
import com.midsancode.docklet.ui.components.SectionCard
import com.midsancode.docklet.ui.components.ThinUsageBar
import com.midsancode.docklet.ui.theme.Tone
import com.midsancode.docklet.ui.theme.toneColors
import org.koin.compose.koinInject

/**
 * 主页：VM 状态 + 引擎概览 + 启动日志。
 *
 * 视觉上做成"一张状态英雄卡 + 三宫格 + 引擎明细"，让用户一眼看到
 * 「能不能用」→「用了多少」→「出问题看日志」三层信息。
 */
@Composable
fun HomeScreen(
    repo: EngineRepository,
    modifier: Modifier = Modifier,
    onGoSettings: () -> Unit = {},
) {
    val status by VmManager.status.collectAsState()
    val serial by VmManager.serialLog.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    val info by repo.systemInfo.collectAsState()
    val df by repo.diskUsage.collectAsState()
    val freed by repo.lastPruneFreed.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // 英雄卡副标题要显示真实的引擎端口，所以这里读一下设置
    val settingsRepo: com.midsancode.docklet.data.settings.SettingsRepository = koinInject()
    val settings by settingsRepo.current.collectAsState()

    val bundleReady = remember(status) {
        VmBundle.isReady(context) && QemuBinaries.isPresent(context)
    }
    val running = status !is VmStatus.Stopped
    var logExpanded by remember { mutableStateOf(false) }
    val logScroll = rememberScrollState()

    // 日志跟随到底部
    LaunchedEffect(serial.size, logExpanded) {
        logScroll.animateScrollTo(logScroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        // ---- 状态英雄卡 ----
        StatusHero(
            status = status,
            engineOk = engineOk,
            running = running,
            port = settings.enginePort,
            onStart = { VmManager.startVm() },
            onStop = { VmManager.stopVm() },
            canStart = bundleReady,
        )

        if (!bundleReady) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.home_need_bundle_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_need_bundle_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    DockletTonalButton(onClick = onGoSettings) {
                        Text(stringResource(R.string.home_go_settings))
                    }
                }
            }
        }

        // ---- 三宫格 ----
        // /info 是慢路由（真机上 30~150 秒），不能等它回来才画卡片：
        // 否则引擎已经就绪、首页却整块空白，看着像坏了。
        if (engineOk) {
            val inf = info
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.stat_running),
                    value = inf?.ContainersRunning?.toString() ?: "—",
                    icon = Icons.Rounded.PlayArrow,
                    tone = Tone.Success,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_images),
                    value = inf?.Images?.toString() ?: "—",
                    icon = Icons.Rounded.Inventory2,
                    tone = Tone.Info,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_containers),
                    value = inf?.Containers?.toString() ?: "—",
                    icon = Icons.Rounded.Dns,
                    tone = Tone.Neutral,
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- 引擎明细 ----
            SectionCard(
                title = stringResource(R.string.engine_title),
                icon = Icons.Rounded.Storage,
                subtitle = if (inf == null) stringResource(R.string.engine_loading) else null,
            ) {
                KeyValueRow(stringResource(R.string.kv_version), inf?.ServerVersion?.ifBlank { "—" } ?: "—")
                KeyValueRow(stringResource(R.string.kv_arch), inf?.Architecture?.ifBlank { "—" } ?: "—")
                KeyValueRow(
                    stringResource(R.string.kv_containers),
                    inf?.let {
                        stringResource(
                            R.string.kv_containers_value,
                            it.ContainersRunning,
                            it.Containers,
                        )
                    } ?: "—",
                )
                KeyValueRow(stringResource(R.string.kv_images), inf?.Images?.toString() ?: "—")

                df?.let { d ->
                    val imgSize = d.Images.sumOf { it.Size }
                    val total = imgSize.coerceAtLeast(1)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.kv_disk), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(formatSize(imgSize), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    ThinUsageBar(imgSize.toFloat() / total)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { repo.prune() }) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.home_prune))
                    }
                    if (freed > 0) {
                        Text(
                            stringResource(R.string.home_pruned, formatSize(freed)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }

        // ---- 启动日志 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_boot_log), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(serial.joinToString("\n"))) },
                        enabled = serial.isNotEmpty(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { VmManager.clearSerialLog() },
                        enabled = serial.isNotEmpty(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { logExpanded = !logExpanded },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (logExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(
                                if (logExpanded) R.string.action_collapse else R.string.action_expand
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val shown = if (logExpanded) serial.takeLast(400) else serial.takeLast(6)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .heightIn(max = if (logExpanded) 320.dp else 132.dp)
                        .verticalScroll(logScroll)
                        .padding(12.dp)
                        .animateContentSize(),
                ) {
                    Text(
                        text = if (shown.isEmpty()) stringResource(R.string.home_no_output)
                        else shown.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 状态英雄卡：渐变底 + 大状态 + 主操作。 */
@Composable
private fun StatusHero(
    status: VmStatus,
    engineOk: Boolean,
    running: Boolean,
    port: Int,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val tone = when (status) {
        is VmStatus.Ready -> Tone.Success
        is VmStatus.Booting -> Tone.Warning
        is VmStatus.Error -> Tone.Danger
        is VmStatus.Stopped -> Tone.Neutral
    }
    val dotColor = toneColors(tone).content
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
    )
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(brush)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    statusText(status),
                    style = MaterialTheme.typography.titleLarge,
                    color = onHero,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                heroSubtitle(status, engineOk, running, port),
                style = MaterialTheme.typography.bodySmall,
                color = onHero.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStart, enabled = !running && canStart) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_start_vm))
                }
                OutlinedButton(onClick = onStop, enabled = running) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_stop_vm))
                }
            }
        }
    }
}

@Composable
private fun heroSubtitle(status: VmStatus, engineOk: Boolean, running: Boolean, port: Int): String =
    when (status) {
        is VmStatus.Ready ->
            if (engineOk) stringResource(R.string.hero_ready_engine, port)
            else stringResource(R.string.hero_ready_connecting)
        is VmStatus.Booting -> stringResource(R.string.hero_booting)
        is VmStatus.Stopped ->
            if (running) stringResource(R.string.hero_stopping)
            else stringResource(R.string.hero_stopped)
        is VmStatus.Error -> stringResource(R.string.hero_error)
    }

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    val c = toneColors(tone)
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = c.content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = c.content)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusText(status: VmStatus): String = when (status) {
    is VmStatus.Stopped -> stringResource(R.string.vm_status_stopped)
    is VmStatus.Booting -> status.bootStage ?: stringResource(R.string.vm_status_booting)
    is VmStatus.Ready -> stringResource(R.string.vm_status_ready)
    is VmStatus.Error -> stringResource(R.string.hero_status_error_short)
}
