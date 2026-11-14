package com.midsancode.podroid.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * 少数几个"必须同步读"的开关放这里（SharedPreferences）。
 *
 * 为什么不全用 DataStore：语言必须在 [android.app.Activity.attachBaseContext]
 * 里同步拿到（那一刻还没有协程上下文），DataStore 是 suspend 的，读不了。
 * 其余配置仍然走 DataStore（[com.midsancode.podroid.data.settings.SettingsRepository]）。
 */
object Prefs {

    private const val NAME = "podroid_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_KEEP_ALIVE = "keep_alive"
    private const val KEY_CRASH_TIMES = "crash_times"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 语言：空串 = 跟随系统；否则是 BCP-47 语言标签（zh / en）。 */
    fun language(context: Context): String =
        get(context).getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(context: Context, tag: String) {
        get(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** 后台保活开关（默认开）。 */
    fun keepAlive(context: Context): Boolean =
        get(context).getBoolean(KEY_KEEP_ALIVE, true)

    fun setKeepAlive(context: Context, on: Boolean) {
        get(context).edit().putBoolean(KEY_KEEP_ALIVE, on).apply()
    }

    // ---- 崩溃重启节流 ----

    /** 记录一次崩溃时间戳，返回最近 10 分钟内崩溃次数。 */
    fun recordCrash(context: Context, now: Long = System.currentTimeMillis()): Int {
        val p = get(context)
        val raw = p.getString(KEY_CRASH_TIMES, "") ?: ""
        val recent = raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { now - it < 10 * 60 * 1000L }
            .toMutableList()
        recent.add(now)
        p.edit().putString(KEY_CRASH_TIMES, recent.joinToString(",")).apply()
        return recent.size
    }

    fun clearCrashHistory(context: Context) {
        get(context).edit().remove(KEY_CRASH_TIMES).apply()
    }
}
