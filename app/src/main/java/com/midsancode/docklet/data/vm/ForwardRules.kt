package com.midsancode.docklet.data.vm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 端口转发规则持久化（App 侧记录，便于列表展示与清理）。
 * 实际生效依赖 QMP hostfwd（VM 运行中）或 QEMU 启动参数。
 */
object ForwardRules {
    private const val PREFS = "forward_rules"
    private const val KEY = "rules"

    data class Rule(val proto: String, val hostPort: Int, val guestPort: Int)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Rule> {
        val raw = prefs(ctx).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Rule(o.getString("proto"), o.getInt("host"), o.getInt("guest"))
            }
        }.getOrDefault(emptyList())
    }

    private fun save(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(JSONObject().put("proto", it.proto).put("host", it.hostPort).put("guest", it.guestPort)) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, r: Rule) {
        val cur = list(ctx).filterNot { it.hostPort == r.hostPort && it.proto == r.proto }
        save(ctx, cur + r)
    }

    fun remove(ctx: Context, proto: String, hostPort: Int) {
        save(ctx, list(ctx).filterNot { it.hostPort == hostPort && it.proto == proto })
    }

    fun clear(ctx: Context) = save(ctx, emptyList())
}
