package com.midsancode.docklet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Docker 蓝 + Material 3
private val DockerBlue = Color(0xFF1D63ED)
private val DockerBlueDark = Color(0xFF8AB4F8)

private val LightColors = lightColorScheme(
    primary = DockerBlue,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DockerBlueDark,
    onPrimary = Color(0xFF00325A),
)

@Composable
fun DockletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
