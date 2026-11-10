package com.dockerroid.data.vm

import android.content.Context
import java.io.File

/**
 * 一次 VM 启动所需的资源集合（内核、rootfs、可写盘等）。
 *
 * 解析顺序：
 *  1. 按需下载目录 filesDir/vm-bundle/<version>/
 *  2. 内置 assets（如果某版本选择内置）
 *
 * 均衡档默认走"按需下载"，APK 本体不带大文件；P1 阶段可先用开发机手工放置文件。
 */
data class VmBundle(
    val kernel: File,
    val rootfs: File,        // squashfs 只读底座（-drive readonly）
    val dataImage: File,     // ext4 可写（每次启动前若无则创建空文件）
    val ramMb: Int,
    val hostPort: Int,       // host->guest 默认转发端口
) {
    val qmpSocket: File get() = dataImage.parentFile!!.resolve("qmp.sock")
    val hostdSocket: File get() = dataImage.parentFile!!.resolve("hostd.sock")

    companion object {
        const val BUNDLE_VERSION = "0.1.0"
        private const val DEFAULT_RAM_MB = 1024
        private const val DEFAULT_HOST_PORT = 8080
        private const val DATA_IMAGE_SIZE_MB = 512   // 512MB 起步（可调；原 2GB 对低存储设备过大）

        fun resolve(context: Context): VmBundle {
            val dir = context.filesDir.resolve("vm-bundle").resolve(BUNDLE_VERSION)
            if (!dir.exists()) dir.mkdirs()

            val kernel = dir.resolve("Image")
            val rootfs = dir.resolve("rootfs.squashfs")
            val dataImage = dir.resolve("data.ext4")

            // 首次启动：若可写盘不存在则创建一个稀疏文件（ext4 由 guest 内首次开机 mkfs，
            // 或在下载底座时一并提供已格式化的空盘）。这里仅确保文件存在并预分配大小。
            if (!dataImage.exists()) {
                java.io.RandomAccessFile(dataImage, "rw").use { it.setLength(DATA_IMAGE_SIZE_MB * 1024L * 1024L) }
            }

            return VmBundle(
                kernel = kernel,
                rootfs = rootfs,
                dataImage = dataImage,
                ramMb = DEFAULT_RAM_MB,
                hostPort = DEFAULT_HOST_PORT,
            )
        }

        /** 检查底座是否完整，缺失时 UI 应提示"需要下载底座"。 */
        fun isReady(context: Context): Boolean {
            val dir = context.filesDir.resolve("vm-bundle").resolve(BUNDLE_VERSION)
            return dir.resolve("Image").exists() && dir.resolve("rootfs.squashfs").exists()
        }
    }
}
