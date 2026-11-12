package com.midsancode.docklet

import android.app.Application
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.vm.VmManager
import com.midsancode.docklet.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DockletApp : Application() {
    private val engineRepository: EngineRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DockletApp)
            modules(appModule)
        }
        // 提前初始化 VM 管理器（读取磁盘配置等）
        VmManager.init(this)
        // 启动引擎仓库（轮询 + 事件监听）
        engineRepository.start()
    }
}
