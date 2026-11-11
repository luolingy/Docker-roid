package com.dockerroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.dockerroid.data.vm.QmpClient
import com.dockerroid.data.vm.VmBundle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 端口转发管理对话框：通过 QMP 在 VM 运行中动态增删 hostfwd 规则。
 * 对应命令行 `docker-roid-forward add/remove`。
 */
@Composable
fun PortForwardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    var hostPort by remember { mutableStateOf("") }
    var guestPort by remember { mutableStateOf("") }
    var resultLine by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("端口转发（QMP 动态）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "将手机端口转发到 VM 内端口；容器 -p 8080:80 后，把手机 8080 转到 VM 8080，局域网即可访问。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(hostPort, { hostPort = it }, label = { Text("手机端口（如 8080）") }, singleLine = true)
                OutlinedTextField(guestPort, { guestPort = it }, label = { Text("VM 端口（如 8080）") }, singleLine = true)
                resultLine?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hostPort.toIntOrNull() in 1024..65535 && guestPort.toIntOrNull() in 1..65535,
                onClick = {
                    cs.launch(Dispatchers.IO) {
                        val qmp = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                        val ok = qmp.addHostfwd("tcp", hostPort.toInt(), guestPort.toInt())
                        qmp.close()
                        resultLine = if (ok) "✓ 已添加 ${hostPort}→${guestPort}（重启 VM 后失效）"
                        else "✗ QMP 添加失败（VM 未运行或 QEMU 版本不支持动态规则）"
                    }
                },
            ) { Text("添加") }
        },
        dismissButton = {
            Row {
                if (hostPort.toIntOrNull() != null) {
                    TextButton(onClick = {
                        cs.launch(Dispatchers.IO) {
                            val qmp = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                            val ok = qmp.removeHostfwd("tcp", hostPort.toInt())
                            qmp.close()
                            resultLine = if (ok) "✓ 已移除 ${hostPort}" else "✗ 移除失败"
                        }
                    }) { Text("移除") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
