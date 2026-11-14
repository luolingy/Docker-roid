package com.midsancode.docklet.data.docker

import android.content.Context
import com.midsancode.docklet.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Docker Engine REST 客户端工厂。
 *
 * 均衡档下 Docker/Podman socket 在 guest 内，通过 SLIRP 端口转发暴露到
 * 宿主的 127.0.0.1:<enginePort>（TCP）。
 *
 * 端口从设置里实时读取 —— 之前写死 2375，设置页改了端口也不会生效，
 * 等于给用户一个假开关。
 */
class DockerEngineFactory(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val settings: SettingsRepository,
) {
    private val baseUrl: String
        get() = "http://127.0.0.1:${settings.current.value.enginePort}"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        // 流式端点（logs/events/stats）不能超时；非流式端点靠调用方自己兜住。
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun baseUrl(): String = baseUrl

    /** 创建针对某端点的请求构造器，自动补 API 版本前缀 /v1.41。 */
    fun request(path: String): okhttp3.Request.Builder {
        val p = if (path.startsWith("/")) path else "/$path"
        return okhttp3.Request.Builder().url("$baseUrl/v1.41$p")
    }
}
