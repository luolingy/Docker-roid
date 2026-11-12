package com.midsancode.docklet.data.vm

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
enum class BootStage(val marker: String, val label: String) {
    KERNEL("Docklet: kernel up", "内核已加载"),
    BOOTSTRAP("Docklet: bootstrap done", "系统初始化"),
    NETWORK("Docklet: network up", "网络就绪"),
    ENGINE("Docklet: engine starting", "容器引擎启动"),
    READY("Docklet: READY", "就绪"),
    ;

    companion object {
        fun match(line: String): BootStage? =
            entries.firstOrNull { line.contains(it.marker) }
    }
}
