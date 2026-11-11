package com.dockerroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.data.docker.model.CreateContainerRequest
import com.dockerroid.data.docker.model.CreateHostConfig
import com.dockerroid.data.docker.model.PortMap

/**
 * 新建运行容器对话框（docker run 图形化）：
 * 镜像 / 名称 / 命令 / 环境变量 / 端口映射 / 卷挂载 / 重启策略。
 * 高级组合（compose/build 等）仍走终端。
 */
@Composable
fun RunDialog(repo: EngineRepository, onDismiss: () -> Unit) {
    var image by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("") }       // KEY=VAL 换行分隔
    var ports by remember { mutableStateOf("") }     // host:container 换行分隔
    var binds by remember { mutableStateOf("") }     // /src:/dst 换行分隔
    var restart by remember { mutableStateOf("no") } // no/always/unless-stopped
    var network by remember { mutableStateOf("") }   // 网络模式，空=默认
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建容器") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(image, { image = it }, label = { Text("镜像 *（如 nginx:alpine）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("容器名（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cmd, { cmd = it }, label = { Text("命令（可选，空格分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(env, { env = it }, label = { Text("环境变量（KEY=VAL，每行一个）") }, minLines = 1, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ports, { ports = it }, label = { Text("端口映射（8080:80，每行一个）") }, minLines = 1, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(binds, { binds = it }, label = { Text("挂载（/src:/dst，每行一个）") }, minLines = 1, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(network, { network = it }, label = { Text("网络（可选：bridge/host/自定义名）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RestartPicker(restart, { restart = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = image.isNotBlank(),
                onClick = {
                    try {
                        val exposed = mutableMapOf<String, com.dockerroid.data.docker.model.ExposedPort>()
                        val bindings = mutableMapOf<String, List<PortMap>>()
                        parseLines(ports).forEach { line ->
                            val parts = line.split(":")
                            if (parts.size != 2) throw IllegalArgumentException("端口格式错误：$line（应为 host:container）")
                            val (host, container) = parts
                            val key = "$container/tcp"
                            exposed[key] = com.dockerroid.data.docker.model.ExposedPort()
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
                                RestartPolicy = if (restart != "no")
                                    com.dockerroid.data.docker.model.RestartPolicy(Name = restart) else null,
                            ),
                        )
                        repo.createAndStart(req)
                        onDismiss()
                    } catch (e: Exception) {
                        error = e.message
                    }
                },
            ) { Text("创建并启动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RestartPicker(current: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("no", "always", "unless-stopped").forEach { opt ->
            TextButton(onClick = { onChange(opt) }) {
                Text(
                    if (opt == current) "[$opt]" else opt,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (opt == current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parseLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }
