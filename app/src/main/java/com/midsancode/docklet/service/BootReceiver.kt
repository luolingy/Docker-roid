package com.midsancode.docklet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.midsancode.docklet.data.prefs.Prefs
import com.midsancode.docklet.data.settings.SettingsRepository
import com.midsancode.docklet.data.vm.VmBundle
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.data.vm.QemuBinaries
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 开机自启：只在用户打开过「随开机自动启动 VM」时才拉起服务。
 *
 * 用 goAsync + runBlocking 读 DataStore：开机广播里必须尽快返回，但 DataStore 是
 * suspend 的；这里读的是本地文件、通常几毫秒，用 runBlocking 比引入第二份配置副本更省心。
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val settingsRepo: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        try {
            VmManager.init(context)
            val settings = runBlocking { settingsRepo.settingsOnce() }
            val bundleReady = VmBundle.isReady(context) && QemuBinaries.isPresent(context)
            if (settings.autoStart && bundleReady && Prefs.keepAlive(context)) {
                VmManager.startVm()
            }
        } catch (_: Throwable) {
            // 开机流程里失败不能崩（否则会被系统记为开机崩溃），静默跳过
        } finally {
            pending.finish()
        }
    }
}
