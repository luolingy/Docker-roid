package com.midsancode.docklet.data.vm

import androidx.annotation.StringRes
import com.midsancode.docklet.R

/**
 * VM 生命周期状态。由串口日志解析 + 进程状态共同驱动。
 */
sealed interface VmStatus {

    /** 未运行。 */
    data object Stopped : VmStatus

    /**
     * 正在启动。bootStage 来自串口日志（用户可见的引导进度）。
     * @param bootStage 最近一个已识别的启动阶段标记，如 "kernel", "network", "engine"
     */
    data class Booting(val bootStage: String? = null) : VmStatus

    /** 已就绪，Docker 引擎可访问。 */
    data object Ready : VmStatus

    /** 启动失败或运行中崩溃。 */
    data class Error(val message: String) : VmStatus
}

/**
 * 启动阶段标记。按顺序出现，来自 guest 串口输出。
 * 与 guest 内 /etc/init.d 的 Docklet-* 服务打印的标记一一对应。
 */
enum class BootStage(val marker: String, @StringRes val labelRes: Int) {
    KERNEL("Docklet: kernel up", R.string.boot_kernel),
    BOOTSTRAP("Docklet: bootstrap done", R.string.boot_bootstrap),
    NETWORK("Docklet: network up", R.string.boot_network),
    ENGINE("Docklet: engine starting", R.string.boot_engine),
    READY("Docklet: READY", R.string.boot_ready),
    ;

    companion object {
        fun match(line: String): BootStage? =
            entries.firstOrNull { line.contains(it.marker) }
    }
}
