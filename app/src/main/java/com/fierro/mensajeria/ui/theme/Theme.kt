package com.fierro.mensajeria.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// PALETA OSCURA "AURA"
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8A2BE2), // AccentColor
    secondary = Color(0xFF6A5ACD), // SoftPurple
    background = Color(0xFF0D0B1F), // DarkBg
    surface = Color(0xFF1C1A2E), // CardBg
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

// PALETA CLARA "VEE"
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A2BE2),
    secondary = Color(0xFF6A5ACD),
    background = Color(0xFFF5F5FA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0D0B1F),
    onSurface = Color(0xFF0D0B1F)
)

@Composable
fun MensajeriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
