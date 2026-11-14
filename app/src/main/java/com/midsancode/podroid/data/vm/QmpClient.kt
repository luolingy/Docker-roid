package com.midsancode.podroid.data.vm

import android.net.LocalSocketAddress
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * QMP（QEMU Machine Protocol）客户端 —— 用于 VM 运行中动态增删端口转发。
 *
 * 通过 Android LocalSocket 连接 QEMU 的 unix socket（QMP server）。
 * 握手：connect → greeting → qmp_capabilities → 就绪。
 * hostfwd-add/remove 需要 QEMU >= 7.1；不支持时返回 false（UI 提示用启动参数）。
 */
class QmpClient(private val socketPath: String) {

    private var localSocket: android.net.LocalSocket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    private fun connect() {
        if (localSocket?.isConnected == true) return
        val ls = android.net.LocalSocket()
        ls.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
        localSocket = ls
        reader = BufferedReader(InputStreamReader(ls.inputStream))
        writer = BufferedWriter(OutputStreamWriter(ls.outputStream))
        // 丢弃 greeting 行
        reader?.readLine()
    }

    private fun send(cmd: JSONObject): JSONObject? {
        connect()
        writer?.write(cmd.toString())
        writer?.newLine()
        writer?.flush()
        while (true) {
            val line = reader?.readLine() ?: return null
            if (line.contains("\"return\"") || line.contains("\"error\"")) {
                return JSONObject(line)
            }
        }
    }

    fun addHostfwd(proto: String, hostPort: Int, guestPort: Int): Boolean = try {
        send(JSONObject().put("execute", "qmp_capabilities"))
        val args = JSONObject()
            .put("id", "net0")
            .put("source", "$proto::${hostPort}-:${guestPort}")
        val resp = send(JSONObject().put("execute", "hostfwd-add").put("arguments", args))
        resp != null && !resp.has("error")
    } catch (e: Exception) {
        Log.w(TAG, "hostfwd-add 失败", e)
        false
    }

    fun removeHostfwd(proto: String, hostPort: Int): Boolean = try {
        send(JSONObject().put("execute", "qmp_capabilities"))
        val args = JSONObject()
            .put("id", "net0")
            .put("source", "$proto::${hostPort}-:")
        val resp = send(JSONObject().put("execute", "hostfwd-remove").put("arguments", args))
        resp != null && !resp.has("error")
    } catch (e: Exception) {
        Log.w(TAG, "hostfwd-remove 失败", e)
        false
    }

    fun close() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { localSocket?.close() }
        reader = null; writer = null; localSocket = null
    }

    companion object {
        private const val TAG = "QmpClient"
    }
}
