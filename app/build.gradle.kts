import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// release 签名：口令放在 keystore.properties（已 gitignore），缺失时回退 debug 签名，
// 保证 clone 下来直接 assembleRelease 也能出包（只是不可上架）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.midsancode.podroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.midsancode.podroid"
        minSdk = 26           // Android 8.0
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"

        // 仅 arm64（内核/QEMU 二进制都是 aarch64）
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 关于页与更新检测需要 BuildConfig.VERSION_NAME / VERSION_CODE
        buildConfig = true
    }

    packaging {
        jniLibs {
            // 必须解压 native 库到 nativeLibraryDir（QEMU 及其依赖库从此处 exec/加载）
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // initrd.img 内部已是 gzip，不需要再压；不压缩才能用 assets.openFd() 拿到长度，
        // 从而做「大小一致就跳过解压」的幂等判断。
        noCompress += "img"
    }

    // 大体积底座（rootfs/kernel/qemu）不在此打包，改为首次启动按需下载
    // 若要内置，把文件放到 app/src/main/assets/ 并在 VmBundle 里读取
}

dependencies {
    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // 网络（Docker REST）
    implementation(libs.okhttp)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // 存储
    implementation(libs.androidx.datastore.preferences)

    // 测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
