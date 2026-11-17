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
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings

private val AlAzharShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Top-level Material 3 theme composable applying dynamic color (Android 12+), system/light/dark
 * mode preferences, custom [AccentPalettes], [AlAzharTypography], and rounded [AlAzharShapes]
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 5.1, 5.2, & 5.4).
 */
@Composable
fun AlAzharTexTheme(
    preferences: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (preferences.themeMode) {
        ThemeModeSetting.SYSTEM -> isSystemInDarkTheme()
        ThemeModeSetting.LIGHT -> false
        ThemeModeSetting.DARK -> true
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
        typography = AlAzharTypography,
        shapes = AlAzharShapes,
        content = content,
    )
}
