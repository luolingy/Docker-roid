package com.midsancode.podroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主题色（强调色）可选集合。
 *
 * 只换"强调色系"（primary / tertiary 及其容器），中性表面色（background、surfaceContainer*）
 * 保持不变——否则每换一个色都要重调整套灰阶，对比度很容易翻车。
 */
enum class BrandPalette(val key: String) {
    Blue("blue"),
    Teal("teal"),
    Purple("purple"),
    Green("green"),
    Orange("orange");

    companion object {
        fun from(key: String?): BrandPalette =
            entries.firstOrNull { it.key == key } ?: Blue
    }
}

/** 一套强调色：主色 + 第三色（统计/提示点缀）。 */
private data class Accent(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

private fun lightAccent(p: BrandPalette): Accent = when (p) {
    BrandPalette.Blue -> Accent(
        Color(0xFF1B5FDB), Color.White, Color(0xFFDCE6FF), Color(0xFF00174A),
        Color(0xFF6B4EA0), Color.White, Color(0xFFEADDFF), Color(0xFF25114A),
    )
    BrandPalette.Teal -> Accent(
        Color(0xFF00696D), Color.White, Color(0xFFB2ECEF), Color(0xFF002022),
        Color(0xFF7A4E00), Color.White, Color(0xFFFFDDB8), Color(0xFF271900),
    )
    BrandPalette.Purple -> Accent(
        Color(0xFF6B4EA0), Color.White, Color(0xFFEADDFF), Color(0xFF25114A),
        Color(0xFF00696D), Color.White, Color(0xFFB2ECEF), Color(0xFF002022),
    )
    BrandPalette.Green -> Accent(
        Color(0xFF2E6B3E), Color.White, Color(0xFFB7F0C4), Color(0xFF00210B),
        Color(0xFF4A6076), Color.White, Color(0xFFD3E4F8), Color(0xFF051D2F),
    )
    BrandPalette.Orange -> Accent(
        Color(0xFF8B5000), Color.White, Color(0xFFFFDDB8), Color(0xFF2C1600),
        Color(0xFF5B5F7A), Color.White, Color(0xFFDFE1F9), Color(0xFF171B33),
    )
}

private fun darkAccent(p: BrandPalette): Accent = when (p) {
    BrandPalette.Blue -> Accent(
        Color(0xFFAEC6FF), Color(0xFF002E69), Color(0xFF0B4BA8), Color(0xFFDCE6FF),
        Color(0xFFD3BBFF), Color(0xFF3B1F6B), Color(0xFF4E3180), Color(0xFFEADDFF),
    )
    BrandPalette.Teal -> Accent(
        Color(0xFF6FD4D8), Color(0xFF00373A), Color(0xFF004F52), Color(0xFFB2ECEF),
        Color(0xFFFFB870), Color(0xFF4A2800), Color(0xFF6B3D00), Color(0xFFFFDDB8),
    )
    BrandPalette.Purple -> Accent(
        Color(0xFFD3BBFF), Color(0xFF3B1F6B), Color(0xFF4E3180), Color(0xFFEADDFF),
        Color(0xFF6FD4D8), Color(0xFF00373A), Color(0xFF004F52), Color(0xFFB2ECEF),
    )
    BrandPalette.Green -> Accent(
        Color(0xFF9BD8AB), Color(0xFF00391A), Color(0xFF1E5230), Color(0xFFB7F0C4),
        Color(0xFFB7C9DF), Color(0xFF203243), Color(0xFF374A5D), Color(0xFFD3E4F8),
    )
    BrandPalette.Orange -> Accent(
        Color(0xFFFFB870), Color(0xFF4A2800), Color(0xFF6B3D00), Color(0xFFFFDDB8),
        Color(0xFFC3C5DD), Color(0xFF2C2F45), Color(0xFF42455F), Color(0xFFDFE1F9),
    )
}

/** 中性表面色（浅色）：与强调色无关，保持稳定。 */
private fun lightScheme(a: Accent) = lightColorScheme(
    primary = a.primary,
    onPrimary = a.onPrimary,
    primaryContainer = a.primaryContainer,
    onPrimaryContainer = a.onPrimaryContainer,

    // 次级色跟主色同源：M3 默认的 secondary 是独立色，两套色放一起会显得像两个设计系统
    secondary = a.primary,
    onSecondary = a.onPrimary,
    secondaryContainer = a.primaryContainer,
    onSecondaryContainer = a.onPrimaryContainer,

    tertiary = a.tertiary,
    onTertiary = a.onTertiary,
    tertiaryContainer = a.tertiaryContainer,
    onTertiaryContainer = a.onTertiaryContainer,

    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E4EC),
    onSurfaceVariant = Color(0xFF454A53),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEFF6),
    surfaceContainerHigh = Color(0xFFE5EAF3),
    surfaceContainerHighest = Color(0xFFDFE4ED),

    outline = Color(0xFF757A84),
    outlineVariant = Color(0xFFC5CAD4),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** 中性表面色（深色）：背景压到接近纯黑，卡片靠 surfaceContainer 抬起来做层次。 */
private fun darkScheme(a: Accent) = darkColorScheme(
    primary = a.primary,
    onPrimary = a.onPrimary,
    primaryContainer = a.primaryContainer,
    onPrimaryContainer = a.onPrimaryContainer,

    secondary = a.primary,
    onSecondary = a.onPrimary,
    secondaryContainer = a.primaryContainer,
    onSecondaryContainer = a.onPrimaryContainer,

    tertiary = a.tertiary,
    onTertiary = a.onTertiary,
    tertiaryContainer = a.tertiaryContainer,
    onTertiaryContainer = a.onTertiaryContainer,

    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE2E4E9),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFE2E4E9),
    surfaceVariant = Color(0xFF41464F),
    onSurfaceVariant = Color(0xFFC1C6CF),

    surfaceContainerLowest = Color(0xFF0A0C0F),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1A1D22),
    surfaceContainerHigh = Color(0xFF22262C),
    surfaceContainerHighest = Color(0xFF2B2F36),

    outline = Color(0xFF8B909A),
    outlineVariant = Color(0xFF41464F),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 标题略加粗、行高收紧，避免中文标题看起来松垮。 */
private val PodroidTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** 圆角统一放大一点，卡片更"软"。 */
private val PodroidShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/**
 * 当前是否深色。
 *
 * 不能直接用 [isSystemInDarkTheme]：明暗模式是用户可选的三态（跟随系统/浅色/深色），
 * 手动选「深色」而系统是浅色时，[toneColors] 之类的语义色必须跟着变深。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun PodroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: BrandPalette = BrandPalette.Blue,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkScheme(darkAccent(palette)) else lightScheme(lightAccent(palette))
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PodroidTypography,
            shapes = PodroidShapes,
            content = content,
        )
    }
}

/** 供个别需要自定义字重的地方引用。 */
internal val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
