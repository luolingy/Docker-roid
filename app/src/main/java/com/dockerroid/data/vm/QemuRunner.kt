package com.dockerroid.data.vm

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
                    com.dockerroid.data.settings.SettingsRepository(context)
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
        addAll(
            listOf(
                "-machine", "virt",
                "-cpu", "max",
                "-smp", "2",
                "-m", "${b.ramMb}",
                "-nographic",                       // 串口 = stdio（双向）
                "-kernel", b.kernel.absolutePath,
                "-append", "console=ttyAMA0 root=/dev/vdb rootfstype=squashfs ro " +
                    "docker_roid.dns=${b.dnsServers} docker_roid.ram=${b.ramMb}",
                "-drive", "file=${b.dataImage.absolutePath},if=virtio,format=raw",
                "-drive", "file=${b.rootfs.absolutePath},if=virtio,format=raw,readonly=on",
                "-netdev", "user,id=net0,hostfwd=tcp::${b.hostPort}-:8080",
                "-device", "virtio-net-pci,netdev=net0",
                "-qmp", "unix:${b.qmpSocket.absolutePath},server=on,wait=off",
                "-chardev", "socket,id=hostd,path=${b.hostdSocket.absolutePath},server=on,wait=off",
                "-device", "virtio-serial-pci",
                "-device", "virtserialport,chardev=hostd,name=docker-roid.hostd",
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
