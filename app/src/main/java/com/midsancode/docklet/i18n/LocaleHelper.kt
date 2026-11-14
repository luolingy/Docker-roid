package com.midsancode.docklet.i18n

import android.content.Context
import android.content.res.Configuration
import com.midsancode.docklet.data.prefs.Prefs
import java.util.Locale

/**
 * 应用内语言覆盖。
 *
 * 实现方式：在 [android.app.Activity.attachBaseContext] 里把配置里的 locale 换掉，
 * 再 `createConfigurationContext`。这样 `stringResource()` / `getString()` 都会走
 * 目标语言的资源目录（values-zh / values），不需要 appcompat。
 *
 * 系统语言 → 资源选择规则：设备是中文就命中 values-zh，否则落到默认 values（英文）。
 * 用户在设置里显式选语言时，直接覆盖 locale，不再看系统。
 */
object LocaleHelper {

    /** 支持的语言。tag 为空串表示跟随系统。 */
    val SUPPORTED = listOf("", "zh", "en")

    fun wrap(base: Context): Context {
        val tag = Prefs.language(base)
        if (tag.isBlank()) return base
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull()
            ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
