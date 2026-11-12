package com.midsancode.docklet.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.midsancode.docklet.data.vm.ForwardRules
import com.midsancode.docklet.data.vm.QmpClient
import com.midsancode.docklet.data.vm.VmBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端口转发管理：列出已保存规则、添加（QMP 动态）、移除、一键清理。
 */
@Composable
fun PortForwardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    var hostPort by remember { mutableStateOf("") }
    var guestPort by remember { mutableStateOf("") }
    var resultLine by remember { mutableStateOf<String?>(null) }
    var rules by remember { mutableStateOf(ForwardRules.list(context)) }

    fun refresh() { rules = ForwardRules.list(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("端口转发") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "手机端口 → VM 端口。容器 -p 8080:80 后，把手机 8080 转到 VM 8080，局域网可访问。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(hostPort, { hostPort = it }, label = { Text("手机端口（8080）") }, singleLine = true)
                OutlinedTextField(guestPort, { guestPort = it }, label = { Text("VM 端口（8080）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = hostPort.toIntOrNull() in 1024..65535 && guestPort.toIntOrNull() in 1..65535,
                        onClick = {
                            val hp = hostPort.toInt(); val gp = guestPort.toInt()
                            cs.launch(Dispatchers.IO) {
                                val qmp = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                                val ok = qmp.addHostfwd("tcp", hp, gp); qmp.close()
                                if (ok) ForwardRules.add(context, ForwardRules.Rule("tcp", hp, gp))
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    resultLine = if (ok) "✓ 已添加 $hp→$gp" else "✗ QMP 失败（VM 未运行或版本不支持）"
                                    refresh()
                                }
                            }
                        },
                    ) { Text("添加") }
                    if (rules.isNotEmpty()) {
                        TextButton(onClick = {
                            cs.launch(Dispatchers.IO) {
                                val sock = VmBundle.resolve(context).qmpSocket.absolutePath
                                rules.forEach { r ->
                                    val q = QmpClient(sock); q.removeHostfwd(r.proto, r.hostPort); q.close()
                                }
                                ForwardRules.clear(context)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    resultLine = "✓ 已清理全部"; refresh()
                                }
                            }
                        }) { Text("全部清理") }
                    }
                }
                resultLine?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
                Text("当前规则 (${rules.size})", style = MaterialTheme.typography.labelMedium)
                rules.forEach { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp)) {
                            Text("${r.hostPort} → ${r.guestPort} (${r.proto})",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                cs.launch(Dispatchers.IO) {
                                    val q = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                                    q.removeHostfwd(r.proto, r.hostPort); q.close()
                                    ForwardRules.remove(context, r.proto, r.hostPort)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) { refresh() }
                                }
                            }) { Text("删除", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
