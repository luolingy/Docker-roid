package com.dockerroid.di

import android.app.Application
import com.dockerroid.data.docker.DockerEngineFactory
import com.dockerroid.data.docker.EngineRepository
import com.dockerroid.data.settings.SettingsRepository
import org.koin.dsl.module

/**
 * 应用级 DI 模块。
 */
val appModule = module {
    single { DockerEngineFactory(get()) }
    single { EngineRepository(get()) }
    single { SettingsRepository(get<Application>()) }
}
