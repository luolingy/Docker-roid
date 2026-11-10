package com.dockerroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dockerroid.data.vm.VmManager
import com.dockerroid.data.vm.VmStatus

/**
 * 串口终端（P4 前段，行模式）：
 * 输入经 VmManager.writeToConsole → QEMU stdin → guest shell；
 * 输出经 QEMU stdout → VmManager.serialLog → 本页展示。
 * P4 后段替换为 Termux terminal-view 的完整 xterm 仿真。
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val serial by VmManager.serialLog.collectAsState()
    val status by VmManager.status.collectAsState()
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    // 新日志到达时滚到底部
    LaunchedEffect(serial.size) {
        if (serial.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (val s = status) {
                is VmStatus.Ready -> "终端（guest shell 就绪）"
                is VmStatus.Booting -> "终端（启动中：${s.bootStage ?: "…"}，输出只读）"
                else -> "终端（VM 未运行，输入无效）"
            },
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = serial.takeLast(200).joinToString("\n").ifEmpty { "[等待 VM 输出…]" },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(scroll)
                .padding(8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("命令") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        VmManager.writeToConsole(input)
                        input = ""
                    }
                },
                enabled = status is VmStatus.Ready || status is VmStatus.Booting,
            ) { Text("发送") }
        }
    }
}
