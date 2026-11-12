package com.midsancode.docklet.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.midsancode.docklet.MainActivity
import com.midsancode.docklet.R
import com.midsancode.docklet.data.vm.QemuRunner
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.VmStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务：持有 WakeLock + 常驻通知，托管 QemuRunner 的生命周期。
 *
 * Android 只允许前台服务长期保活后台进程，所以 QEMU 必须挂在这里。
 */
class VmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runner: QemuRunner
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        runner = QemuRunner(applicationContext, scope)
        createChannel()
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVm()
            ACTION_STOP -> stopVm()
        }
        return START_STICKY
    }

    private fun startVm() {
        startForegroundCompat(buildNotification(getString(R.string.vm_status_booting)))
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
                    is VmStatus.Stopped -> getString(R.string.vm_status_stopped)
                    is VmStatus.Booting -> status.bootStage ?: getString(R.string.vm_status_booting)
                    is VmStatus.Ready -> getString(R.string.vm_status_ready)
                    is VmStatus.Error -> "错误：${status.message}"
                }
                updateNotification(text)

                if (status is VmStatus.Stopped) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    // ---- WakeLock ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Docklet::Vm").apply {
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
            "VM 运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "显示 Linux VM 与容器引擎的运行状态" }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Docklet")
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
        runner.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.midsancode.docklet.action.START_VM"
        const val ACTION_STOP = "com.midsancode.docklet.action.STOP_VM"
        private const val CHANNEL_ID = "vm_status"
        private const val NOTIF_ID = 1001
    }
}
