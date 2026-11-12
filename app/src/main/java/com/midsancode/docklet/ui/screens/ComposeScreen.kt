package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * Compose 管理：编辑 docker-compose.yml，保存到 App 私有目录，导出到外部目录，
 * 并给出在终端里执行的命令提示（guest 内 podman-compose up -d）。
 */
@Composable
fun ComposeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val file = remember { File(context.filesDir, "compose/docker-compose.yml") }

    var yaml by remember {
        mutableStateOf(
            if (file.exists()) file.readText()
            else """
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
""".trimIndent()
        )
    }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Compose 管理", style = MaterialTheme.typography.titleMedium)
        Text(
            "编辑 docker-compose.yml 并保存。启动请在终端页执行：podman-compose -f <路径> up -d",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = yaml,
            onValueChange = { yaml = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                file.parentFile?.mkdirs()
                file.writeText(yaml)
                msg = "已保存: ${file.absolutePath}"
            }) { Text("保存") }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString("podman-compose -f ${file.absolutePath} up -d"))
                msg = "命令已复制到剪贴板"
            }) { Text("复制启动命令") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                cs.launch {
                    val dir = context.getExternalFilesDir(null) ?: context.cacheDir
                    val out = File(dir, "docker-compose.yml")
                    out.writeText(yaml); msg = "已导出: ${out.absolutePath}"
                }
            }) { Text("导出到文件") }
            TextButton(onClick = {
                yaml = """
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
""".trimIndent(); msg = "已重置为示例"
            }) { Text("示例模板") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}
