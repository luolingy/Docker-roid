package com.midsancode.podroid.data.diag

import android.content.Context
import android.os.Build
import com.midsancode.podroid.data.vm.VmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断日志导出（用户报障用）：
 * 设备信息 + VM 状态 + 最近串口日志 + 引擎配置摘要 → Downloads 或 cache。
 * 不含任何用户数据/镜像内容，仅系统与运行状态。
 */
object Diagnostics {

    fun export(context: Context): File {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.cacheDir
        val out = File(dir, "Podroid-diag-$ts.txt")

        val sb = StringBuilder()
        sb.appendLine("=== Podroid 诊断 ===")
        sb.appendLine("时间: $ts")
        sb.appendLine()
        sb.appendLine("--- 设备 ---")
        sb.appendLine("model: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("abi: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("16KB-pages: ${has16kPages()}")
        sb.appendLine()
        sb.appendLine("--- VM 状态 ---")
        sb.appendLine("status: ${VmManager.status.value}")
        sb.appendLine()
        sb.appendLine("--- 串口日志（最近 100 行）---")
        VmManager.serialLog.value.takeLast(100).forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("--- 底座 ---")
        val bundleDir = File(context.filesDir, "vm-bundle/0.1.0")
        sb.appendLine("bundle dir: ${bundleDir.absolutePath}")
        sb.appendLine("Image: ${fileStat(File(bundleDir, "Image"))}")
        sb.appendLine("rootfs.squashfs: ${fileStat(File(bundleDir, "rootfs.squashfs"))}")
        sb.appendLine("data.ext4: ${fileStat(File(bundleDir, "data.ext4"))}")
        sb.appendLine("qemu binary: ${fileStat(File(context.filesDir, "bin/qemu-system-aarch64"))}")

        out.writeText(sb.toString())
        return out
    }

    private fun fileStat(f: File): String =
        if (f.exists()) "存在, ${f.length() / 1024} KB" else "缺失"

    /** 16KB 内存页检测：真实读取内核页大小。 */
    private fun has16kPages(): Boolean = try {
        // getconf PAGE_SIZE 最可靠；_arm64 上 4K 设备返回 4096，16K 设备返回 16384
        val pageSize = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
        pageSize >= 16384
    } catch (_: Exception) {
        false
    }
}
