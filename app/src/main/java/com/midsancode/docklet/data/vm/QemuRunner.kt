package com.midsancode.docklet.data.vm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * 负责实际拉起并管理 QEMU 进程。
 *
 * 均衡档：qemu-system-aarch64（TCG）+ virt 机器。
 * 串口复用 stdio：stdout→App（状态机解析），stdin←App（终端输入）。
 */
class QemuRunner(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var process: Process? = null
    private var consoleJob: Job? = null

    val isRunning: Boolean get() = process?.isAlive == true

    /** 终端/脚本向 guest 串口写一行（不含换行，自动补 \n）。 */
    fun writeConsole(text: String) {
        val p = process ?: return
        if (!p.isAlive) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                p.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8))
                p.outputStream.flush()
            }.onFailure { Log.w(TAG, "写串口失败", it) }
        }
    }

    fun start() {
        if (isRunning) {
            VmManager.onStartFailure("VM 已在运行")
            return
        }
        VmManager.consoleWriter = { text -> writeConsole(text) }
        scope.launch(Dispatchers.IO) {
            try {
                val qemu = QemuBinaries.ensureExtracted(context)
                val settings = try {
                    com.midsancode.docklet.data.settings.SettingsRepository(context)
                } catch (_: Exception) { null }
                val bundle = VmBundle.resolve(context, settings)
                val cmd = buildCommand(qemu, bundle)
                Log.i(TAG, "启动命令: " + cmd.joinToString(" "))
                VmManager.onSerialLine("[host] \$ " + cmd.joinToString(" "))

                val pb = ProcessBuilder(cmd)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                // QEMU 及依赖库都在 nativeLibraryDir（随 APK 打包），linker 自动搜索该目录。
                // 若还有下载的额外库，追加 filesDir/qemu/lib。
                val nativeLib = context.applicationInfo.nativeLibraryDir
                val extraLib = QemuBinaries.libDir(context)
                pb.environment()["LD_LIBRARY_PATH"] =
                    listOf(nativeLib, extraLib.absolutePath).joinToString(":")
                val shareDir = QemuBinaries.rootDir(context).resolve("share/qemu")
                if (shareDir.exists()) pb.environment()["QEMU_DATADIR"] = shareDir.absolutePath
                pb.environment()["PREFIX"] = QemuBinaries.rootDir(context).absolutePath
                val proc = pb.start()
                process = proc
                VmManager.onProcessLaunched(pidOf(proc))

                consoleJob = scope.launch(Dispatchers.IO) {
                    streamLines(proc.inputStream) { line ->
                        VmManager.onSerialLine(line)
                    }
                }

                val exit = proc.waitFor()
                consoleJob?.cancel()
                process = null
                VmManager.onProcessExited(exit)
            } catch (e: Exception) {
                Log.e(TAG, "启动 QEMU 失败", e)
                VmManager.onStartFailure(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        consoleJob?.cancel()
        VmManager.consoleWriter = null
        val p = process
        process = null
        if (p != null && p.isAlive) {
            p.destroy()
            scope.launch(Dispatchers.IO) {
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
                VmManager.resetStopped()
            }
        } else {
            VmManager.resetStopped()
        }
    }

    // ---- QEMU 命令行（均衡档默认参数） ----

    private fun buildCommand(qemu: File, b: VmBundle): List<String> = buildList {
        add(qemu.absolutePath)
        // QEMU 用 -L 指定 rom/bios 目录（打包在 filesDir/qemu/share/qemu）
        val romDir = QemuBinaries.rootDir(context).resolve("share/qemu")
        if (romDir.exists()) {
            addAll(listOf("-L", romDir.absolutePath))
        }
        // rootfs 盘：squashfs 必须只读；ext4 可写
        val rootDrive = buildString {
            append("file=").append(b.rootfs.absolutePath)
            append(",if=virtio,format=raw")
            if (b.rootfsReadOnly) append(",readonly=on")
        }
        // 持久盘：/dev/vdb，首次启动由 initramfs 内 mke2fs 格式化
        val dataDrive = "file=${b.dataImage.absolutePath},if=virtio,format=raw"
        // 内核 cmdline 里不能出现空格；镜像站用逗号分隔（boot.sh 会换成空格再写 registries.conf）。
        val extraCmdline = buildString {
            if (b.mirrors.isNotBlank()) append(" docklet.mirror=").append(b.mirrors.trim())
            if (b.networkMode.isNotBlank()) append(" docklet.network=").append(b.networkMode.trim())
        }
        addAll(
            listOf(
                "-machine", "virt",
                "-cpu", "max",
                "-smp", "2",
                "-m", "${b.ramMb}",
                "-nographic",                       // 串口 = stdio（双向）
                "-kernel", b.kernel.absolutePath,
                "-initrd", b.initrd.absolutePath,
                // rdinit：由 initramfs 的 /init 接管（挂 rootfs、准备可写层、拉起容器引擎）。
                // 不再用 init=/usr/local/bin/startvm —— 已发布的镜像里没有该文件。
                "-append", "console=ttyAMA0 rdinit=/init " +
                    "docklet.dns=${b.dnsServers} docklet.ram=${b.ramMb} " +
                    "docklet.disk=/dev/vdb docklet.rootfs=${b.rootfsType}" + extraCmdline,
                "-drive", rootDrive,                // -> /dev/vda
                "-drive", dataDrive,                // -> /dev/vdb
                // 注意：这里不要加 dns=…。加了会覆盖 SLIRP 的转发目标，实测会导致
                // guest DNS 直接超时（1.1.1.1 在国内常不可达）。不加则由 SLIRP 转发到
                // 手机自身的 DNS，guest 内 resolv.conf 首行写 10.0.2.3 即可。
                "-netdev", "user,id=net0,hostfwd=tcp::${b.hostPort}-:2375",
                "-device", "virtio-net-pci,netdev=net0",
                "-qmp", "unix:${b.qmpSocket.absolutePath},server=on,wait=off",
                "-chardev", "socket,id=hostd,path=${b.hostdSocket.absolutePath},server=on,wait=off",
                "-device", "virtio-serial-pci",
                "-device", "virtserialport,chardev=hostd,name=Docklet.hostd",
                "-no-reboot",                       // guest panic 直接退出，不要空转重启
            )
        )
    }

    private fun pidOf(proc: Process): Int = try {
        val f = proc.javaClass.getDeclaredField("pid")
        f.isAccessible = true
        f.getInt(proc)
    } catch (_: Exception) {
        -1
    }

    private fun streamLines(input: InputStream, onLine: (String) -> Unit) {
        input.bufferedReader().useLines { seq ->
            seq.forEach { onLine(it) }
        }
    }

    companion object {
        private const val TAG = "QemuRunner"
    }
}
