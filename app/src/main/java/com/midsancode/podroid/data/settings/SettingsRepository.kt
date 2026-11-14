package com.midsancode.podroid.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "podroid_settings")

/** 默认 DNS：阿里 + 腾讯，国内可达性最好。 */
const val DEFAULT_DNS = "223.5.5.5,119.29.29.29"

/** 默认镜像站序列（boot.sh 里也有同一份兜底，这里显式写出来供 UI 展示）。 */
const val DEFAULT_MIRRORS = "docker.m.daocloud.io,docker.1panel.live,hub.rat.dev"

/**
 * 用户设置（DataStore）。
 *
 * 注意：除 [autoStart] 外，其余项都在**下次启动 VM 时**通过内核 cmdline 传给 guest，
 * 运行中修改不会热生效——UI 上必须明确告知，否则用户会以为改了没用。
 */
data class Settings(
    val ramMb: Int = 1024,
    val diskMb: Int = 4096,
    val enginePort: Int = 2375,
    val dnsServers: String = DEFAULT_DNS,
    val autoStart: Boolean = false,
    /** docker.io 镜像站，逗号分隔；空串表示用 boot.sh 内置默认。 */
    val mirrors: String = "",
    /** 容器默认网络模式：host / bridge。 */
    val networkMode: String = "host",
    /** 主题强调色：blue / teal / purple / green / orange。 */
    val themeColor: String = "blue",
    /** 明暗模式：system / light / dark。 */
    val themeMode: String = "system",
)

/**
 * 设置仓库：DataStore 持久 + StateFlow 同步内存视图（VM 启动时同步读取）。
 */
class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_RAM = intPreferencesKey("ram_mb")
    private val KEY_DISK = intPreferencesKey("disk_mb")
    private val KEY_PORT = intPreferencesKey("engine_port")
    private val KEY_DNS = stringPreferencesKey("dns_servers")
    private val KEY_AUTO = booleanPreferencesKey("auto_start")
    private val KEY_MIRRORS = stringPreferencesKey("mirrors")
    private val KEY_NETMODE = stringPreferencesKey("network_mode")
    private val KEY_THEME = stringPreferencesKey("theme_color")
    private val KEY_MODE = stringPreferencesKey("theme_mode")

    /** 内存缓存：VM 启动路径（非协程上下文）可直接读。 */
    private val _current = MutableStateFlow(Settings())
    val current: StateFlow<Settings> = _current.asStateFlow()

    init {
        // 启动时加载一次到内存
        scope.launch { _current.value = settingsOnce() }
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun settingsOnce(): Settings = context.dataStore.data.first().toSettings()

    private fun androidx.datastore.preferences.core.Preferences.toSettings() = Settings(
        ramMb = this[KEY_RAM] ?: 1024,
        diskMb = this[KEY_DISK] ?: 4096,
        enginePort = this[KEY_PORT] ?: 2375,
        dnsServers = this[KEY_DNS] ?: DEFAULT_DNS,
        autoStart = this[KEY_AUTO] ?: false,
        mirrors = this[KEY_MIRRORS] ?: "",
        networkMode = this[KEY_NETMODE] ?: "host",
        themeColor = this[KEY_THEME] ?: "blue",
        themeMode = this[KEY_MODE] ?: "system",
    )

    private suspend fun commit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
        _current.value = settingsOnce()
    }

    suspend fun setRam(mb: Int) = commit { it[KEY_RAM] = mb.coerceIn(256, 4096) }

    suspend fun setDisk(mb: Int) = commit { it[KEY_DISK] = mb.coerceIn(512, 16384) }

    suspend fun setPort(p: Int) = commit { it[KEY_PORT] = p.coerceIn(1024, 65535) }

    suspend fun setDns(s: String) = commit { it[KEY_DNS] = s.take(120) }

    suspend fun setAutoStart(b: Boolean) = commit { it[KEY_AUTO] = b }

    suspend fun setMirrors(s: String) = commit { it[KEY_MIRRORS] = s.take(200) }

    suspend fun setNetworkMode(m: String) = commit { it[KEY_NETMODE] = if (m == "bridge") "bridge" else "host" }

    suspend fun setThemeColor(key: String) = commit { it[KEY_THEME] = key }

    suspend fun setThemeMode(mode: String) = commit {
        it[KEY_MODE] = if (mode == "light" || mode == "dark") mode else "system"
    }
}
