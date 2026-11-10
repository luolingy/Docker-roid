package com.dockerroid.data.vm

import android.content.Context
import java.io.File

/**
 * 把打包在 assets 里的 QEMU aarch64 二进制解出到可执行目录，并 chmod +x。
 * 均衡档裁剪后的 qemu-system-aarch64 约 10–12MB。
 */
object QemuBinaries {
    private const val ASSET_NAME = "qemu-system-aarch64"

    fun ensureExtracted(context: Context): File {
        val out = context.filesDir.resolve("bin").resolve(ASSET_NAME)
        if (out.exists() && out.canExecute()) return out

        out.parentFile?.mkdirs()
        context.assets.open(ASSET_NAME).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        out.setExecutable(true)
        return out
    }
}
