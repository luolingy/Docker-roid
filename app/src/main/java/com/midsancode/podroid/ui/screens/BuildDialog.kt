package com.midsancode.podroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.podroid.R
import com.midsancode.podroid.data.docker.EngineRepository
import com.midsancode.podroid.ui.components.PodroidDialog
import com.midsancode.podroid.ui.components.HintRow
import com.midsancode.podroid.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Dockerfile 构建对话框：输入 Dockerfile + 镜像 tag，流式显示构建日志。
 *
 * 相比原版：改成可滚动全宽卡片、日志区自动跟随到底部（构建时不用手滑）、
 * 构建中禁止关闭避免误触丢日志。
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
    val logScroll = rememberScrollState()

    // 有新日志就滚到底，构建进度总是看最后一行
    LaunchedEffect(log) { logScroll.animateScrollTo(logScroll.maxValue) }

    PodroidDialog(
        title = stringResource(R.string.build_title),
        subtitle = stringResource(R.string.build_sub),
        onDismiss = { if (!building) onDismiss() },
        footer = {
            TextButton(enabled = !building, onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            Spacer(Modifier.size(8.dp))
            Button(
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
            ) { Text(stringResource(if (building) R.string.build_running else R.string.build_action)) }
        },
    ) {
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text(stringResource(R.string.build_tag)) },
            placeholder = { Text("myimg:latest") },
            singleLine = true,
            enabled = !building,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dockerfile,
            onValueChange = { dockerfile = it },
            label = { Text("Dockerfile") },
            enabled = !building,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        if (building || log.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (building) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(R.string.build_log), style = MaterialTheme.typography.labelMedium)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(10.dp),
            ) {
                Text(
                    log.ifEmpty { "…" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(logScroll),
                )
            }
        }

        HintRow(
            stringResource(R.string.build_hint),
            tone = Tone.Info,
        )
    }
}
