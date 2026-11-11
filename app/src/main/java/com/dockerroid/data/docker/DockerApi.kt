package com.dockerroid.data.docker

import com.dockerroid.data.docker.model.ContainerSummary
import com.dockerroid.data.docker.model.CreateContainerRequest
import com.dockerroid.data.docker.model.CreateNetworkRequest
import com.dockerroid.data.docker.model.CreateVolumeRequest
import com.dockerroid.data.docker.model.DockerEvent
import com.dockerroid.data.docker.model.ImageSummary
import com.dockerroid.data.docker.model.NetworkSummary
import com.dockerroid.data.docker.model.SystemDf
import com.dockerroid.data.docker.model.SystemInfo
import com.dockerroid.data.docker.model.VolumeListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
/**
 * Docker Engine REST 客户端（轻量手写，OkHttp + kotlinx.serialization）。
 *
 * 覆盖：containers / images / networks / volumes / system。
 * 流式端点（events / logs / stats）返回 Flow，离开页面取消协程即断流。
 */
class DockerApi(
    private val factory: DockerEngineFactory,
) {
    private val client: OkHttpClient get() = factory.client
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json".toMediaType()

    // ---- 基础封装 ----

    class DockerException(val code: Int, message: String) : Exception(message)

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DockerException(resp.code, "HTTP ${resp.code}: $body")
            return json.decodeFromString(body)
        }
    }

    private fun executeNoBody(request: Request): Response {
        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) {
            val text = resp.body?.string().orEmpty()
            resp.close()
            throw DockerException(resp.code, "HTTP ${resp.code}: $text")
        }
        return resp
    }

    private fun post(path: String, bodyJson: String? = null, query: String? = null): Request {
        val builder = factory.request(path + (query?.let { "?$it" } ?: ""))
            .post((bodyJson ?: "{}").toRequestBody(jsonMedia))
        return builder.build()
    }

    private fun get(path: String, query: String? = null): Request {
        return factory.request(path + (query?.let { "?$it" } ?: "")).get().build()
    }

    private fun delete(path: String, query: String? = null): Request {
        return factory.request(path + (query?.let { "?$it" } ?: "")).delete().build()
    }

    // ---- System ----

    fun info(): SystemInfo = execute(get("/info"))

    fun systemDf(): SystemDf = execute(get("/system/df"))

    /** 清理未使用的镜像/容器/网络/卷（docker system prune）。返回释放的字节数。 */
    fun prune(volumes: Boolean = false): Long {
        val q = "filters=%7B%7D" + if (volumes) "&dangling=false" else ""
        val resp = executeNoBody(post("/system/prune", query = q))
        val text = resp.body?.string().orEmpty()
        resp.close()
        // 不同引擎返回字段：SpaceReclaimed 或 SpaceReclaimed
        return runCatching {
            json.parseToJsonElement(text).jsonObject["SpaceReclaimed"]?.jsonPrimitive?.content?.toLongOrNull()
        }.getOrNull() ?: 0L
    }

    /** 构删镜像（docker build）。body 为 tar 包（含 Dockerfile），此处用字符串 Dockerfile 简单构建。 */
    fun buildImage(dockerfileTarPath: String, tag: String): Flow<String> = flow {
        // 说明：真正的 build 需要 tar 上下文。此处占位实现——读取 tar 文件 POST /build
        val file = java.io.File(dockerfileTarPath)
        if (!file.exists()) { emit("错误：构建上下文不存在"); return@flow }
        val req = factory.request("/build")
            .post(file.readBytes().toRequestBody("application/x-tar".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val reader = resp.body?.charStream() ?: return@flow
            reader.forEachLine { line ->
                if (line.contains("\"stream\"")) {
                    val msg = runCatching {
                        json.parseToJsonElement(line).jsonObject["stream"]?.jsonPrimitive?.content
                    }.getOrNull() ?: line
                    emit(msg.trimEnd())
                } else if (line.contains("\"error\"")) {
                    emit("错误：" + line)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun ping(): Boolean = try {
        executeNoBody(factory.request("/_ping").get().build()).close(); true
    } catch (_: Exception) { false }

    // ---- Containers ----

    fun listContainers(all: Boolean = true): List<ContainerSummary> =
        execute(get("/containers/json", "all=${if (all) 1 else 0}"))

    fun startContainer(id: String) = executeNoBody(post("/containers/$id/start")).close()

    fun stopContainer(id: String, timeoutSec: Int = 10) =
        executeNoBody(post("/containers/$id/stop", query = "t=$timeoutSec")).close()

    fun restartContainer(id: String) = executeNoBody(post("/containers/$id/restart")).close()

    fun pauseContainer(id: String) = executeNoBody(post("/containers/$id/pause")).close()

    fun unpauseContainer(id: String) = executeNoBody(post("/containers/$id/unpause")).close()

    fun removeContainer(id: String, force: Boolean = false, removeVolumes: Boolean = false) =
        executeNoBody(
            delete("/containers/$id", "force=${if (force) 1 else 0}&v=${if (removeVolumes) 1 else 0}")
        ).close()

    fun createContainer(req: CreateContainerRequest): String {
        val body = json.encodeToString(CreateContainerRequest.serializer(), req)
        val resp = executeNoBody(post("/containers/create", body))
        val text = resp.body?.string().orEmpty()
        resp.close()
        val obj = json.parseToJsonElement(text).jsonObject
        return obj["Id"]?.jsonPrimitive?.content ?: ""
    }

    fun inspectContainer(id: String): com.dockerroid.data.docker.model.ContainerInspect =
        execute(get("/containers/$id/json"))

    fun renameContainer(id: String, newName: String) {
        val body = """{"name":"$newName"}"""
        executeNoBody(post("/containers/$id/rename", body)).close()
    }

    /** 提交容器为镜像（docker commit）。返回新镜像 Id。 */
    fun commitContainer(id: String, repo: String, tag: String = "latest", comment: String = ""): String {
        val q = "container=$id&repo=$repo&tag=$tag&comment=" + java.net.URLEncoder.encode(comment, "UTF-8")
        val resp = executeNoBody(post("/commit", query = q))
        val text = resp.body?.string().orEmpty()
        resp.close()
        return runCatching { json.parseToJsonElement(text).jsonObject["Id"]?.jsonPrimitive?.content }
            .getOrNull() ?: ""
    }

    // ---- Images ----

    fun listImages(): List<ImageSummary> = execute(get("/images/json"))

    fun removeImage(name: String, force: Boolean = false) =
        executeNoBody(delete("/images/$name", "force=${if (force) 1 else 0}")).close()

    /**
     * 拉取镜像（流式进度，NDJSON 每行一个 {status, id, progressDetail...}）。
     * UI 只需展示 status + progress 字符串。
     */
    data class PullProgress(
        val status: String = "",
        val id: String? = null,
        val progress: String? = null,
        val error: String? = null,
    )

    fun pullImage(name: String): Flow<PullProgress> = flow {
        val req = post("/images/create", query = "fromImage=$name")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DockerException(resp.code, "HTTP ${resp.code}")
            val reader = resp.body?.charStream() ?: return@flow
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val obj = json.parseToJsonElement(line).jsonObject
                val err = obj["error"]?.jsonPrimitive?.contentOrNull
                if (err != null) throw DockerException(-1, err)
                emit(
                    PullProgress(
                        status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        id = obj["id"]?.jsonPrimitive?.contentOrNull,
                        progress = obj["progress"]?.jsonPrimitive?.contentOrNull,
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Networks ----

    fun listNetworks(): List<NetworkSummary> = execute(get("/networks"))

    fun createNetwork(req: CreateNetworkRequest) {
        execute<kotlinx.serialization.json.JsonObject>(
            post("/networks/create", json.encodeToString(CreateNetworkRequest.serializer(), req))
        )
    }

    fun removeNetwork(id: String) = executeNoBody(delete("/networks/$id")).close()

    fun connectNetwork(networkId: String, containerId: String) {
        val body = """{"Container":"$containerId"}"""
        executeNoBody(post("/networks/$networkId/connect", body)).close()
    }

    fun disconnectNetwork(networkId: String, containerId: String) {
        val body = """{"Container":"$containerId"}"""
        executeNoBody(post("/networks/$networkId/disconnect", body)).close()
    }

    // ---- Volumes ----

    fun listVolumes(): VolumeListResponse = execute(get("/volumes"))

    fun createVolume(req: CreateVolumeRequest) {
        execute<kotlinx.serialization.json.JsonObject>(
            post("/volumes/create", json.encodeToString(CreateVolumeRequest.serializer(), req))
        )
    }

    fun removeVolume(name: String) = executeNoBody(delete("/volumes/$name")).close()

    // ---- Events（长连接流） ----

    fun events(): Flow<DockerEvent> = flow {
        val req = get("/events", "filters=%7B%7D")  // {} 空过滤器
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DockerException(resp.code, "HTTP ${resp.code}")
            val reader = resp.body?.charStream() ?: return@flow
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                runCatching { json.decodeFromString<DockerEvent>(line) }
                    .getOrNull()
                    ?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Logs（流式；stdout=1 stderr=2） ----

    /** Docker multiplexed stream：非 TTY 时每帧 [8字节头][payload]。此处按帧解析。 */
    fun logs(id: String, tail: Int = 200, follow: Boolean): Flow<String> = flow {
        val req = get(
            "/containers/$id/logs",
            "stdout=1&stderr=1&tail=$tail" + if (follow) "&follow=1" else "",
        )
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DockerException(resp.code, "HTTP ${resp.code}")
            val input = resp.body?.byteStream() ?: return@flow
            val header = ByteArray(8)
            while (true) {
                // 读 8 字节帧头: [streamType, 0,0,0, size(4, big-endian)]
                var read = 0
                while (read < 8) {
                    val n = input.read(header, read, 8 - read)
                    if (n < 0) return@flow
                    read += n
                }
                val size = ((header[4].toInt() and 0xFF) shl 24) or
                    ((header[5].toInt() and 0xFF) shl 16) or
                    ((header[6].toInt() and 0xFF) shl 8) or
                    (header[7].toInt() and 0xFF)
                if (size <= 0) continue
                val payload = ByteArray(size)
                read = 0
                while (read < size) {
                    val n = input.read(payload, read, size - read)
                    if (n < 0) return@flow
                    read += n
                }
                emit(String(payload, Charsets.UTF_8).trimEnd('\n', '\r'))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Stats（流式，每秒一条） ----

    @kotlinx.serialization.Serializable
    data class StatsSample(
        val cpuPercent: Double = 0.0,
        val memUsedMb: Double = 0.0,
        val memLimitMb: Double = 0.0,
    )

    fun stats(id: String): Flow<StatsSample> = flow {
        val req = get("/containers/$id/stats", "stream=1")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DockerException(resp.code, "HTTP ${resp.code}")
            val reader = resp.body?.charStream() ?: return@flow
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                    ?: return@forEachLine
                val cpuDelta = obj["cpu_stats"]?.jsonObject?.get("cpu_usage")
                    ?.jsonObject?.get("total_usage")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val preDelta = obj["precpu_stats"]?.jsonObject?.get("cpu_usage")
                    ?.jsonObject?.get("total_usage")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val sysDelta = obj["cpu_stats"]?.jsonObject?.get("system_cpu_usage")
                    ?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val preSys = obj["precpu_stats"]?.jsonObject?.get("system_cpu_usage")
                    ?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val online = obj["cpu_stats"]?.jsonObject?.get("online_cpus")
                    ?.jsonPrimitive?.content?.toLongOrNull() ?: 1L
                val cpuPct = if (sysDelta > preSys && sysDelta > 0)
                    (cpuDelta - preDelta).toDouble() / (sysDelta - preSys).toDouble() * online * 100.0
                else 0.0
                val memRaw = obj["memory_stats"]?.jsonObject ?: return@forEachLine
                val used = (memRaw["usage"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) -
                    (memRaw["stats"]?.jsonObject?.get("cache")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
                val limit = memRaw["limit"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                emit(
                    StatsSample(
                        cpuPercent = "%.1f".format(cpuPct).toDouble(),
                        memUsedMb = used / 1024.0 / 1024.0,
                        memLimitMb = limit / 1024.0 / 1024.0,
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- 扩展小工具 ----

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as kotlinx.serialization.json.JsonObject

    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as kotlinx.serialization.json.JsonPrimitive

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull
        get() = if (this is kotlinx.serialization.json.JsonNull) null else this.content

    private inline fun java.io.Reader.forEachLine(block: (String) -> Unit) {
        val br = java.io.BufferedReader(this)
        while (true) {
            val line = br.readLine() ?: break
            block(line)
        }
    }
}
