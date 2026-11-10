package com.dockerroid.data.docker

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Docker Engine REST 客户端工厂。
 *
 * 均衡档下 Docker/Podman socket 在 guest 内，通过 SLIRP 端口转发暴露到
 * 宿主的 127.0.0.1:2375（TCP）。P1 阶段先按此约定连接；后续可切换 vsock。
 */
class DockerEngineFactory(context: Context) {

    // TODO(P1+): 从 Settings/DataStore 读取实际端口
    private val baseUrl = "http://127.0.0.1:2375"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // 流式（logs/events/stats）不超时
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
