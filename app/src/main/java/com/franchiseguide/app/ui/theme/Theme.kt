package com.franchiseguide.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6650A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF49454F)
)

@Composable
fun FranchiseGuideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
