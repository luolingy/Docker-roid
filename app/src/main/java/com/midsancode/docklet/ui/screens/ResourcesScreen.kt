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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.docker.model.CreateNetworkRequest
import com.midsancode.docklet.data.docker.model.CreateVolumeRequest
import com.midsancode.docklet.data.docker.model.NetworkSummary
import com.midsancode.docklet.data.docker.model.VolumeSummary
import com.midsancode.docklet.ui.components.DockletDialog
import com.midsancode.docklet.ui.components.EmptyState
import com.midsancode.docklet.ui.components.ScreenHeader
import com.midsancode.docklet.ui.components.StatusPill
import com.midsancode.docklet.ui.components.dockletCardColors
import com.midsancode.docklet.ui.theme.Tone

/**
 * 网络与卷的合并管理界面。
 *
 * 原来两个 LazyColumn 上下堆在一个 Column 里，滚动会互相抢手势；改成
 * SegmentedButton 分段，一次只看一类，FAB 也跟着切换语义。
 */
@Composable
fun ResourcesScreen(repo: EngineRepository, modifier: Modifier = Modifier) {
    val networks by repo.networks.collectAsState()
    val volumes by repo.volumes.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var netDialog by remember { mutableStateOf(false) }
    var volDialog by remember { mutableStateOf(false) }
    var fwdDialog by remember { mutableStateOf(false) }
    var composeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            // 引擎未连上时新建必然失败，按钮直接不出现，避免"点了没反应"
            if (engineOk) {
                ExtendedFloatingActionButton(
                    onClick = { if (tab == 0) netDialog = true else volDialog = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = {
                        Text(
                            stringResource(
                                if (tab == 0) R.string.resources_new_network
                                else R.string.resources_new_volume
                            )
                        )
                    },
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
                title = stringResource(R.string.resources_title),
                subtitle = stringResource(
                    R.string.resources_sub,
                    networks.items.size,
                    volumes.items.size,
                ),
                trailing = {
                    IconButton(onClick = { fwdDialog = true }) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = stringResource(R.string.port_forward),
                        )
                    }
                    IconButton(onClick = { composeDialog = true }) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = stringResource(R.string.resources_compose_title),
                        )
                    }
                },
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    icon = { Icon(Icons.Rounded.Lan, null, Modifier.size(16.dp)) },
                ) { Text(stringResource(R.string.resources_tab_networks)) }
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    icon = { Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp)) },
                ) { Text(stringResource(R.string.resources_tab_volumes)) }
            }

            if (tab == 0) {
                if (networks.items.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Lan,
                        title = stringResource(R.string.resources_empty_net_title),
                        message = stringResource(R.string.resources_empty_net_msg),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(networks.items, key = { it.Id.ifEmpty { it.Name } }) { n ->
                            NetworkCard(n) { repo.removeNetwork(n.Name) }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } else {
                if (volumes.items.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Folder,
                        title = stringResource(R.string.resources_empty_vol_title),
                        message = stringResource(R.string.resources_empty_vol_msg),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(volumes.items, key = { it.Name }) { v ->
                            VolumeCard(v) { repo.removeVolume(v.Name) }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (netDialog) {
        NameDialog(
            title = stringResource(R.string.resources_new_network),
            label = stringResource(R.string.resources_net_name),
            placeholder = "mynet",
            onDismiss = { netDialog = false },
        ) { name ->
            repo.createNetwork(CreateNetworkRequest(Name = name))
            netDialog = false
        }
    }
    if (volDialog) {
        NameDialog(
            title = stringResource(R.string.resources_new_volume),
            label = stringResource(R.string.resources_vol_name),
            placeholder = "data1",
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
        DockletDialog(
            title = stringResource(R.string.resources_compose_title),
            subtitle = stringResource(R.string.resources_compose_sub),
            onDismiss = { composeDialog = false },
            footer = {
                TextButton(onClick = { composeDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        ) {
            ComposeScreen()
        }
    }
}

@Composable
private fun NetworkCard(n: NetworkSummary, onDelete: () -> Unit) {
    val builtin = n.Name in setOf("bridge", "host", "none", "podman")
    Card(Modifier.fillMaxWidth(), colors = dockletCardColors()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.Name,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    StatusPill(
                        n.Driver,
                        if (builtin) Tone.Neutral else Tone.Info,
                    )
                }
                Text(
                    stringResource(R.string.resources_net_meta, n.Scope, n.Containers.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, enabled = !builtin) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                )
            }
        }
    }
}

@Composable
private fun VolumeCard(v: VolumeSummary, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = dockletCardColors()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        v.Name,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    StatusPill(v.Driver, Tone.Neutral)
                }
                if (v.Mountpoint.isNotBlank()) {
                    Text(
                        v.Mountpoint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    DockletDialog(
        title = title,
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text(stringResource(R.string.common_create)) }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
