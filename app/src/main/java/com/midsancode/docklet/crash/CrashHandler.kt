package com.midsancode.docklet.crash

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.midsancode.docklet.BuildConfig
import com.midsancode.docklet.MainActivity
import com.midsancode.docklet.data.prefs.Prefs
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 全局崩溃兜底：记录现场 → 定时拉起自己。
 *
 * 为什么用 AlarmManager 而不是 Handler.postDelayed：
 * 主线程崩溃时 Looper 已经死了，进程内任何延迟任务都不会执行；只有交给系统
 * 调度的 PendingIntent 才能在进程被杀之后把 Activity 重新拉起来。
 *
 * 防崩溃循环：10 分钟内崩溃超过 [MAX_RESTARTS] 次就不再自动重启，避免"一开就崩"
 * 导致手机反复闪屏。用户可以手动打开 App，崩溃计数会自动过期。
 */
class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        val count = runCatching { Prefs.recordCrash(app) }.getOrDefault(0)
        runCatching { writeLog(t, e, count) }
        if (count <= MAX_RESTARTS) {
            runCatching { scheduleRestart() }
        }
        // 交回系统默认处理（打印日志并杀掉进程）
        previous?.uncaughtException(t, e) ?: run {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun scheduleRestart() {
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(MainActivity.EXTRA_AFTER_CRASH, true)
        }
        val pi = PendingIntent.getActivity(
            app,
            REQ_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        // 1.5 秒后拉起：留一点时间让系统把崩溃进程彻底回收，否则会撞上同一个异常现场
        val at = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        runCatching {
            // setAndAllowWhileIdle 不需要 SCHEDULE_EXACT_ALARM 权限，doze 下也能触发
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }.onFailure {
            runCatching { am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi) }
        }
    }

    private fun writeLog(t: Thread, e: Throwable, count: Int) {
        val dir = File(app.filesDir, "crash").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { e.printStackTrace(it) }
        val text = buildString {
            appendLine("time: $ts")
            appendLine("thread: ${t.name}")
            appendLine("restart#: $count")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}")
            appendLine()
            appendLine(sw.toString())
        }
        // 只保留最近一次，避免无限增长
        File(dir, "last_crash.txt").writeText(text)
    }

    companion object {
        private const val MAX_RESTARTS = 3
        private const val RESTART_DELAY_MS = 1500L
        private const val REQ_CODE = 0x0C0FFEE

        fun crashLogFile(app: Application): File = File(app.filesDir, "crash/last_crash.txt")

        fun install(app: Application) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(app))
        }
    }
}
