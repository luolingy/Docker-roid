package com.midsancode.podroid.di

import android.app.Application
import com.midsancode.podroid.data.docker.DockerEngineFactory
import com.midsancode.podroid.data.docker.EngineRepository
import com.midsancode.podroid.data.settings.SettingsRepository
import org.koin.dsl.module

/**
 * 应用级 DI 模块。
 */
val appModule = module {
    // DockerEngineFactory 需要设置里的引擎端口，所以显式注入 SettingsRepository
    single { DockerEngineFactory(get(), get()) }
    single { EngineRepository(get()) }
    single { SettingsRepository(get<Application>()) }
}
