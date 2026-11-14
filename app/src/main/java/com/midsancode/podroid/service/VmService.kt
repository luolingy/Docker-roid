package com.midsancode.podroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.midsancode.podroid.MainActivity
import com.midsancode.podroid.R
import com.midsancode.podroid.data.prefs.Prefs
import com.midsancode.podroid.i18n.LocaleHelper
import com.midsancode.podroid.data.vm.QemuRunner
import com.midsancode.podroid.data.vm.VmManager
import com.midsancode.podroid.data.vm.VmStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务：持有 WakeLock + 常驻通知，托管 QemuRunner 的生命周期。
 *
 * Android 只允许前台服务长期保活后台进程，所以 QEMU 必须挂在这里。
 *
 * 保活相关的三件事：
 *  1. [START_STICKY] + [onStartCommand] 收到空 Intent（系统重启服务）时自动恢复 VM；
 *  2. [onTaskRemoved] 不清服务——用户从最近任务划掉 App 不等于要关容器；
 *  3. QEMU 异常退出（非用户停止）时按 [MAX_AUTO_RETRY] 次自动重拉。
 */
class VmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runner: QemuRunner
    private var wakeLock: PowerManager.WakeLock? = null

    /** 用户显式停止时置位，避免自动重启逻辑把刚停的 VM 又拉起来。 */
    private var userStopped = false
    private var autoRetries = 0
    private var restartJob: Job? = null

    /**
     * 取文案用的 context。
     *
     * Service 的 configuration 跟随**系统**语言，应用内语言覆盖只套在 Activity 上，
     * 直接用 [getString] 会让通知里的阶段文案停留在系统语言。
     */
    private val i18n: Context get() = LocaleHelper.wrap(this)

    override fun onCreate() {
        super.onCreate()
        runner = QemuRunner(applicationContext, scope)
        createChannel()
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                userStopped = false
                autoRetries = 0
                startVm()
            }
            ACTION_STOP -> {
                userStopped = true
                restartJob?.cancel()
                stopVm()
            }
            // intent == null：系统按 START_STICKY 重启了服务。只有保活开启时才恢复，
            // 否则用户明明停过 VM，重启服务后又自己跑起来会很困惑。
            null -> {
                if (Prefs.keepAlive(this) && !userStopped) {
                    startForegroundCompat(buildNotification(i18n.getString(R.string.vm_status_booting)))
                    acquireWakeLock()
                    runner.start()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startVm() {
        startForegroundCompat(buildNotification(i18n.getString(R.string.vm_status_booting)))
        acquireWakeLock()
        runner.start()
    }

    private fun stopVm() {
        runner.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 状态变化时刷新常驻通知文案；VM 停止后收尾。 */
    private fun observeStatus() {
        scope.launch {
            VmManager.status.collectLatest { status ->
                val text = when (status) {
                    is VmStatus.Stopped -> i18n.getString(R.string.vm_status_stopped)
                    is VmStatus.Booting -> status.bootStage ?: i18n.getString(R.string.vm_status_booting)
                    is VmStatus.Ready -> i18n.getString(R.string.vm_status_ready)
                    is VmStatus.Error -> i18n.getString(R.string.vm_status_error)
                }
                updateNotification(text)

                when (status) {
                    is VmStatus.Stopped -> {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    // QEMU 自己挂了（内核 panic / 进程非零退出）→ 自动重拉
                    is VmStatus.Error -> maybeAutoRestart()
                    else -> Unit
                }
            }
        }
    }

    private fun maybeAutoRestart() {
        if (userStopped || !Prefs.keepAlive(this)) return
        if (autoRetries >= MAX_AUTO_RETRY) return
        restartJob?.cancel()
        restartJob = scope.launch {
            autoRetries++
            delay(RETRY_DELAY_MS * autoRetries)
            if (!userStopped && Prefs.keepAlive(this@VmService)) {
                VmManager.onSerialLineDebug("[host] 异常退出，自动重启（第 $autoRetries 次）")
                // 不调 runner.stop()：进程已经自己退出了，QemuRunner 里的 process 也已置空。
                // 这里再 stop() 会让旧 start() 协程的 waitFor() 返回并二次上报状态，
                // 触发新一轮 Error → 重试计数被吃掉。
                runner.start()
            }
        }
    }

    /** 用户从最近任务里划掉 App：前台服务继续跑，容器不会因此中断。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs.keepAlive(this)) {
            // 通知被系统清理时补一条，保证服务仍在前台
            updateNotification(i18n.getString(R.string.vm_status_ready))
        }
        super.onTaskRemoved(rootIntent)
    }

    // ---- WakeLock ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Podroid::Vm").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 最长 12 小时，防泄漏
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---- 通知 ----

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID,
            i18n.getString(R.string.notif_channel_vm),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = i18n.getString(R.string.notif_channel_vm_desc) }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(i18n.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        restartJob?.cancel()
        runner.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.midsancode.podroid.action.START_VM"
        const val ACTION_STOP = "com.midsancode.podroid.action.STOP_VM"
        private const val CHANNEL_ID = "vm_status"
        private const val NOTIF_ID = 1001
        private const val MAX_AUTO_RETRY = 2
        private const val RETRY_DELAY_MS = 4_000L
    }
}
