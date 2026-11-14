package com.midsancode.docklet.data.update

import com.midsancode.docklet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 更新检测：查 GitHub Releases 的最新 tag，与本地 versionName 比较。
 *
 * 只用 REST 的一个公开接口，不需要 token；未发布 release 时（404）按"已是最新"处理，
 * 避免把"仓库还没有 release"误报成检查失败。
 */
class UpdateChecker {

    data class Release(
        val tag: String,
        val version: String,
        val url: String,
        val notes: String,
        val publishedAt: String,
    )

    sealed interface Result {
        data class Newer(val release: Release) : Result
        data class UpToDate(val current: String) : Result
        data class Failed(val message: String) : Result
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Docklet/$currentVersion")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> Result.UpToDate(currentVersion)
                    !resp.isSuccessful -> Result.Failed("HTTP ${resp.code}")
                    else -> {
                        val body = resp.body?.string().orEmpty()
                        val o = JSONObject(body)
                        val tag = o.optString("tag_name")
                        if (tag.isBlank()) return@use Result.UpToDate(currentVersion)
                        val rel = Release(
                            tag = tag,
                            version = tag.removePrefix("v").removePrefix("V"),
                            url = o.optString("html_url"),
                            notes = o.optString("body").take(600),
                            publishedAt = o.optString("published_at").take(10),
                        )
                        if (isNewer(rel.version, currentVersion)) Result.Newer(rel)
                        else Result.UpToDate(currentVersion)
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /** 更新源仓库（底座下载也用同一个）。 */
        const val REPO = "luolingy/Docklet"

        /**
         * 语义化版本比较：逐段比数字，缺位补 0。
         * "0.10.0" > "0.9.9"，不能按字符串比。
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val b = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val n = maxOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
