package com.midsancode.docklet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midsancode.docklet.R
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.VmStatus

/**
 * 终端：接近真实终端的体验。
 * - 黑底绿/白字等宽字体终端主题
 * - 单行输入框紧跟提示符，回车执行
 * - 命令历史 ↑↓ 翻
 * - 快捷工具栏：Ctrl+C / Tab / 清屏 / 复制
 * - 自动滚到底部
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    val serial by VmManager.serialLog.collectAsState()
    val status by VmManager.status.collectAsState()
    val ready = status is VmStatus.Ready
    val canInput = ready || status is VmStatus.Booting

    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var histIndex by remember { mutableStateOf(-1) }
    var histDraft by remember { mutableStateOf("") }

    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    // 终端配色
    val bg = Color(0xFF0B0F14)
    val fg = Color(0xFFCDD6DC)
    val promptColor = Color(0xFF4CAF50)

    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = fg)

    LaunchedEffect(serial.size) {
        if (serial.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    fun history() = VmManager.history()

    fun send() {
        val txt = inputValue.text
        if (txt.isEmpty()) { VmManager.writeToConsole(""); return }
        VmManager.addHistory(txt)
        VmManager.writeToConsole(txt)
        inputValue = TextFieldValue("")
        histIndex = -1; histDraft = ""
    }

    Column(modifier.fillMaxSize().background(bg)) {
        // 顶部状态条
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF11161D)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (ready) Color(0xFF4CAF50)
                        else if (status is VmStatus.Booting) Color(0xFFFFB300)
                        else Color(0xFFEF5350),
                        androidx.compose.foundation.shape.CircleShape,
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when (val s = status) {
                    is VmStatus.Ready -> "root@docklet:~#"
                    is VmStatus.Booting -> stringResource(R.string.terminal_booting, s.bootStage ?: "…")
                    is VmStatus.Error -> stringResource(R.string.terminal_error, s.message.take(40))
                    else -> stringResource(R.string.terminal_vm_not_running)
                },
                color = fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { VmManager.clearSerialLog() }, modifier = Modifier.height(28.dp)) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.terminal_clear),
                    tint = fg,
                )
            }
        }

        // 输出区
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
                .verticalScroll(scroll)
                .pointerInput(Unit) { detectTapGestures { focus.requestFocus(); keyboard?.show() } }
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = serial.takeLast(500).joinToString("\n")
                    .ifEmpty { stringResource(R.string.terminal_waiting) },
                style = mono,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 快捷工具栏
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TermKey("Ctrl+C") { VmManager.writeToConsole("\u0003") }
            TermKey("Tab") { VmManager.writeToConsole("\t") }
            TermKey("↑") {
                val h = history(); if (h.isNotEmpty()) {
                    if (histIndex == -1) { histDraft = inputValue.text; histIndex = h.size - 1 }
                    else if (histIndex > 0) histIndex--
                    inputValue = TextFieldValue(h[histIndex])
                }
            }
            TermKey("↓") {
                val h = history(); if (histIndex >= 0) {
                    histIndex++
                    if (histIndex >= h.size) { histIndex = -1; inputValue = TextFieldValue(histDraft) }
                    else inputValue = TextFieldValue(h[histIndex])
                }
            }
            TermKey(stringResource(R.string.terminal_clear)) { VmManager.clearSerialLog() }
            TermKey(stringResource(R.string.common_copy)) {
                clipboard.setText(AnnotatedString(serial.takeLast(200).joinToString("\n")))
            }
            TermKey("podman ps") { inputValue = TextFieldValue("podman ps -a") }
        }

        // 输入行
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF11161D)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$ ", color = promptColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            BasicTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                enabled = canInput,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = fg),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(promptColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            TermKey("↵") { send() }
        }
    }
}

@Composable
private fun TermKey(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = Color(0xFF8FB8D8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
