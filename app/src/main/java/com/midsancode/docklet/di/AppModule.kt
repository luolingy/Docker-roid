package com.midsancode.docklet.di

import android.app.Application
import com.midsancode.docklet.data.docker.DockerEngineFactory
import com.midsancode.docklet.data.docker.EngineRepository
import com.midsancode.docklet.data.settings.SettingsRepository
import org.koin.dsl.module

/**
 * 应用级 DI 模块。
 */
val appModule = module {
    single { DockerEngineFactory(get()) }
    single { EngineRepository(get()) }
    single { SettingsRepository(get<Application>()) }
}
