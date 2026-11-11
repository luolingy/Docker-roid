package com.dockerroid.data.vm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * QEMU 二进制管理：优先内置 assets（若有），否则从 Release 下载到 filesDir/bin/。
 * 与 [BundleDownloader] 同一镜像源。
 */
object QemuBinaries {
    private const val ASSET_NAME = "qemu-system-aarch64"
    private const val EXEC_NAME = "qemu-system-aarch64"

    /** 供 UI 显示的下载源（由 SettingsScreen 传入的 MIRROR_BASE 决定）。 */
    fun targetPath(context: Context): File =
        context.filesDir.resolve("bin").resolve(EXEC_NAME)

    fun isPresent(context: Context): Boolean {
        val f = targetPath(context)
        return f.exists() && f.length() > 0 && f.canExecute()
    }

    /**
     * 确保二进制可用：先看 assets，再下载。
     * 这是同步阻塞版本（VM 启动路径用）；下载请用 [downloadFrom]。
     */
    fun ensureExtracted(context: Context): File {
        val out = targetPath(context)
        if (out.exists() && out.canExecute()) return out

        out.parentFile?.mkdirs()
        // 1) 优先内置 assets
        try {
            context.assets.open(ASSET_NAME).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.setExecutable(true)
            return out
        } catch (_: Exception) { /* 无内置，走下载 */ }

        // 2) assets 无 → 必须已由 [downloadFrom] 下载
        throw java.io.FileNotFoundException(
            "qemu-system-aarch64 未就绪（请在设置中下载底座）"
        )
    }

    /**
     * 从镜像源下载 QEMU 二进制（流式进度）。
     * @param mirrorBase 如 https://github.com/.../releases/latest/download
     */
    fun downloadFrom(context: Context, mirrorBase: String): Flow<BundleDownloader.DownloadProgress> = flow {
        val out = targetPath(context)
        if (out.exists() && out.length() > 0) {
            out.setExecutable(true)
            emit(BundleDownloader.DownloadProgress("qemu-system-aarch64", 1, 1, done = true))
            return@flow
        }
        out.parentFile?.mkdirs()
        val url = "$mirrorBase/$ASSET_NAME"
        val tmp = File(out.absolutePath + ".tmp")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) emit(BundleDownloader.DownloadProgress(ASSET_NAME, done, total))
                    }
                }
            }
            conn.disconnect()
            if (!tmp.renameTo(out)) { out.writeBytes(tmp.readBytes()); tmp.delete() }
            out.setExecutable(true)
            emit(BundleDownloader.DownloadProgress(ASSET_NAME, 1, 1, done = true))
            Log.i("QemuBinaries", "QEMU 下载完成: ${out.absolutePath} (${out.length()} bytes)")
        } catch (e: Exception) {
            tmp.delete(); out.delete()
            emit(BundleDownloader.DownloadProgress(ASSET_NAME, 0, 0, error = e.message ?: "下载失败"))
        }
    }.flowOn(Dispatchers.IO)
}
