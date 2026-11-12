package com.midsancode.docklet.data.settings

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

private val Context.dataStore by preferencesDataStore(name = "docklet_settings")

/**
 * 用户设置（DataStore）。
 */
data class Settings(
    val ramMb: Int = 1024,
    val diskMb: Int = 512,
    val enginePort: Int = 2375,
    val dnsServers: String = "1.1.1.1,8.8.8.8",
    val autoStart: Boolean = false,
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

    /** 内存缓存：VM 启动路径（非协程上下文）可直接读。 */
    private val _current = MutableStateFlow(Settings())
    val current: StateFlow<Settings> = _current.asStateFlow()

    init {
        // 启动时加载一次到内存
        scope.launch { _current.value = settingsOnce() }
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            ramMb = p[KEY_RAM] ?: 1024,
            diskMb = p[KEY_DISK] ?: 512,
            enginePort = p[KEY_PORT] ?: 2375,
            dnsServers = p[KEY_DNS] ?: "1.1.1.1,8.8.8.8",
            autoStart = p[KEY_AUTO] ?: false,
        )
    }

    suspend fun settingsOnce(): Settings {
        val p = context.dataStore.data.first()
        return Settings(
            ramMb = p[KEY_RAM] ?: 1024,
            diskMb = p[KEY_DISK] ?: 512,
            enginePort = p[KEY_PORT] ?: 2375,
            dnsServers = p[KEY_DNS] ?: "1.1.1.1,8.8.8.8",
            autoStart = p[KEY_AUTO] ?: false,
        )
    }

    suspend fun setRam(mb: Int) {
        context.dataStore.edit { it[KEY_RAM] = mb.coerceIn(256, 4096) }
        _current.value = settingsOnce()
    }

    suspend fun setDisk(mb: Int) {
        context.dataStore.edit { it[KEY_DISK] = mb.coerceIn(256, 16384) }
        _current.value = settingsOnce()
    }

    suspend fun setPort(p: Int) {
        context.dataStore.edit { it[KEY_PORT] = p.coerceIn(1024, 65535) }
        _current.value = settingsOnce()
    }

    suspend fun setDns(s: String) {
        context.dataStore.edit { it[KEY_DNS] = s.take(120) }
        _current.value = settingsOnce()
    }

    suspend fun setAutoStart(b: Boolean) {
        context.dataStore.edit { it[KEY_AUTO] = b }
        _current.value = settingsOnce()
    }
}
