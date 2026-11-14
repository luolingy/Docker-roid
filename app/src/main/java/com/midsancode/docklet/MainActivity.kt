package com.midsancode.docklet

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.midsancode.docklet.data.settings.SettingsRepository
import com.midsancode.docklet.i18n.LocaleHelper
import com.midsancode.docklet.ui.DockletRoot
import com.midsancode.docklet.ui.theme.BrandPalette
import com.midsancode.docklet.ui.theme.DockletTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    /** 由 [com.midsancode.docklet.crash.CrashHandler] 拉起时置位，用于提示"上次崩了"。 */
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
            DockletTheme(palette = BrandPalette.from(settings.themeColor)) {
                DockletRoot(afterCrash = afterCrash)
            }
        }
    }

    companion object {
        const val EXTRA_AFTER_CRASH = "com.midsancode.docklet.extra.AFTER_CRASH"
    }
}
