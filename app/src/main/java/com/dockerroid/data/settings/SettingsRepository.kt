package com.dockerroid.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "docker_roid_settings")

/**
 * 用户设置（DataStore）。
 */
data class Settings(
    val ramMb: Int = 1024,
    val diskGb: Int = 2,
    val enginePort: Int = 2375,
    val dnsServers: String = "1.1.1.1,8.8.8.8",
    val autoStart: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private val KEY_RAM = intPreferencesKey("ram_mb")
    private val KEY_DISK = intPreferencesKey("disk_gb")
    private val KEY_PORT = intPreferencesKey("engine_port")
    private val KEY_DNS = stringPreferencesKey("dns_servers")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            ramMb = p[KEY_RAM] ?: 1024,
            diskGb = p[KEY_DISK] ?: 2,
            enginePort = p[KEY_PORT] ?: 2375,
            dnsServers = p[KEY_DNS] ?: "1.1.1.1,8.8.8.8",
        )
    }

    suspend fun setRam(mb: Int) { context.dataStore.edit { it[KEY_RAM] = mb.coerceIn(512, 4096) } }
    suspend fun setDisk(gb: Int) { context.dataStore.edit { it[KEY_DISK] = gb.coerceIn(1, 16) } }
    suspend fun setPort(p: Int) { context.dataStore.edit { it[KEY_PORT] = p.coerceIn(1024, 65535) } }
    suspend fun setDns(s: String) { context.dataStore.edit { it[KEY_DNS] = s } }
}
