package com.midsancode.podroid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 语义色。
 *
 * 之前各处直接写 `Color(0xFF2E7D32)` 这类硬编码：浅色主题下勉强能看，深色主题下
 * 绿底绿字对比度极低。这里按主题分别给「前景色 + 容器色」一对，统一走 [toneColors]。
 */
enum class Tone { Success, Warning, Danger, Info, Neutral }

@Immutable
data class ToneColors(
    /** 文字/图标色，保证在 [container] 上有足够对比度。 */
    val content: Color,
    /** 胶囊/徽章底色。 */
    val container: Color,
)

@Composable
fun toneColors(tone: Tone): ToneColors {
    // 用主题注入的深色标记，而不是 isSystemInDarkTheme()：
    // 用户可以在设置里手动选深色/浅色，跟系统不一定一致。
    val dark = LocalDarkTheme.current
    return when (tone) {
        Tone.Success -> if (dark) ToneColors(Color(0xFF8BE0A5), Color(0xFF17351F))
        else ToneColors(Color(0xFF15683A), Color(0xFFDCF3E4))

        Tone.Warning -> if (dark) ToneColors(Color(0xFFF6C96B), Color(0xFF3B2C0C))
        else ToneColors(Color(0xFF8A5300), Color(0xFFFFEECC))

        Tone.Danger -> if (dark) ToneColors(Color(0xFFFFB4AB), Color(0xFF491510))
        else ToneColors(Color(0xFFB3261E), Color(0xFFFFE2DE))

        Tone.Info -> if (dark) ToneColors(Color(0xFFAEC6FF), Color(0xFF12294C))
        else ToneColors(Color(0xFF1B5FDB), Color(0xFFDCE6FF))

        Tone.Neutral -> if (dark) ToneColors(Color(0xFFC1C6CF), Color(0xFF2B2F36))
        else ToneColors(Color(0xFF525760), Color(0xFFE6EAF1))
    }
}

/** 容器状态 → 语义色。 */
fun stateTone(state: String): Tone = when (state.lowercase()) {
    "running" -> Tone.Success
    "paused", "restarting" -> Tone.Warning
    "created" -> Tone.Info
    "exited", "dead", "removing" -> Tone.Neutral
    else -> Tone.Neutral
}

/** VM 状态 → 语义色（状态灯、徽章共用）。 */
fun vmTone(ready: Boolean, booting: Boolean, error: Boolean): Tone = when {
    error -> Tone.Danger
    ready -> Tone.Success
    booting -> Tone.Warning
    else -> Tone.Neutral
}
