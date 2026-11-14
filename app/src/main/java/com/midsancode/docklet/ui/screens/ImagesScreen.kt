package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.docker.model.ImageSummary
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.VmStatus
import com.midsancode.docklet.ui.components.DockletDialog
import com.midsancode.docklet.ui.components.DockletTonalButton
import com.midsancode.docklet.ui.components.EmptyState
import com.midsancode.docklet.ui.components.ScreenHeader
import com.midsancode.docklet.ui.components.dockletCardColors
import kotlinx.coroutines.launch

/**
 * 镜像列表 + 拉取/构建对话框。
 */
@Composable
fun ImagesScreen(repo: EngineRepository, modifier: Modifier = Modifier) {
    val state by repo.images.collectAsState()
    val engineOk by repo.engineAvailable.collectAsState()
    val vmStatus by VmManager.status.collectAsState()
    var showPullDialog by remember { mutableStateOf(false) }
    var showBuildDialog by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.images_title),
            subtitle = if (state.items.isEmpty()) stringResource(R.string.images_sub_empty)
            else stringResource(
                R.string.images_sub_count,
                state.items.size,
                formatSize(state.items.sumOf { it.Size }),
            ),
            trailing = {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { repo.refreshImages() }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                    )
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockletTonalButton(onClick = { showPullDialog = true }, enabled = engineOk) {
                Text(stringResource(R.string.images_pull))
            }
            DockletTonalButton(onClick = { showBuildDialog = true }, enabled = engineOk) {
                Text(stringResource(R.string.images_build))
            }
        }

        when {
            vmStatus !is VmStatus.Ready -> EmptyState(
                icon = Icons.Rounded.Inventory2,
                title = stringResource(R.string.images_not_ready_title),
                message = when (vmStatus) {
                    is VmStatus.Stopped -> stringResource(R.string.images_not_ready_stopped)
                    is VmStatus.Booting -> stringResource(R.string.images_not_ready_booting)
                    is VmStatus.Error -> stringResource(R.string.images_not_ready_error)
                    else -> "…"
                },
            )

            !engineOk -> EmptyState(
                icon = Icons.Rounded.Inventory2,
                title = stringResource(R.string.images_engine_off_title),
                message = stringResource(R.string.images_engine_off_msg),
            )

            state.items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Inventory2,
                title = stringResource(R.string.images_empty_title),
                message = stringResource(R.string.images_empty_msg),
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.Id }) { img -> ImageCard(img, repo) }
                item { Spacer(Modifier.size(80.dp)) }
            }
        }
    }

    if (showPullDialog) {
        PullDialog(repo, onDismiss = { showPullDialog = false })
    }
    if (showBuildDialog) {
        BuildDialog(repo, onDismiss = { showBuildDialog = false })
    }
}

@Composable
private fun ImageCard(img: ImageSummary, repo: EngineRepository) {
    Card(Modifier.fillMaxWidth(), colors = dockletCardColors()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val tag = img.RepoTags.firstOrNull()
                Text(
                    tag ?: "<none>:<none>",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tag != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatSize(img.Size), style = MaterialTheme.typography.labelSmall)
                    Text(
                        "sha256:${img.Id.removePrefix("sha256:").take(12)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = {
                val t = img.RepoTags.firstOrNull() ?: return@IconButton
                repo.removeImage(t)
            }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                )
            }
        }
    }
}

@Composable
private fun PullDialog(repo: EngineRepository, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var progressLine by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    val cs = rememberCoroutineScope()

    DockletDialog(
        title = stringResource(R.string.images_pull_title),
        onDismiss = { if (!pulling) onDismiss() },
        footer = {
            TextButton(enabled = !pulling, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = input.isNotBlank() && !pulling,
                onClick = {
                    pulling = true
                    cs.launch {
                        repo.pullImage(input.trim()).collect { p ->
                            progressLine = listOfNotNull(p.id?.take(12), p.status, p.progress)
                                .joinToString(" ")
                        }
                        pulling = false
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.images_pull_action)) }
        },
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.images_pull_label)) },
            placeholder = { Text("nginx:alpine") },
            singleLine = true,
            enabled = !pulling,
            modifier = Modifier.fillMaxWidth(),
        )
        if (pulling) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(progressLine ?: "…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
