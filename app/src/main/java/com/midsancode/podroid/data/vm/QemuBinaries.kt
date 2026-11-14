package com.midsancode.podroid.data.vm

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

    /** 下载/解压产物目录（额外库、share 数据）。 */
    fun rootDir(context: Context): File = context.filesDir.resolve("qemu")

    /** APK 内 jniLibs 打包名（伪装成 .so 以便系统解压到可执行的 nativeLibraryDir）。 */
    private const val JNI_LIB_NAME = "libqemu-system-aarch64.so"

    /**
     * 可执行文件路径：Android 只允许 exec nativeLibraryDir 里的原生库，
     * 所以 QEMU 以 jniLibs 形式随 APK 打包，安装后系统解压到这里。
     */
    fun targetPath(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, JNI_LIB_NAME)

    /** 解压产物中的原始二进制（下载模式下可能用到）。 */
    fun extractedBinary(context: Context): File =
        rootDir(context).resolve("bin").resolve("qemu-system-aarch64")

    /** 库目录（LD_LIBRARY_PATH，下载的依赖 .so）。 */
    fun libDir(context: Context): File = rootDir(context).resolve("lib")

    fun isPresent(context: Context): Boolean {
        val f = targetPath(context)
        return f.exists() && f.length() > 0
    }

    /** VM 启动路径：确保可用，返回可执行的 nativeLibraryDir 路径。 */
    fun ensureExtracted(context: Context): File {
        val out = targetPath(context)
        if (!out.exists()) {
            throw java.io.FileNotFoundException("QEMU 未随 APK 打包（libqemu-system-aarch64.so 缺失）")
        }
        out.setExecutable(true)
        ensureShareData(context)
        return out
    }

    /** 把 assets/qemu-share 下的 rom 文件解压到 filesDir/qemu/share/qemu（幂等）。 */
    fun ensureShareData(context: Context) {
        val shareDir = rootDir(context).resolve("share/qemu")
        if (shareDir.exists() && (shareDir.listFiles()?.isNotEmpty() == true)) return
        shareDir.mkdirs()
        try {
            val names = context.assets.list("qemu-share").orEmpty()
            for (n in names) {
                context.assets.open("qemu-share/$n").use { i ->
                    File(shareDir, n).outputStream().use { o -> i.copyTo(o) }
                }
            }
            Log.i("QemuBinaries", "已解压 ${names.size} 个 rom 文件")
        } catch (e: Exception) {
            Log.w("QemuBinaries", "解压 share 数据失败: ${e.message}")
        }
    }

    /** 从镜像源下载 qemu-bundle.tar.gz 并解压（多镜像回退，流式进度）。 */
    fun downloadFrom(context: Context, mirrorBase: String): Flow<BundleDownloader.DownloadProgress> = flow {
        if (isPresent(context)) {
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 1, 1, done = true)); return@flow
        }
        val tmp = File(context.cacheDir, BUNDLE_NAME)
        var got = false
        var lastErr: String? = null
        for (url in Mirrors.candidates(BUNDLE_NAME)) {
            try {
                Log.i("QemuBinaries", "尝试: $url")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 120_000
                conn.instanceFollowRedirects = true
                if (conn.responseCode !in 200..299) { conn.disconnect(); throw java.io.IOException("HTTP ${conn.responseCode}") }
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
                if (total > 0 && tmp.length() != total) throw java.io.IOException("大小不符")
                got = true; break
            } catch (e: Exception) {
                Log.w("QemuBinaries", "$url 失败: ${e.message}")
                lastErr = e.message
                tmp.delete()
            }
        }
        if (!got) {
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 0, 0, error = lastErr ?: "所有镜像下载失败")); return@flow
        }
        emit(BundleDownloader.DownloadProgress("qemu: 解压中…", 1, 1))
        try {
            TarExtractor.extractTarGz(tmp, rootDir(context))
            tmp.delete()
            extractedBinary(context).setExecutable(true)
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 1, 1, done = true))
            Log.i("QemuBinaries", "QEMU 就绪: ${extractedBinary(context)}")
        } catch (e: Exception) {
            emit(BundleDownloader.DownloadProgress(BUNDLE_NAME, 0, 0, error = "解压失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
