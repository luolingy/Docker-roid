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
 * VM 底座下载器：首次启动时从镜像源下载内核与 rootfs。
 *
 * 均衡档体积策略：APK 不含大文件（~55-75MB 之外的底座按需下载）。
 * 下载完成后校验 sha256（可选），写入 filesDir/vm-bundle/<version>/。
 */
class BundleDownloader(private val context: Context) {

    data class DownloadProgress(
        val file: String,
        val bytesDone: Long,
        val bytesTotal: Long,
        val done: Boolean = false,
        val error: String? = null,
    )

    /**
     * 下载完整底座。mirrorBase 形如 "https://dl.example.com/docker-roid/0.1.0"。
     * 文件名约定：Image / rootfs.squashfs。
     */
    fun downloadAll(mirrorBase: String): Flow<DownloadProgress> = flow {
        val dir = context.filesDir.resolve("vm-bundle").resolve(VmBundle.BUNDLE_VERSION)
        dir.mkdirs()

        for (name in listOf("Image", "rootfs.squashfs")) {
            val target = dir.resolve(name)
            if (target.exists() && target.length() > 0) {
                emit(DownloadProgress(name, 1, 1, done = true))
                continue
            }
            try {
                val url = "$mirrorBase/$name"
                download(url, target) { done, total ->
                    emit(DownloadProgress(name, done, total))
                }
                emit(DownloadProgress(name, 1, 1, done = true))
            } catch (e: Exception) {
                Log.e(TAG, "下载 $name 失败", e)
                target.delete()
                emit(DownloadProgress(name, 0, 0, error = e.message ?: "下载失败"))
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    private inline fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${conn.responseCode} for $url")
            }
            val total = conn.contentLengthLong
            val tmp = File(target.absolutePath + ".tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                target.writeBytes(tmp.readBytes())
                tmp.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "BundleDownloader"
    }
}
