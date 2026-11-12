package com.dockerroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.data.docker.model.ContainerSummary
import com.dockerroid.data.vm.VmManager
import com.dockerroid.data.vm.VmStatus

/**
 * 容器列表：状态灯 + 名称/镜像 + 启停/暂停/删除。
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
    var showRunDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (engineOk) showRunDialog = true },
            ) { Icon(Icons.Filled.Add, "新建容器") }
        },
    ) { inner ->
    Column(Modifier.padding(inner).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("容器 (${state.items.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { repo.refreshContainers() }) {
                Icon(Icons.Filled.Refresh, "刷新")
            }
        }

        when {
            vmStatus !is VmStatus.Ready -> NotReadyHint(vmStatus)
            !engineOk -> Text(
                "引擎未连接（等待 Podman system service…）",
                color = MaterialTheme.colorScheme.error,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.Id }) { c ->
                    ContainerCard(c, repo, onClick = { onOpenDetail(c.Id) })
                }
            }
        }
    }
    }

    if (showRunDialog) {
        RunDialog(repo, onDismiss = { showRunDialog = false })
    }
}

@Composable
private fun NotReadyHint(vmStatus: com.dockerroid.data.vm.VmStatus) {
    Text(
        when (vmStatus) {
            is com.dockerroid.data.vm.VmStatus.Stopped -> "VM 未启动，请到主页启动。"
            is com.dockerroid.data.vm.VmStatus.Booting -> "VM 启动中：${vmStatus.bootStage ?: "…"}"
            is com.dockerroid.data.vm.VmStatus.Error -> "VM 错误：${vmStatus.message}"
            else -> "…"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ContainerCard(c: ContainerSummary, repo: EngineRepository, onClick: () -> Unit = {}) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(stateColor(c.State), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    c.Names.firstOrNull()?.removePrefix("/") ?: c.Id.take(12),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                // 状态胶囊
                Box(
                    Modifier.background(stateColor(c.State).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(c.State, style = MaterialTheme.typography.labelSmall, color = stateColor(c.State))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { repo.startContainer(c.Id) }, enabled = c.State != "running", modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.PlayArrow, "启动")
                }
                IconButton(onClick = { repo.stopContainer(c.Id) }, enabled = c.State == "running" || c.State == "paused", modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Stop, "停止")
                }
                IconButton(onClick = { repo.removeContainer(c.Id, force = false) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, "删除")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                c.Image,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // 端口映射
            val ports = c.Ports.filter { it.PublicPort != null }
            if (ports.isNotEmpty()) {
                Text(
                    ports.joinToString("  ") { "${it.PublicPort}→${it.PrivatePort}/${it.Type}" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(c.Status, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun stateColor(state: String): Color = when (state) {
    "running" -> Color(0xFF2E7D32)
    "paused" -> Color(0xFFF9A825)
    "exited" -> Color(0xFF757575)
    "restarting" -> Color(0xFF1565C0)
    else -> Color(0xFF9E9E9E)
}
