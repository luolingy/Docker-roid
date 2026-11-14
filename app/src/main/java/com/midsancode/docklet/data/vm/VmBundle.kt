package com.midsancode.docklet.data.vm

import android.content.Context
import android.util.Log
import com.midsancode.docklet.data.settings.SettingsRepository
import java.io.File

/**
 * 一次 VM 启动所需的资源集合（内核、rootfs、initramfs、可写盘）+ 用户配置。
 *
 * 解析顺序：按需下载目录 filesDir/vm-bundle/<version>/
 *
 * 启动形态（见 QemuRunner.buildCommand）：
 *   -kernel Image -initrd initrd.cpio.gz -append "rdinit=/init ..."
 *   /dev/vda = rootfs（squashfs 只读，或 ext4 可写）
 *   /dev/vdb = data.ext4（持久化 /var/lib/containers，首次启动由 initramfs 内 mke2fs 格式化）
 */
data class VmBundle(
    val kernel: File,
    val rootfs: File,        // 只读底座（squashfs）或可写根（ext4）
    val initrd: File,        // initramfs：负责挂载 rootfs、准备可写层、拉起容器引擎
    val dataImage: File,     // ext4 持久盘（/dev/vdb）
    val ramMb: Int,
    val hostPort: Int,       // host->guest 默认转发端口
    val dnsServers: String,
    /** docker.io 镜像站（逗号分隔）；空串 = 用 boot.sh 内置默认。 */
    val mirrors: String = "",
    /** 容器默认网络模式：host / bridge。 */
    val networkMode: String = "host",
) {
    val qmpSocket: File get() = dataImage.parentFile!!.resolve("qmp.sock")
    val hostdSocket: File get() = dataImage.parentFile!!.resolve("hostd.sock")

    /** 根文件系统类型：由文件名后缀决定，必须与 -append 的 rootfstype 一致。 */
    val rootfsType: String get() = if (rootfs.name.endsWith(".squashfs")) "squashfs" else "ext4"

    /** squashfs 必须只读挂载。 */
    val rootfsReadOnly: Boolean get() = rootfsType == "squashfs"

    companion object {
        const val BUNDLE_VERSION = "0.1.0"
        private const val TAG = "VmBundle"
        private const val DEFAULT_RAM_MB = 1024

        /**
         * 持久盘默认 4 GB。稀疏文件，未写入前几乎不占空间；
         * guest 首次启动会 mke2fs 格式化，元数据约占 70~80 MB。
         * 手机 /data 通常很紧张，所以不用更大的默认值。
         */
        private const val DEFAULT_DISK_MB = 4096
        private const val DEFAULT_HOST_PORT = 2375

        // 注意：资产名不能带 .gz —— AAPT 会把 .gz 自动解压并去掉扩展名，
        // 导致 assets.open("xxx.gz") 直接 FileNotFoundException。
        const val INITRD_ASSET = "initrd.img"
        private const val INITRD_NAME = "initrd.img"

        fun bundleDir(context: Context): File =
            context.filesDir.resolve("vm-bundle").resolve(BUNDLE_VERSION)

        fun resolve(context: Context, settings: SettingsRepository? = null): VmBundle {
            val s = settings?.current?.value
            val ramMb = s?.ramMb ?: DEFAULT_RAM_MB
            val diskMb = s?.diskMb ?: DEFAULT_DISK_MB
            val port = s?.enginePort ?: DEFAULT_HOST_PORT
            val dns = s?.dnsServers ?: "223.5.5.5,119.29.29.29"
            val mirrors = s?.mirrors ?: ""
            val networkMode = s?.networkMode ?: "host"

            val dir = bundleDir(context)
            if (!dir.exists()) dir.mkdirs()

            val kernel = dir.resolve("Image")
            // 优先 squashfs（Release 实际发布的资产），回退 ext4
            val rootfs = if (dir.resolve("rootfs.squashfs").exists()) dir.resolve("rootfs.squashfs")
                         else dir.resolve("rootfs.ext4")
            val dataImage = dir.resolve("data.ext4")
            val initrd = ensureInitrd(context)

            // 可写盘：不存在则创建稀疏文件；已存在但比目标小则扩容（ext4 在 guest 内自动识别）
            val wantBytes = diskMb * 1024L * 1024L
            if (!dataImage.exists() || dataImage.length() < wantBytes) {
                runCatching {
                    java.io.RandomAccessFile(dataImage, "rw").use { it.setLength(wantBytes) }
                }.onFailure { Log.w(TAG, "创建/扩容 data.ext4 失败: ${it.message}") }
            }

            return VmBundle(
                kernel = kernel,
                rootfs = rootfs,
                initrd = initrd,
                dataImage = dataImage,
                ramMb = ramMb,
                hostPort = port,
                dnsServers = dns,
                mirrors = mirrors,
                networkMode = networkMode,
            )
        }

        /**
         * initramfs 随 APK 打包在 assets，首次使用时解到 vm-bundle 目录。
         * 幂等：大小一致就跳过，升级 APK 后自动更新。
         */
        fun ensureInitrd(context: Context): File {
            val dir = bundleDir(context)
            if (!dir.exists()) dir.mkdirs()
            val out = dir.resolve(INITRD_NAME)
            val assetLen = runCatching {
                context.assets.openFd(INITRD_ASSET).use { it.length }
            }.getOrNull()
            if (out.exists() && out.length() > 0 && (assetLen == null || out.length() == assetLen)) {
                return out
            }
            context.assets.open(INITRD_ASSET).use { input ->
                val tmp = File(dir, "$INITRD_NAME.tmp")
                tmp.outputStream().use { o -> input.copyTo(o) }
                if (!tmp.renameTo(out)) {
                    out.writeBytes(tmp.readBytes())
                    tmp.delete()
                }
            }
            Log.i(TAG, "initramfs 已解压: ${out.absolutePath} (${out.length()} bytes)")
            return out
        }

        /** 检查底座是否完整，缺失时 UI 应提示"需要下载底座"。 */
        fun isReady(context: Context): Boolean {
            val dir = bundleDir(context)
            val hasRoot = dir.resolve("rootfs.squashfs").exists() || dir.resolve("rootfs.ext4").exists()
            return dir.resolve("Image").exists() && hasRoot
        }
    }
}
