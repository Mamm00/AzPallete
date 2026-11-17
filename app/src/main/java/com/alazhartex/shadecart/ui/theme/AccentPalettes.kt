package com.alazhartex.shadecart.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting

/**
 * Curated Material 3 light and dark [ColorScheme] definitions matching Al AzharTex textile brand
 * palettes when Material You dynamic color is disabled or unavailable (pre-Android 12)
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 5.2 & Chapter 5.4).
 */
object AccentPalettes {

    private val AzharNavyCrimsonLight = lightColorScheme(
        primary = Color(0xFF102A71),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE2FF),
        onPrimaryContainer = Color(0xFF001552),
        secondary = Color(0xFFD90A0A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDAD6),
        onSecondaryContainer = Color(0xFF410002),
        tertiary = Color(0xFF0F5132),
        onTertiary = Color.White,
        background = Color(0xFFFAF9F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFECEAE2),
    )

    private val AzharNavyCrimsonDark = darkColorScheme(
        primary = Color(0xFFB5C4FF),
        onPrimary = Color(0xFF002584),
        primaryContainer = Color(0xFF1A388F),
        onPrimaryContainer = Color(0xFFDCE2FF),
        secondary = Color(0xFFFFB4AB),
        onSecondary = Color(0xFF690005),
        secondaryContainer = Color(0xFF93000A),
        onSecondaryContainer = Color(0xFFFFDAD6),
        tertiary = Color(0xFF8CD7A8),
        onTertiary = Color(0xFF00391F),
        background = Color(0xFF0E1320),
        surface = Color(0xFF141B2D),
        surfaceVariant = Color(0xFF232B3E),
    )

    private val AzharEmeraldSandLight = lightColorScheme(
        primary = Color(0xFF0F5132),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA9F2C6),
        onPrimaryContainer = Color(0xFF002111),
        secondary = Color(0xFFB08207),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE59E),
        onSecondaryContainer = Color(0xFF261A00),
        background = Color(0xFFFFFCF5),
        surface = Color.White,
        surfaceVariant = Color(0xFFEBE8DE),
    )

    private val AzharEmeraldSandDark = darkColorScheme(
        primary = Color(0xFF8CD7A8),
        onPrimary = Color(0xFF00391F),
        primaryContainer = Color(0xFF005230),
        onPrimaryContainer = Color(0xFFA9F2C6),
        secondary = Color(0xFFF3C84B),
        onSecondary = Color(0xFF3F2E00),
        secondaryContainer = Color(0xFF5B4300),
        onSecondaryContainer = Color(0xFFFFE59E),
        background = Color(0xFF0F1511),
        surface = Color(0xFF161E19),
        surfaceVariant = Color(0xFF253029),
    )

    private val RoyalIndigoGoldLight = lightColorScheme(
        primary = Color(0xFF1E3A8A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBE2FF),
        onPrimaryContainer = Color(0xFF00164E),
        secondary = Color(0xFFCA8A04),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE08A),
        onSecondaryContainer = Color(0xFF271900),
        background = Color(0xFFF8F9FC),
        surface = Color.White,
        surfaceVariant = Color(0xFFE6E9F2),
    )

    private val RoyalIndigoGoldDark = darkColorScheme(
        primary = Color(0xFFB3C5FF),
        onPrimary = Color(0xFF00287C),
        primaryContainer = Color(0xFF1D3B90),
        onPrimaryContainer = Color(0xFFDBE2FF),
        secondary = Color(0xFFF3C43F),
        onSecondary = Color(0xFF3E2D00),
        secondaryContainer = Color(0xFF5A4300),
        onSecondaryContainer = Color(0xFFFFE08A),
        background = Color(0xFF10131C),
        surface = Color(0xFF171C29),
        surfaceVariant = Color(0xFF262D3E),
    )

    private val TerracottaCharcoalLight = lightColorScheme(
        primary = Color(0xFF9A3412),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCF),
        onPrimaryContainer = Color(0xFF380C00),
        secondary = Color(0xFF334155),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD8E2F0),
        onSecondaryContainer = Color(0xFF111C2C),
        background = Color(0xFFFCF9F6),
        surface = Color.White,
        surfaceVariant = Color(0xFFEEE6E0),
    )

    private val TerracottaCharcoalDark = darkColorScheme(
        primary = Color(0xFFFFB59D),
        onPrimary = Color(0xFF5C1900),
        primaryContainer = Color(0xFF7D2709),
        onPrimaryContainer = Color(0xFFFFDBCF),
        secondary = Color(0xFFBCC7D8),
        onSecondary = Color(0xFF263140),
        secondaryContainer = Color(0xFF3B485A),
        onSecondaryContainer = Color(0xFFD8E2F0),
        background = Color(0xFF171210),
        surface = Color(0xFF201A17),
        surfaceVariant = Color(0xFF332B27),
    )

    /** Resolves the Material 3 [ColorScheme] for [palette] and [darkTheme]. */
    fun colorSchemeFor(palette: AccentPaletteSetting, darkTheme: Boolean): ColorScheme = when (palette) {
        AccentPaletteSetting.AZHAR_NAVY_CRIMSON ->
            if (darkTheme) AzharNavyCrimsonDark else AzharNavyCrimsonLight
        AccentPaletteSetting.AZHAR_EMERALD_SAND ->
            if (darkTheme) AzharEmeraldSandDark else AzharEmeraldSandLight
        AccentPaletteSetting.ROYAL_INDIGO_GOLD ->
            if (darkTheme) RoyalIndigoGoldDark else RoyalIndigoGoldLight
        AccentPaletteSetting.TERRACOTTA_CHARCOAL ->
            if (darkTheme) TerracottaCharcoalDark else TerracottaCharcoalLight
    }
}
