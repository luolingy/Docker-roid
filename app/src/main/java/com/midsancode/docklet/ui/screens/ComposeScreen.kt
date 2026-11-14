package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.R
import com.midsancode.docklet.ui.components.HintRow
import com.midsancode.docklet.ui.theme.Tone
import java.io.File

private const val COMPOSE_SAMPLE = """services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
"""

/**
 * Compose 管理：编辑 docker-compose.yml，保存到 App 私有目录，导出到外部目录，
 * 并给出在终端里执行的命令提示（guest 内 podman-compose up -d）。
 *
 * 宿主是 DockletDialog，所以这里不再自己撑满屏幕，高度交给外层卡片决定。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val file = remember { File(context.filesDir, "compose/docker-compose.yml") }

    var yaml by remember {
        mutableStateOf(if (file.exists()) file.readText() else COMPOSE_SAMPLE)
    }
    // 提示语 + 是否成功：不能靠 startsWith("已") 判断，英文文案里没有这个前缀
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(true) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = yaml,
            onValueChange = { yaml = it },
            label = { Text(stringResource(R.string.compose_editor_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(yaml)
                }.onSuccess {
                    msgOk = true
                    msg = context.getString(R.string.compose_saved, file.absolutePath)
                }.onFailure {
                    msgOk = false
                    msg = it.message ?: it.javaClass.simpleName
                }
            }) { Text(stringResource(R.string.common_save)) }

            TextButton(onClick = {
                clipboard.setText(AnnotatedString("podman-compose -f ${file.absolutePath} up -d"))
                msgOk = true
                msg = context.getString(R.string.compose_cmd_copied)
            }) { Text(stringResource(R.string.compose_copy_cmd)) }

            TextButton(onClick = {
                runCatching {
                    val dir = context.getExternalFilesDir(null) ?: context.cacheDir
                    val out = File(dir, "docker-compose.yml")
                    out.writeText(yaml)
                    out
                }.onSuccess { out ->
                    msgOk = true
                    msg = context.getString(R.string.compose_exported, out.absolutePath)
                }.onFailure {
                    msgOk = false
                    msg = it.message ?: it.javaClass.simpleName
                }
            }) { Text(stringResource(R.string.action_export)) }

            TextButton(onClick = {
                yaml = COMPOSE_SAMPLE
                msgOk = true
                msg = context.getString(R.string.compose_reset)
            }) { Text(stringResource(R.string.compose_template)) }
        }

        msg?.let {
            HintRow(it, tone = if (msgOk) Tone.Success else Tone.Danger)
        }
    }
}
