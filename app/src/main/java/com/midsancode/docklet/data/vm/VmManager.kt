package com.midsancode.docklet.data.vm

import android.content.Context
import android.content.Intent
import android.util.Log
import com.midsancode.docklet.R
import com.midsancode.docklet.i18n.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VM 全局单例管理器。
 *
 * 职责：
 *  - 持有 VM 状态（StateFlow，供 UI 观察）
 *  - 触发前台服务启动/停止 QEMU
 *  - 接收来自 [com.midsancode.docklet.service.VmService] 的串口日志，驱动状态机
 *
 * 说明：真正的 QEMU 进程由 VmService（前台服务）持有，因为只有前台服务能长期
 * 保活 + 持 WakeLock。本类只做轻量状态协调，避免 UI 与进程生命周期的直接耦合。
 */
object VmManager {

    private const val TAG = "VmManager"

    private val _status = MutableStateFlow<VmStatus>(VmStatus.Stopped)
    val status: StateFlow<VmStatus> = _status.asStateFlow()

    /** 串口日志缓冲区（最近 N 行），供诊断与终端展示引导过程。 */
    private val _serialLog = MutableStateFlow<List<String>>(emptyList())
    val serialLog: StateFlow<List<String>> = _serialLog.asStateFlow()
    private val serialBuffer = ArrayDeque<String>()
    private const val MAX_SERIAL_LINES = 400

    private var appContext: Context? = null

    /**
     * 取文案用的 context。
     *
     * 不能直接用 [appContext]：Application 的 configuration 跟随**系统**语言，
     * 而应用内语言覆盖只套在 Activity 上（`LocaleHelper.wrap`）。
     * 之前启动阶段胶囊在英文界面里显示中文「容器引擎启动」就是这个原因。
     */
    private val i18n: Context? get() = appContext?.let { LocaleHelper.wrap(it) }

    /** QemuRunner 注册的串口写入器（App 生命周期内只有一个 runner）。 */
    @Volatile
    internal var consoleWriter: ((String) -> Unit)? = null

    /** 终端 → guest 串口输入。VM 未运行时丢弃。 */
    fun writeToConsole(text: String) {
        consoleWriter?.invoke(text)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- 由 UI 调用 ----

    fun startVm() {
        val ctx = appContext ?: return
        _status.value = VmStatus.Booting(null)
        appendSerial("[host] 请求启动 VM…")
        val intent = Intent(ctx, com.midsancode.docklet.service.VmService::class.java).apply {
            action = com.midsancode.docklet.service.VmService.ACTION_START
        }
        ctx.startForegroundService(intent)
    }

    fun stopVm() {
        val ctx = appContext ?: return
        appendSerial("[host] 请求停止 VM…")
        val intent = Intent(ctx, com.midsancode.docklet.service.VmService::class.java).apply {
            action = com.midsancode.docklet.service.VmService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    // ---- 由 VmService 回调 ----

    /** 服务已成功拉起 QEMU 进程。 */
    internal fun onProcessLaunched(pid: Int) {
        Log.i(TAG, "QEMU pid=$pid")
        appendSerial("[host] QEMU 已启动 (pid=$pid)")
    }

    /**
     * 收到一行串口/主机日志。解析启动阶段，推进状态机。
     */
    internal fun onSerialLine(line: String) {
        appendSerial(line)
        val stage = BootStage.match(line)
        when {
            stage == BootStage.READY -> {
                _status.value = VmStatus.Ready
                appendSerial("[host] VM 就绪")
            }

            stage != null -> {
                val label = i18n?.getString(stage.labelRes).orEmpty()
                _status.value = VmStatus.Booting(label)
                appendSerial("[host] 阶段：$label")
            }

            // QEMU 自身 panic / 崩溃关键字
            line.contains("Kernel panic", ignoreCase = true) ||
                line.contains("qemu: fatal", ignoreCase = true) -> {
                _status.value = VmStatus.Error(line.take(200))
            }
        }
    }

    /** 进程退出。 */
    internal fun onProcessExited(exitCode: Int) {
        appendSerial("[host] QEMU 退出，exit=$exitCode")
        _status.value = if (exitCode == 0) VmStatus.Stopped
        else VmStatus.Error(
            i18n?.getString(R.string.vm_exit_error, exitCode)
                ?: "VM exited (code=$exitCode)"
        )
    }

    internal fun onStartFailure(message: String) {
        _status.value = VmStatus.Error(message)
        appendSerial("[host] 启动失败：$message")
    }

    internal fun resetStopped() {
        _status.value = VmStatus.Stopped
    }

    /** 供 UI/终端占位使用：把一行调试信息追加到日志缓冲。 */
    fun onSerialLineDebug(line: String) {
        appendSerial(line)
    }

    /** 清空串口日志缓冲（终端的"清屏"）。 */
    fun clearSerialLog() {
        synchronized(serialBuffer) {
            serialBuffer.clear()
            _serialLog.value = emptyList()
        }
    }

    /** 命令历史（终端 ↑↓ 用，最多 100 条）。 */
    private val _history = mutableListOf<String>()
    fun addHistory(cmd: String) {
        if (cmd.isBlank()) return
        if (_history.lastOrNull() != cmd) _history.add(cmd)
        if (_history.size > 100) _history.removeAt(0)
    }
    fun history(): List<String> = synchronized(_history) { _history.toList() }

    // ---- 内部 ----

    private fun appendSerial(line: String) {
        synchronized(serialBuffer) {
            if (serialBuffer.size >= MAX_SERIAL_LINES) serialBuffer.removeFirst()
            serialBuffer.addLast(line)
            _serialLog.value = serialBuffer.toList()
        }
    }
}
