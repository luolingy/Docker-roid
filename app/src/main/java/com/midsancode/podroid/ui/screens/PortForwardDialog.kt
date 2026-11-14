package com.midsancode.podroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.midsancode.podroid.R
import com.midsancode.podroid.data.vm.ForwardRules
import com.midsancode.podroid.data.vm.QmpClient
import com.midsancode.podroid.data.vm.VmBundle
import com.midsancode.podroid.ui.components.PodroidDialog
import com.midsancode.podroid.ui.components.HintRow
import com.midsancode.podroid.ui.components.StatusPill
import com.midsancode.podroid.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端口转发管理：列出已保存规则、添加（QMP 动态）、移除、一键清理。
 *
 * 交互重做要点：
 *  1. 两个端口输入框并排 + 中间箭头，一眼看出方向（手机 → VM）；
 *  2. 输入不合法时直接在输入框下方说明原因，而不是让「添加」按钮默默变灰；
 *  3. 规则列表用胶囊标出协议，删除按钮不再抢视觉。
 */
@Composable
fun PortForwardDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = rememberCoroutineScope()
    var hostPort by remember { mutableStateOf("") }
    var guestPort by remember { mutableStateOf("") }
    var resultLine by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(true) }
    var rules by remember { mutableStateOf(ForwardRules.list(context)) }

    fun refresh() { rules = ForwardRules.list(context) }

    val hp = hostPort.toIntOrNull()
    val gp = guestPort.toIntOrNull()
    val hostError = when {
        hostPort.isBlank() -> null
        hp == null -> stringResource(R.string.pf_err_digits)
        hp !in 1024..65535 -> stringResource(R.string.pf_err_host_range)
        else -> null
    }
    val guestError = when {
        guestPort.isBlank() -> null
        gp == null -> stringResource(R.string.pf_err_digits)
        gp !in 1..65535 -> stringResource(R.string.pf_err_guest_range)
        else -> null
    }
    val canAdd = hp in 1024..65535 && gp in 1..65535

    // 错误提示不放在 supportingText 里：两个输入框并排时它只有半个屏宽，
    // "需在 1024–65535" 会被折成两行。改成整行一条。
    val errorText = when {
        hostError != null -> stringResource(R.string.pf_err_host, hostError)
        guestError != null -> stringResource(R.string.pf_err_guest, guestError)
        else -> null
    }

    PodroidDialog(
        title = stringResource(R.string.pf_title),
        subtitle = stringResource(R.string.pf_sub),
        onDismiss = onDismiss,
        footer = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    ) {
        HintRow(stringResource(R.string.pf_hint))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = hostPort,
                onValueChange = { hostPort = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.pf_host_port)) },
                placeholder = { Text("8080") },
                singleLine = true,
                isError = hostError != null,
                modifier = Modifier.weight(1f),
            )
            Text(
                "→",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp),
            )
            OutlinedTextField(
                value = guestPort,
                onValueChange = { guestPort = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.pf_guest_port)) },
                placeholder = { Text("8080") },
                singleLine = true,
                isError = guestError != null,
                modifier = Modifier.weight(1f),
            )
        }

        errorText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = canAdd,
                onClick = {
                    val h = hostPort.toInt(); val g = guestPort.toInt()
                    cs.launch(Dispatchers.IO) {
                        val qmp = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                        val ok = qmp.addHostfwd("tcp", h, g); qmp.close()
                        if (ok) ForwardRules.add(context, ForwardRules.Rule("tcp", h, g))
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            resultOk = ok
                            resultLine = if (ok) context.getString(R.string.pf_added, h, g)
                            else context.getString(R.string.pf_failed)
                            refresh()
                        }
                    }
                },
            ) { Text(stringResource(R.string.pf_add)) }
            if (rules.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = {
                    cs.launch(Dispatchers.IO) {
                        val sock = VmBundle.resolve(context).qmpSocket.absolutePath
                        rules.forEach { r ->
                            val q = QmpClient(sock); q.removeHostfwd(r.proto, r.hostPort); q.close()
                        }
                        ForwardRules.clear(context)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            resultOk = true
                            resultLine = context.getString(R.string.pf_cleared)
                            refresh()
                        }
                    }
                }) { Text(stringResource(R.string.pf_clear_all)) }
            }
        }

        resultLine?.let {
            HintRow(it, tone = if (resultOk) Tone.Success else Tone.Danger)
        }

        Text(
            stringResource(R.string.pf_rules, rules.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rules.isEmpty()) {
            Text(
                stringResource(R.string.pf_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rules.forEach { r ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(r.proto, Tone.Info)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "${r.hostPort} → ${r.guestPort}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        cs.launch(Dispatchers.IO) {
                            val q = QmpClient(VmBundle.resolve(context).qmpSocket.absolutePath)
                            q.removeHostfwd(r.proto, r.hostPort); q.close()
                            ForwardRules.remove(context, r.proto, r.hostPort)
                            kotlinx.coroutines.withContext(Dispatchers.Main) { refresh() }
                        }
                    }) {
                        Text(
                            stringResource(R.string.common_delete),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
