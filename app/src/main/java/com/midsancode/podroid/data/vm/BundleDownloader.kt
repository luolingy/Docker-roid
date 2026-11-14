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
 * 镜像源配置：GitHub Release 直链 + 多个加速前缀（国内优先）。
 * 下载时按顺序尝试，任一成功即停止。
 */
object Mirrors {
    private const val RELEASE = "https://github.com/luolingy/Podroid/releases/latest/download"

    /** 加速前缀（按速度/稳定性排序），空串代表直链。 */
    private val PROXIES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "",  // 直链兜底
    )

    /** 返回某文件的所有候选 URL（加速优先，直链兜底）。 */
    fun candidates(fileName: String): List<String> {
        val direct = "$RELEASE/$fileName"
        return PROXIES.map { if (it.isEmpty()) direct else "$it$direct" }
    }
}

/**
 * VM 底座下载器：内核与 rootfs。支持多镜像回退。
 */
class BundleDownloader(private val context: Context) {

    data class DownloadProgress(
        val file: String,
        val bytesDone: Long,
        val bytesTotal: Long,
        val done: Boolean = false,
        val error: String? = null,
    )

    fun downloadAll(mirrorBase: String): Flow<DownloadProgress> = flow {
        // mirrorBase 参数保留兼容；实际用 Mirrors.candidates
        val dir = context.filesDir.resolve("vm-bundle").resolve(VmBundle.BUNDLE_VERSION)
        dir.mkdirs()

        // 每个逻辑资源给出候选资产名（按优先级）。rootfs 以 squashfs 为主，ext4 兜底。
        val targets: List<Pair<String, List<String>>> = listOf(
            "Image" to listOf("Image"),
            "rootfs" to listOf("rootfs.squashfs", "rootfs.ext4"),
        )

        for ((label, names) in targets) {
            // 已有任一候选就算就绪
            val existing = names.map { dir.resolve(it) }.firstOrNull { it.exists() && it.length() > 0 }
            if (existing != null) {
                emit(DownloadProgress(existing.name, 1, 1, done = true))
                continue
            }
            var ok = false
            var lastName = names.first()
            for (name in names) {
                lastName = name
                val target = dir.resolve(name)
                if (tryDownload(name, target) { done, total ->
                        emit(DownloadProgress(name, done, total))
                    }) {
                    emit(DownloadProgress(name, 1, 1, done = true))
                    ok = true
                    break
                }
            }
            if (!ok) {
                emit(DownloadProgress(lastName, 0, 0, error = "$label 所有镜像均失败"))
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 依次尝试候选 URL，成功返回 true。 */
    private suspend fun tryDownload(
        name: String,
        target: File,
        onProgress: suspend (Long, Long) -> Unit,
    ): Boolean {
        for (url in Mirrors.candidates(name)) {
            try {
                Log.i("BundleDownloader", "尝试: $url")
                downloadOnce(url, target) { d, t -> onProgress(d, t) }
                if (target.exists() && target.length() > 0) return true
            } catch (e: Exception) {
                Log.w("BundleDownloader", "$url 失败: ${e.message}")
                target.delete()
            }
        }
        return false
    }

    private suspend fun downloadOnce(url: String, target: File, onProgress: suspend (Long, Long) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val tmp = File(target.absolutePath + ".tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) onProgress(done, total)
                    }
                }
            }
            if (total > 0 && tmp.length() != total) throw java.io.IOException("大小不符 ${tmp.length()}/$total")
            if (!tmp.renameTo(target)) { target.writeBytes(tmp.readBytes()); tmp.delete() }
        } finally {
            conn.disconnect()
        }
    }
}
