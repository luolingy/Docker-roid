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
 * QEMU 二进制管理：从 Release 下载 qemu-bundle.tar.gz（含 bin + 30+ 依赖 .so），
 * 解压到 filesDir/qemu/。运行 QEMU 时需把 lib 目录加入 LD_LIBRARY_PATH。
 */
object QemuBinaries {
    private const val BUNDLE_NAME = "qemu-bundle.tar.gz"
    private const val EXEC_NAME = "qemu-system-aarch64"

    /** QEMU 解压根目录（含 bin/ 与 lib/）。 */
    fun rootDir(context: Context): File = context.filesDir.resolve("qemu")

    /** 可执行文件路径。 */
    fun targetPath(context: Context): File = rootDir(context).resolve("bin").resolve(EXEC_NAME)

    /** 库目录（LD_LIBRARY_PATH）。 */
    fun libDir(context: Context): File = rootDir(context).resolve("lib")

    fun isPresent(context: Context): Boolean {
        val f = targetPath(context)
        return f.exists() && f.length() > 0 && f.canExecute()
    }

    /** VM 启动路径：确保可用（不下载，只校验/内置）。 */
    fun ensureExtracted(context: Context): File {
        val out = targetPath(context)
        if (out.exists() && out.canExecute()) return out

        // 尝试内置 assets 的 tar.gz
        try {
            val tmp = File(context.cacheDir, BUNDLE_NAME)
            context.assets.open(BUNDLE_NAME).use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            TarExtractor.extractTarGz(tmp, rootDir(context))
            tmp.delete()
            if (out.exists()) { out.setExecutable(true); return out }
        } catch (_: Exception) { /* 无内置 */ }

        throw java.io.FileNotFoundException(
            "QEMU 未就绪（请在设置中下载底座）"
        )
    }

    /** 从镜像源下载 qemu-bundle.tar.gz 并解压（流式进度）。 */
    fun downloadFrom(context: Context, mirrorBase: String): Flow<BundleDownloader.DownloadProgress> = flow {
        if (isPresent(context)) {
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 1, 1, done = true)); return@flow
        }
        val tmp = File(context.cacheDir, BUNDLE_NAME)
        try {
            val url = "$mirrorBase/$BUNDLE_NAME"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, done, total))
                    }
                }
            }
            conn.disconnect()
            emit(BundleDownloader.DownloadProgress("qemu: 解压中…", 1, 1))
            TarExtractor.extractTarGz(tmp, rootDir(context))
            tmp.delete()
            targetPath(context).setExecutable(true)
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 1, 1, done = true))
            Log.i("QemuBinaries", "QEMU 就绪: ${targetPath(context)}")
        } catch (e: Exception) {
            tmp.delete()
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 0, 0, error = e.message ?: "下载/解压失败"))
        }
    }.flowOn(Dispatchers.IO)
}
