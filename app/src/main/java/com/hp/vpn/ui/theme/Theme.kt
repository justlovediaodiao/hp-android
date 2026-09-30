package com.hp.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondaryContainer = Color(0xFFDFE2EB),
    onSecondaryContainer = Color(0xFF171C24),
    tertiary = Color(0xFF1E7A4B),
    onTertiary = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00458F),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondaryContainer = Color(0xFF3A4048),
    onSecondaryContainer = Color(0xFFDEE3EA),
    tertiary = Color(0xFF7FD9A3),
    onTertiary = Color(0xFF00391F),
)

@Composable
fun HpVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
