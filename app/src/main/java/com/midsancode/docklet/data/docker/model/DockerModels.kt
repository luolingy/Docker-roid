package com.midsancode.docklet.data.docker.model

import kotlinx.serialization.Serializable

/**
 * 镜像摘要（GET /images/json）。
 */
@Serializable
data class ImageSummary(
    val Id: String,                  // sha256:...
    val RepoTags: List<String> = emptyList(),
    val RepoDigests: List<String> = emptyList(),
    val Created: Long = 0,           // unix 秒
    val Size: Long = 0,
    val Labels: Map<String, String> = emptyMap(),
)

/**
 * 镜像详情（GET /images/{name}/json）。
 */
@Serializable
data class ImageInspect(
    val Id: String,
    val RepoTags: List<String> = emptyList(),
    val Size: Long = 0,
    val Created: String = "",
    val Config: ContainerConfig? = null,
    val Architecture: String = "",
    val Os: String = "",
)

/**
 * 网络摘要（GET /networks）。
 */
@Serializable
data class NetworkSummary(
    val Name: String,
    val Id: String = "",
    val Driver: String = "",
    val Scope: String = "",
    val Internal: Boolean = false,
    val Containers: Map<String, NetworkContainerRef> = emptyMap(),
)

@Serializable
data class NetworkContainerRef(
    val Name: String = "",
)

/**
 * 创建网络请求（POST /networks/create）。
 */
@Serializable
data class CreateNetworkRequest(
    val Name: String,
    val Driver: String = "bridge",
    val Internal: Boolean = false,
    val Labels: Map<String, String>? = null,
)

/**
 * 卷摘要（GET /volumes）。
 */
@Serializable
data class VolumeListResponse(
    val Volumes: List<VolumeSummary> = emptyList(),
    val Warnings: List<String> = emptyList(),
)

@Serializable
data class VolumeSummary(
    val Name: String,
    val Driver: String = "local",
    val Mountpoint: String = "",
    val CreatedAt: String? = null,
    val Labels: Map<String, String> = emptyMap(),
)

/**
 * 创建卷请求（POST /volumes/create）。
 */
@Serializable
data class CreateVolumeRequest(
    val Name: String,
    val Driver: String = "local",
    val Labels: Map<String, String>? = null,
)

/**
 * 系统信息（GET /info）。
 */
@Serializable
data class SystemInfo(
    val ID: String = "",
    val Containers: Int = 0,
    val ContainersRunning: Int = 0,
    val ContainersPaused: Int = 0,
    val ContainersStopped: Int = 0,
    val Images: Int = 0,
    val Name: String = "",
    val ServerVersion: String = "",
    val OperatingSystem: String = "",
    val Architecture: String = "",
    val MemTotal: Long = 0,
    val NCPU: Int = 0,
)

/**
 * 磁盘占用（GET /system/df）。
 */
@Serializable
data class SystemDf(
    val Images: List<ImageSummary> = emptyList(),
    val Containers: List<ContainerSummary> = emptyList(),
    val Volumes: List<VolumeSummary> = emptyList(),
)

/**
 * Docker 事件（GET /events，流式 NDJSON）。
 */
@Serializable
data class DockerEvent(
    val Type: String = "",           // container / image / network / volume
    val Action: String = "",         // start / stop / create / destroy ...
    val Actor: EventActor? = null,
    val time: Long = 0,
)

@Serializable
data class EventActor(
    val ID: String = "",
    val Attributes: Map<String, String> = emptyMap(),
)
