package com.northstar.money.core.designsystem

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFF735B00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF12D6B0),
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF064D42),
    secondary = Color(0xFFFF4778),
    onSecondary = Color(0xFF3F001A),
    secondaryContainer = Color(0xFF5E1631),
    tertiary = Color(0xFF9C6BFF),
    background = Color(0xFF0B0E1A),
    onBackground = Color(0xFFF2F2FA),
    surface = Color(0xFF111526),
    onSurface = Color(0xFFF2F2FA),
    surfaceVariant = Color(0xFF1A1F35),
    onSurfaceVariant = Color(0xFFC5C8D8),
    outline = Color(0xFF535970),
    error = Color(0xFFFF4778),
)

@Composable
fun NorthstarTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
