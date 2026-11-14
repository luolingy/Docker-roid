package com.midsancode.podroid

import android.app.Application
import com.midsancode.podroid.crash.CrashHandler
import com.midsancode.podroid.data.docker.EngineRepository
import com.midsancode.podroid.data.vm.VmManager
import com.midsancode.podroid.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PodroidApp : Application() {
    private val engineRepository: EngineRepository by inject()

    override fun onCreate() {
        super.onCreate()
        // 最先装崩溃兜底：后面任何一步抛异常都能记录 + 自动重启
        CrashHandler.install(this)
        startKoin {
            androidContext(this@PodroidApp)
            modules(appModule)
        }
        // 提前初始化 VM 管理器（读取磁盘配置等）
        VmManager.init(this)
        // 启动引擎仓库（轮询 + 事件监听）
        engineRepository.start()
    }
}
