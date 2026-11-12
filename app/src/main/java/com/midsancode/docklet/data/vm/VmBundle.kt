package com.midsancode.docklet.data.vm

import android.content.Context
import com.midsancode.docklet.data.settings.SettingsRepository
import java.io.File

/**
 * 一次 VM 启动所需的资源集合（内核、rootfs、可写盘等）+ 用户配置。
 *
 * 解析顺序：按需下载目录 filesDir/vm-bundle/<version>/
 * 可写盘大小与 guest RAM 来自用户设置（DataStore），默认 512MB / 1024MB。
 */
data class VmBundle(
    val kernel: File,
    val rootfs: File,        // squashfs 只读底座（-drive readonly）
    val dataImage: File,     // ext4 可写（每次启动前若无则创建空文件）
    val ramMb: Int,
    val hostPort: Int,       // host->guest 默认转发端口
    val dnsServers: String,
) {
    val qmpSocket: File get() = dataImage.parentFile!!.resolve("qmp.sock")
    val hostdSocket: File get() = dataImage.parentFile!!.resolve("hostd.sock")

    companion object {
        const val BUNDLE_VERSION = "0.1.0"
        private const val DEFAULT_RAM_MB = 1024
        private const val DEFAULT_DISK_MB = 512
        private const val DEFAULT_HOST_PORT = 2375

        fun resolve(context: Context, settings: SettingsRepository? = null): VmBundle {
            val s = settings?.current?.value
            val ramMb = s?.ramMb ?: DEFAULT_RAM_MB
            val diskMb = s?.diskMb ?: DEFAULT_DISK_MB
            val port = s?.enginePort ?: DEFAULT_HOST_PORT
            val dns = s?.dnsServers ?: "1.1.1.1,8.8.8.8"

            val dir = context.filesDir.resolve("vm-bundle").resolve(BUNDLE_VERSION)
            if (!dir.exists()) dir.mkdirs()

            val kernel = dir.resolve("Image")
            val rootfs = dir.resolve("rootfs.squashfs")
            val dataImage = dir.resolve("data.ext4")

            // 可写盘：不存在则创建稀疏文件；已存在但比目标小则扩容（ext4 在 guest 内自动识别）
            if (!dataImage.exists()) {
                java.io.RandomAccessFile(dataImage, "rw").use { it.setLength(diskMb * 1024L * 1024L) }
            } else if (dataImage.length() < diskMb * 1024L * 1024L) {
                java.io.RandomAccessFile(dataImage, "rw").use { it.setLength(diskMb * 1024L * 1024L) }
            }

            return VmBundle(
                kernel = kernel,
                rootfs = rootfs,
                dataImage = dataImage,
                ramMb = ramMb,
                hostPort = port,
                dnsServers = dns,
            )
        }

        /** 检查底座是否完整，缺失时 UI 应提示"需要下载底座"。 */
        fun isReady(context: Context): Boolean {
            val dir = context.filesDir.resolve("vm-bundle").resolve(BUNDLE_VERSION)
            return dir.resolve("Image").exists() && dir.resolve("rootfs.squashfs").exists()
        }
    }
}
