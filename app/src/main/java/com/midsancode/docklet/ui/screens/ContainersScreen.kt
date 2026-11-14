package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.docker.model.ContainerSummary
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.VmStatus
import com.midsancode.docklet.ui.components.EmptyState
import com.midsancode.docklet.ui.components.ScreenHeader
import com.midsancode.docklet.ui.components.StatusDot
import com.midsancode.docklet.ui.components.StatusPill
import com.midsancode.docklet.ui.components.dockletCardColors
import com.midsancode.docklet.ui.theme.stateTone

/**
 * 容器列表：状态灯 + 名称/镜像 + 启停/删除。
 * 数据来自 EngineRepository（自动轮询 + Ready 后刷新）。
 */
@Composable
fun ContainersScreen(
    repo: EngineRepository,
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit = {},
) {
    val state by repo.containers.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    val vmStatus by VmManager.status.collectAsState()
    // 委托属性不能 smart cast，先取快照
    val vs = vmStatus
    var showRunDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            // 引擎没连上时直接不显示：之前是个看着能点、点了没反应的死按钮。
            if (engineOk) {
                ExtendedFloatingActionButton(
                    onClick = { showRunDialog = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.action_run_container)) },
                )
            }
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.containers_title),
                subtitle = when {
                    vs !is VmStatus.Ready -> stringResource(R.string.containers_sub_vm_not_ready)
                    !engineOk -> stringResource(R.string.containers_sub_connecting)
                    else -> stringResource(
                        R.string.containers_sub_count,
                        state.items.size,
                        state.items.count { it.State == "running" },
                    )
                },
                trailing = {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { repo.refreshContainers() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                        )
                    }
                },
            )

            when {
                vs !is VmStatus.Ready -> EmptyState(
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.containers_empty_vm_title),
                    message = when (vs) {
                        is VmStatus.Stopped -> stringResource(R.string.containers_empty_vm_stopped)
                        is VmStatus.Booting -> stringResource(
                            R.string.containers_empty_vm_booting,
                            vs.bootStage ?: "…",
                        )
                        is VmStatus.Error -> stringResource(
                            R.string.containers_empty_vm_error,
                            vs.message,
                        )
                        else -> "…"
                    },
                )

                !engineOk -> EmptyState(
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.containers_empty_engine_title),
                    message = stringResource(R.string.containers_empty_engine_msg),
                )

                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.containers_empty_title),
                    message = stringResource(R.string.containers_empty_msg),
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.Id }) { c ->
                        ContainerCard(c, repo, onClick = { onOpenDetail(c.Id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showRunDialog) {
        RunDialog(repo, onDismiss = { showRunDialog = false })
    }
}

@Composable
private fun ContainerCard(c: ContainerSummary, repo: EngineRepository, onClick: () -> Unit = {}) {
    val tone = stateTone(c.State)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = dockletCardColors()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(tone, size = 9)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.Names.firstOrNull()?.removePrefix("/") ?: c.Id.take(12),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(c.State, tone)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                c.Image,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ports = c.Ports.filter { it.PublicPort != null }
                Text(
                    if (ports.isNotEmpty()) {
                        ports.joinToString("  ") { "${it.PublicPort}→${it.PrivatePort}/${it.Type}" }
                    } else {
                        c.Status
                    },
                    fontFamily = if (ports.isNotEmpty()) FontFamily.Monospace else null,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ports.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 操作区右对齐，图标按钮缩小到 34dp，避免抢标题的视觉重量
                IconButton(
                    onClick = { repo.startContainer(c.Id) },
                    enabled = c.State != "running",
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_start),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { repo.stopContainer(c.Id) },
                    enabled = c.State == "running" || c.State == "paused",
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.action_stop),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { repo.removeContainer(c.Id, force = false) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
