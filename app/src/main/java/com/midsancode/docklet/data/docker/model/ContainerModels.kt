package com.midsancode.docklet.data.docker.model

import kotlinx.serialization.Serializable

/**
 * 容器摘要（GET /containers/json）。字段按需取用，未知字段自动忽略。
 */
@Serializable
data class ContainerSummary(
    val Id: String,
    val Names: List<String> = emptyList(),
    val Image: String = "",
    val State: String = "",           // created / running / paused / restarting / exited / dead
    val Status: String = "",          // 人类可读状态，如 "Up 3 minutes"
    val Created: Long = 0,            // unix 秒
    @Suppress("CanBeParameter") val Ports: List<PortBinding> = emptyList(),
    val Labels: Map<String, String> = emptyMap(),
)

@Serializable
data class PortBinding(
    val IP: String? = null,
    val PrivatePort: Int = 0,
    val PublicPort: Int? = null,
    val Type: String = "tcp",
)

/**
 * 容器详情（GET /containers/{id}/json）。只保留 GUI 用到的字段。
 */
@Serializable
data class ContainerInspect(
    val Id: String,
    val Name: String = "",
    val State: ContainerState? = null,
    val Config: ContainerConfig? = null,
    val HostConfig: HostConfig? = null,
    val Mounts: List<MountPoint> = emptyList(),
)

@Serializable
data class ContainerState(
    val Status: String = "",
    val Running: Boolean = false,
    val Paused: Boolean = false,
    val Restarting: Boolean = false,
    val Pid: Int = 0,
    val StartedAt: String = "",
    val FinishedAt: String = "",
)

@Serializable
data class ContainerConfig(
    val Image: String = "",
    val Cmd: List<String> = emptyList(),
    val Entrypoint: List<String> = emptyList(),
    val Env: List<String> = emptyList(),
    val WorkingDir: String? = null,
    val Labels: Map<String, String> = emptyMap(),
)

@Serializable
data class HostConfig(
    val NetworkMode: String? = null,
    val PortBindings: Map<String, List<PortMap>>? = null,
    val RestartPolicy: RestartPolicy? = null,
    val Memory: Long = 0,
)

@Serializable
data class PortMap(
    val HostIp: String = "",
    val HostPort: String = "",
)

@Serializable
data class RestartPolicy(
    val Name: String = "",           // no / always / unless-stopped / on-failure
    val MaximumRetryCount: Int = 0,
)

@Serializable
data class MountPoint(
    val Type: String = "",           // bind / volume / tmpfs
    val Source: String = "",
    val Destination: String = "",
    val RW: Boolean = false,
)

/**
 * 创建容器请求（POST /containers/create）。
 */
@Serializable
data class CreateContainerRequest(
    val Image: String,
    val Cmd: List<String>? = null,
    val Entrypoint: List<String>? = null,
    val Env: List<String>? = null,
    val Labels: Map<String, String>? = null,
    val WorkingDir: String? = null,
    val ExposedPorts: Map<String, ExposedPort>? = null,
    val HostConfig: CreateHostConfig? = null,
)

@Serializable
data class ExposedPort(val nothing: String? = null)  // {} 占位

@Serializable
data class CreateHostConfig(
    val PortBindings: Map<String, List<PortMap>>? = null,
    val NetworkMode: String? = null,
    val Binds: List<String>? = null,
    val RestartPolicy: RestartPolicy? = null,
    val Memory: Long? = null,
)
