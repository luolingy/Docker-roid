package com.midsancode.podroid.data.docker

import com.midsancode.podroid.data.docker.model.ContainerSummary
import com.midsancode.podroid.data.docker.model.CreateContainerRequest
import com.midsancode.podroid.data.docker.model.DockerEvent
import com.midsancode.podroid.data.docker.model.ImageSummary
import com.midsancode.podroid.data.docker.model.NetworkSummary
import com.midsancode.podroid.data.docker.model.SystemDf
import com.midsancode.podroid.data.docker.model.SystemInfo
import com.midsancode.podroid.data.docker.model.VolumeSummary
import com.midsancode.podroid.data.vm.VmManager
import com.midsancode.podroid.data.vm.VmStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 引擎数据仓库：唯一权威状态层。
 *
 * - `engineAvailable`：VM Ready 且 ping 通过，UI 据此切换"未就绪"占位
 * - 容器/镜像/网络/卷列表：启动后自动轮询（5s，轻量）+ 事件流触发即时刷新
 * - 所有调用在 IO 线程，结果以 StateFlow 暴露
 */
class EngineRepository(
    private val apiFactory: DockerEngineFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api by lazy { DockerApi(apiFactory) }

    // ---- 引擎可用性 ----

    private val _engineAvailable = MutableStateFlow(false)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    // ---- 列表状态 ----

    data class ListState<T>(
        val items: List<T> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _containers = MutableStateFlow(ListState<ContainerSummary>())
    val containers: StateFlow<ListState<ContainerSummary>> = _containers.asStateFlow()

    private val _images = MutableStateFlow(ListState<ImageSummary>())
    val images: StateFlow<ListState<ImageSummary>> = _images.asStateFlow()

    private val _networks = MutableStateFlow(ListState<NetworkSummary>())
    val networks: StateFlow<ListState<NetworkSummary>> = _networks.asStateFlow()

    private val _volumes = MutableStateFlow(ListState<VolumeSummary>())
    val volumes: StateFlow<ListState<VolumeSummary>> = _volumes.asStateFlow()

    private val _systemInfo = MutableStateFlow<SystemInfo?>(null)
    val systemInfo: StateFlow<SystemInfo?> = _systemInfo.asStateFlow()

    private val _diskUsage = MutableStateFlow<SystemDf?>(null)
    val diskUsage: StateFlow<SystemDf?> = _diskUsage.asStateFlow()

    /** 最近一次引擎错误（供 Snackbar）。 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearError() { _lastError.value = null }

    private var started = false

    /** App 启动时调用一次：监听 VM 状态 + 轮询循环。 */
    fun start() {
        if (started) return
        started = true

        // VM 状态变化时探测引擎
        scope.launch {
            VmManager.status.collect { status ->
                if (status is VmStatus.Ready) probeEngine() else _engineAvailable.value = false
            }
        }

        // 主轮询：就绪时每 5s 刷新全部列表
        scope.launch {
            while (isActive) {
                if (_engineAvailable.value) refreshAll()
                delay(5_000)
            }
        }

        // 事件流：引擎可用后监听 /events，任何容器/镜像动作即时刷新对应列表
        scope.launch {
            while (isActive) {
                if (_engineAvailable.value) {
                    try {
                        api.events().collect { ev ->
                            when (ev.Type) {
                                "container" -> refreshContainers()
                                "image" -> refreshImages()
                                "network" -> refreshNetworks()
                                "volume" -> refreshVolumes()
                            }
                        }
                    } catch (_: Exception) {
                        // 流断开（VM 停止/引擎重启）→ 等下一轮探测恢复
                        _engineAvailable.value = false
                    }
                }
                delay(2_000)
            }
        }
    }

    private suspend fun probeEngine() {
        try {
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) { api.ping() }
            _engineAvailable.value = ok
            if (ok) refreshAll()
        } catch (e: Exception) {
            _engineAvailable.value = false
        }
    }

    fun refreshAll() {
        refreshContainers(); refreshImages(); refreshNetworks(); refreshVolumes(); refreshSystem()
    }

    // ---- 各列表刷新 ----

    fun refreshContainers() = scope.launch {
        _containers.value = _containers.value.copy(loading = true, error = null)
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listContainers(all = true) }
            _containers.value = ListState(items)
        } catch (e: Exception) {
            _containers.value = _containers.value.copy(loading = false, error = e.message)
            _lastError.value = e.message
        }
    }

    fun refreshImages() = scope.launch {
        _images.value = _images.value.copy(loading = true, error = null)
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listImages() }
            _images.value = ListState(items)
        } catch (e: Exception) {
            _images.value = _images.value.copy(loading = false, error = e.message)
            _lastError.value = e.message
        }
    }

    fun refreshNetworks() = scope.launch {
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listNetworks() }
            _networks.value = ListState(items)
        } catch (_: Exception) { }
    }

    fun refreshVolumes() = scope.launch {
        try {
            val resp = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listVolumes() }
            _volumes.value = ListState(resp.Volumes)
        } catch (_: Exception) { }
    }

    private fun refreshSystem() = scope.launch {
        try {
            _systemInfo.value = kotlinx.coroutines.withContext(Dispatchers.IO) { api.info() }
            _diskUsage.value = kotlinx.coroutines.withContext(Dispatchers.IO) { api.systemDf() }
        } catch (_: Exception) { }
    }

    // ---- 容器操作（执行后立刻刷新） ----

    fun startContainer(id: String) = scope.launch { io { api.startContainer(id) }; refreshContainers() }
    fun stopContainer(id: String) = scope.launch { io { api.stopContainer(id) }; refreshContainers() }
    fun restartContainer(id: String) = scope.launch { io { api.restartContainer(id) }; refreshContainers() }
    fun pauseContainer(id: String) = scope.launch { io { api.pauseContainer(id) }; refreshContainers() }
    fun unpauseContainer(id: String) = scope.launch { io { api.unpauseContainer(id) }; refreshContainers() }
    fun removeContainer(id: String, force: Boolean) =
        scope.launch { io { api.removeContainer(id, force) }; refreshContainers() }
    fun renameContainer(id: String, newName: String) =
        scope.launch { io { api.renameContainer(id, newName) }; refreshContainers() }
    fun commitContainer(id: String, repo: String, tag: String) =
        scope.launch { io { api.commitContainer(id, repo, tag) }; refreshImages() }

    /** 系统清理，返回释放字节数。 */
    fun prune(): kotlinx.coroutines.Job = scope.launch {
        val freed = runCatching { io { api.prune() } }.getOrDefault(0L)
        refreshAll()
        _lastPruneFreed.value = freed
    }

    private val _lastPruneFreed = MutableStateFlow(0L)
    val lastPruneFreed: StateFlow<Long> = _lastPruneFreed.asStateFlow()

    fun createAndStart(req: CreateContainerRequest) = scope.launch {
        io { val id = api.createContainer(req); api.startContainer(id) }
        refreshContainers()
    }

    // ---- 详情相关（供 ContainerDetailScreen） ----

    /** 在容器内执行一条命令并返回输出（一次性，非交互）。 */
    suspend fun execCommand(id: String, cmd: List<String>): String =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { api.execCapture(id, cmd) }.getOrElse { "执行失败: ${it.message}" }
        }

    fun logs(id: String, tail: Int = 200, follow: Boolean): Flow<String> = api.logs(id, tail, follow)

    /** 导出容器日志到文件（一次性拉 tail 行），返回文件路径。 */
    suspend fun exportLogs(id: String, outFile: java.io.File, tail: Int = 2000): java.io.File {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            api.logs(id, tail, follow = false).collect { sb.appendLine(it) }
            outFile.parentFile?.mkdirs()
            outFile.writeText(sb.toString())
            outFile
        }
    }

    fun stats(id: String): Flow<DockerApi.StatsSample> = api.stats(id)

    /** 单次 inspect（不做流；UI 进详情页时拉一次）。 */
    fun containerDetail(id: String): Flow<com.midsancode.podroid.data.docker.model.ContainerInspect> =
        kotlinx.coroutines.flow.flow {
            emit(io { api.inspectContainer(id) })
        }

    // ---- 镜像操作 ----

    fun removeImage(name: String, force: Boolean = false) =
        scope.launch { io { api.removeImage(name, force) }; refreshImages() }

    // ---- 网络/卷操作 ----

    fun createNetwork(req: com.midsancode.podroid.data.docker.model.CreateNetworkRequest) =
        scope.launch { io { api.createNetwork(req) }; refreshNetworksSafe() }

    fun removeNetwork(name: String) =
        scope.launch { io { api.removeNetwork(name) }; refreshNetworksSafe() }

    fun createVolume(req: com.midsancode.podroid.data.docker.model.CreateVolumeRequest) =
        scope.launch { io { api.createVolume(req) }; refreshVolumesSafe() }

    fun removeVolume(name: String) =
        scope.launch { io { api.removeVolume(name) }; refreshVolumesSafe() }

    private suspend fun refreshNetworksSafe() {
        try {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listNetworks() }
            _networks.value = ListState(items)
        } catch (_: Exception) { }
    }

    private suspend fun refreshVolumesSafe() {
        try {
            val resp = kotlinx.coroutines.withContext(Dispatchers.IO) { api.listVolumes() }
            _volumes.value = ListState(resp.Volumes)
        } catch (_: Exception) { }
    }

    /** 拉取镜像：进度通过 Flow 返回给 UI；结束/失败后刷新列表。 */
    fun pullImage(name: String): Flow<DockerApi.PullProgress> =
        api.pullImage(name)
            .catch { e -> _lastError.value = e.message; emit(DockerApi.PullProgress(error = e.message)) }
            .flowOn(Dispatchers.IO)

    /** 从 Dockerfile 构建镜像：返回构建日志流。 */
    fun buildImage(dockerfile: String, tag: String): Flow<String> = flow {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "dd-build-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tar = BuildContext.tarOfDockerfile(dir, dockerfile)
            api.buildImage(tar.absolutePath, tag).collect { emit(it) }
            refreshImages()
        } finally {
            dir.deleteRecursively()
        }
    }.catch { e -> emit("构建失败：${e.message}") }.flowOn(Dispatchers.IO)

    // ---- 事件流（触发即时刷新） ----

    fun observeEvents(): Flow<DockerEvent> = api.events()

    private suspend fun <T> io(block: () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}
