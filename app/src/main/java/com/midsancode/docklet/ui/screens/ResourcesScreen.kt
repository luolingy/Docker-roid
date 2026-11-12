package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.docker.model.CreateNetworkRequest
import com.midsancode.docklet.data.docker.model.CreateVolumeRequest
import com.midsancode.docklet.data.docker.model.NetworkSummary
import com.midsancode.docklet.data.docker.model.VolumeSummary

/**
 * 网络与卷的合并管理界面（P3）。
 * 两个区块：网络（bridge/host/自定义）+ 卷（local）。
 */
@Composable
fun ResourcesScreen(repo: EngineRepository, modifier: Modifier = Modifier) {
    val networks by repo.networks.collectAsState()
    val volumes by repo.volumes.collectAsState()
    var netDialog by remember { mutableStateOf(false) }
    var volDialog by remember { mutableStateOf(false) }
    var fwdDialog by remember { mutableStateOf(false) }
    var composeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { fwdDialog = true }) { Text("端口转发") }
                TextButton(onClick = { composeDialog = true }) { Text("Compose") }
            }
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = { netDialog = true }) {
                    Icon(Icons.Filled.Add, "新建网络")
                }
                FloatingActionButton(onClick = { volDialog = true }) {
                    Icon(Icons.Filled.Add, "新建卷")
                }
            }
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("网络 (${networks.items.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(networks.items, key = { it.Id.ifEmpty { it.Name } }) { n ->
                    NetworkCard(n) { repo.removeNetwork(n.Name) }
                }
            }

            Text("卷 (${volumes.items.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(volumes.items, key = { it.Name }) { v ->
                    VolumeCard(v) { repo.removeVolume(v.Name) }
                }
            }
        }
    }

    if (netDialog) {
        NameDialog(
            title = "新建网络",
            label = "网络名（如 mynet）",
            onDismiss = { netDialog = false },
        ) { name ->
            repo.createNetwork(CreateNetworkRequest(Name = name))
            netDialog = false
        }
    }
    if (volDialog) {
        NameDialog(
            title = "新建卷",
            label = "卷名（如 data1）",
            onDismiss = { volDialog = false },
        ) { name ->
            repo.createVolume(CreateVolumeRequest(Name = name))
            volDialog = false
        }
    }
    if (fwdDialog) {
        PortForwardDialog(onDismiss = { fwdDialog = false })
    }
    if (composeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { composeDialog = false },
            confirmButton = { TextButton(onClick = { composeDialog = false }) { Text("关闭") } },
            title = { Text("Compose") },
            text = { androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(500.dp)) { ComposeScreen() } },
        )
    }
}

@Composable
private fun NetworkCard(n: NetworkSummary, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(n.Name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                Text(
                    "driver=${n.Driver} scope=${n.Scope} containers=${n.Containers.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // 内置网络（bridge/host/none）通常不给删
            val builtin = n.Name in setOf("bridge", "host", "none")
            IconButton(onClick = onDelete, enabled = !builtin) { Icon(Icons.Filled.Delete, "删除") }
        }
    }
}

@Composable
private fun VolumeCard(v: VolumeSummary, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.Name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                Text("driver=${v.Driver}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
