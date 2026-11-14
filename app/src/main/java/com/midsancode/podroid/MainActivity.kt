package com.midsancode.podroid

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.midsancode.podroid.data.settings.SettingsRepository
import com.midsancode.podroid.i18n.LocaleHelper
import com.midsancode.podroid.ui.PodroidRoot
import com.midsancode.podroid.ui.theme.BrandPalette
import com.midsancode.podroid.ui.theme.PodroidTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    /** 由 [com.midsancode.podroid.crash.CrashHandler] 拉起时置位，用于提示"上次崩了"。 */
    private var afterCrash = false

    /**
     * 应用内语言覆盖必须在 base context 生效之前套上，
     * 否则 Compose 里已经解析过一遍资源，切换语言要等下次重建才看到效果。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        afterCrash = intent?.getBooleanExtra(EXTRA_AFTER_CRASH, false) == true

        setContent {
            val settingsRepo: SettingsRepository = koinInject()
            val settings by settingsRepo.current.collectAsState()
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            PodroidTheme(
                darkTheme = dark,
                palette = BrandPalette.from(settings.themeColor),
            ) {
                PodroidRoot(afterCrash = afterCrash)
            }
        }
    }

    companion object {
        const val EXTRA_AFTER_CRASH = "com.midsancode.podroid.extra.AFTER_CRASH"
    }
}
