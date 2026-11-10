package com.dockerroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.data.docker.model.ImageSummary
import com.dockerroid.data.vm.VmManager
import com.dockerroid.data.vm.VmStatus
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 镜像列表 + 拉取对话框（POST /images/create 流式进度）。
 */
@Composable
fun ImagesScreen(repo: EngineRepository, modifier: Modifier = Modifier) {
    val state by repo.images.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    val vmStatus by VmManager.status.collectAsState()
    var showPullDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("镜像 (${state.items.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            TextButton(onClick = { showPullDialog = true }, enabled = engineOk) { Text("拉取") }
            IconButton(onClick = { repo.refreshImages() }) { Icon(Icons.Filled.Refresh, "刷新") }
        }

        when {
            vmStatus !is VmStatus.Ready -> Text(
                when (vmStatus) {
                    is VmStatus.Stopped -> "VM 未启动。"
                    is VmStatus.Booting -> "启动中…"
                    is VmStatus.Error -> "VM 错误"
                    else -> "…"
                }
            )
            !engineOk -> Text("引擎未连接", color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.Id }) { img -> ImageCard(img, repo) }
            }
        }
    }

    if (showPullDialog) {
        PullDialog(repo, onDismiss = { showPullDialog = false })
    }
}

@Composable
private fun ImageCard(img: ImageSummary, repo: EngineRepository) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    img.RepoTags.firstOrNull() ?: img.Id.take(19),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(formatSize(img.Size), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                val tag = img.RepoTags.firstOrNull() ?: return@IconButton
                repo.removeImage(tag)
            }) { Icon(Icons.Filled.Delete, "删除") }
        }
    }
}

@Composable
private fun PullDialog(repo: EngineRepository, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var progressLine by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    val cs = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!pulling) onDismiss() },
        title = { Text("拉取镜像") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("如 nginx:alpine") },
                    singleLine = true,
                    enabled = !pulling,
                )
                if (pulling) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(progressLine ?: "…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank() && !pulling,
                onClick = {
                    pulling = true
                    cs.launch {
                        repo.pullImage(input.trim()).collect { p ->
                            progressLine = listOfNotNull(
                                p.id?.take(12),
                                p.status,
                                p.progress,
                            ).joinToString(" ")
                        }
                        pulling = false
                        onDismiss()
                    }
                },
            ) { Text("拉取") }
        },
        dismissButton = {
            TextButton(enabled = !pulling, onClick = onDismiss) { Text("取消") }
        },
    )
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}
