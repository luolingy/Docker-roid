package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.data.docker.EngineRepository
import kotlinx.coroutines.launch

/**
 * Dockerfile 构建对话框：输入 Dockerfile + 镜像 tag，流式显示构建日志。
 */
@Composable
fun BuildDialog(repo: EngineRepository, onDismiss: () -> Unit) {
    var dockerfile by remember {
        mutableStateOf(
            "FROM alpine:latest\nRUN echo hello > /hello\nCMD [\"cat\", \"/hello\"]\n"
        )
    }
    var tag by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(false) }
    val cs = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!building) onDismiss() },
        title = { Text("构建镜像 (Dockerfile)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("镜像 tag（如 myimg:latest）") },
                    singleLine = true,
                    enabled = !building,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dockerfile,
                    onValueChange = { dockerfile = it },
                    label = { Text("Dockerfile") },
                    enabled = !building,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (building || log.isNotEmpty()) {
                    Row {
                        if (building) {
                            CircularProgressIndicator(Modifier.height(14.dp).padding(end = 6.dp), strokeWidth = 2.dp)
                        }
                        Text("构建日志", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        log.ifEmpty { "…" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                    )
                }
                Text(
                    "注意：构建上下文仅含 Dockerfile，COPY/ADD 引用本地文件会失败。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = tag.isNotBlank() && dockerfile.isNotBlank() && !building,
                onClick = {
                    building = true; log = ""
                    cs.launch {
                        repo.buildImage(dockerfile, tag.trim()).collect { line ->
                            log = (log + line + "\n").takeLast(8000)
                        }
                        building = false
                    }
                },
            ) { Text(if (building) "构建中…" else "构建") }
        },
        dismissButton = { TextButton(enabled = !building, onClick = onDismiss) { Text("关闭") } },
    )
}
