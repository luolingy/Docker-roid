package com.midsancode.docklet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.docker.model.CreateContainerRequest
import com.midsancode.docklet.data.docker.model.CreateHostConfig
import com.midsancode.docklet.data.docker.model.ExposedPort
import com.midsancode.docklet.data.docker.model.PortMap
import com.midsancode.docklet.data.docker.model.RestartPolicy
import com.midsancode.docklet.ui.components.DockletDialog
import com.midsancode.docklet.ui.components.HintRow
import com.midsancode.docklet.ui.theme.Tone

/**
 * 新建并运行容器（docker run 图形化）。
 *
 * 交互重做要点：
 *  1. 镜像名不用背 —— 直接从本地镜像里点选（最常做的事前置）；
 *  2. 端口/环境变量/挂载/网络/重启策略这些进阶项收进「高级选项」，默认收起，
 *     避免一上来就是 8 个输入框；
 *  3. 对话框改成可滚动卡片，字段再多也不会把按钮顶出屏幕。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunDialog(repo: EngineRepository, onDismiss: () -> Unit) {
    val images by repo.images.collectAsState()
    val context = LocalContext.current

    var image by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("") }       // KEY=VAL 每行一个
    var ports by remember { mutableStateOf("") }     // host:container 每行一个
    var binds by remember { mutableStateOf("") }     // /src:/dst 每行一个
    var restart by remember { mutableStateOf("no") } // no / always / unless-stopped
    var network by remember { mutableStateOf("") }   // 空 = 用引擎默认
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 本地镜像标签，去重后最多展示 8 个
    val localTags = remember(images.items) {
        images.items.mapNotNull { it.RepoTags.firstOrNull() }.distinct().take(8)
    }

    DockletDialog(
        title = stringResource(R.string.run_title),
        subtitle = stringResource(R.string.run_sub),
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = image.isNotBlank(),
                onClick = {
                    try {
                        val exposed = mutableMapOf<String, ExposedPort>()
                        val bindings = mutableMapOf<String, List<PortMap>>()
                        parseLines(ports).forEach { line ->
                            val parts = line.split(":")
                            if (parts.size != 2) {
                                throw IllegalArgumentException(
                                    context.getString(R.string.run_port_error, line)
                                )
                            }
                            val (host, container) = parts
                            val key = "$container/tcp"
                            exposed[key] = ExposedPort()
                            bindings[key] = listOf(PortMap(HostIp = "0.0.0.0", HostPort = host))
                        }
                        val req = CreateContainerRequest(
                            Image = image.trim(),
                            Cmd = cmd.trim().split(" ").filter { it.isNotBlank() }.ifEmpty { null },
                            Env = parseLines(env).ifEmpty { null },
                            ExposedPorts = exposed.ifEmpty { null },
                            HostConfig = CreateHostConfig(
                                PortBindings = bindings.ifEmpty { null },
                                Binds = parseLines(binds).ifEmpty { null },
                                NetworkMode = network.trim().ifBlank { null },
                                RestartPolicy = if (restart != "no") RestartPolicy(Name = restart) else null,
                            ),
                        )
                        repo.createAndStart(req)
                        onDismiss()
                    } catch (e: Exception) {
                        error = e.message
                    }
                },
            ) { Text(stringResource(R.string.run_create)) }
        },
    ) {
        OutlinedTextField(
            value = image,
            onValueChange = { image = it },
            label = { Text(stringResource(R.string.run_image)) },
            placeholder = { Text("nginx:alpine") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (localTags.isNotEmpty()) {
            Text(
                stringResource(R.string.run_local_images),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                localTags.forEach { tag ->
                    FilterChip(
                        selected = image == tag,
                        onClick = { image = tag },
                        label = {
                            Text(
                                tag.substringAfterLast('/'),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.run_name)) },
            placeholder = { Text("my-nginx") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it },
            label = { Text(stringResource(R.string.run_cmd)) },
            placeholder = { Text(stringResource(R.string.run_cmd_ph)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 高级选项折叠
        TextButton(
            onClick = { advanced = !advanced },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Icon(
                if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                Modifier.size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(
                    if (advanced) R.string.run_advanced_hide else R.string.run_advanced_show
                )
            )
        }

        AnimatedVisibility(advanced) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = ports,
                    onValueChange = { ports = it },
                    label = { Text(stringResource(R.string.run_ports)) },
                    placeholder = { Text("8080:80\n443:443") },
                    supportingText = { Text(stringResource(R.string.run_ports_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = env,
                    onValueChange = { env = it },
                    label = { Text(stringResource(R.string.run_env)) },
                    placeholder = { Text("TZ=Asia/Shanghai\nMYSQL_ROOT_PASSWORD=123456") },
                    supportingText = { Text(stringResource(R.string.run_env_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = binds,
                    onValueChange = { binds = it },
                    label = { Text(stringResource(R.string.run_binds)) },
                    placeholder = { Text("/data/web:/usr/share/nginx/html") },
                    supportingText = { Text(stringResource(R.string.run_binds_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = network,
                    onValueChange = { network = it },
                    label = { Text(stringResource(R.string.run_network)) },
                    placeholder = { Text(stringResource(R.string.run_network_ph)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.run_restart),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "no" to R.string.run_restart_no,
                        "always" to R.string.run_restart_always,
                        "unless-stopped" to R.string.run_restart_unless,
                    ).forEach { (key, labelRes) ->
                        FilterChip(
                            selected = restart == key,
                            onClick = { restart = key },
                            label = {
                                Text(
                                    stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }

        error?.let { HintRow(it, tone = Tone.Danger) }
    }
}

private fun parseLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }
