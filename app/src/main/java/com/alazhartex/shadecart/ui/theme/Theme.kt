package com.alazhartex.shadecart.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.data.prefs.AccentPaletteId
import com.alazhartex.shadecart.data.prefs.ThemeMode
import com.alazhartex.shadecart.data.prefs.ThemePreferences

private val AlAzharShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AlAzharTexTheme(
    preferences: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        AccentPalettes.colorSchemeFor(
            palette = preferences.accentPalette,
            darkTheme = darkTheme,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AlAzharShapes,
        content = content,
    )
}
