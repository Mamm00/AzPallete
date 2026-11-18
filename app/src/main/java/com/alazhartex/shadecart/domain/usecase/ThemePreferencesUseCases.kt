package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.repository.ThemePreferencesContract
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams persisted theme appearance settings from the domain preferences contract.
 *
 * Keeps DataStore details hidden behind a Clean Architecture boundary
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1 & Chapter 10.4).
 */
class ObserveThemePreferencesUseCase @Inject constructor(
    private val preferences: ThemePreferencesContract,
) {
    /** Returns a reactive [Flow] of [ThemeSettings]. */
    operator fun invoke(): Flow<ThemeSettings> = preferences.settings
}

/**
 * Updates the active Light / Dark / System theme mode preference.
 *
 * (Pyre, Chapter 5.4 & Chapter 10.4).
 */
class UpdateThemeModeUseCase @Inject constructor(
    private val preferences: ThemePreferencesContract,
) {
    /** Persists [mode] asynchronously in Preferences DataStore. */
    suspend operator fun invoke(mode: ThemeModeSetting) = preferences.setThemeMode(mode)
}

/**
 * Toggles Android 12+ Material You Dynamic Color preference.
 *
 * (Pyre, Chapter 5.4 & Chapter 10.4).
 */
class UpdateDynamicColorUseCase @Inject constructor(
    private val preferences: ThemePreferencesContract,
) {
    /** Persists whether dynamic system colors are [enabled]. */
    suspend operator fun invoke(enabled: Boolean) = preferences.setUseDynamicColor(enabled)
}

/**
 * Selects the active Al AzharTex fallback accent palette.
 *
 * (Pyre, Chapter 5.4 & Chapter 10.4).
 */
class UpdateAccentPaletteUseCase @Inject constructor(
    private val preferences: ThemePreferencesContract,
) {
    /** Persists [palette] asynchronously. */
    suspend operator fun invoke(palette: AccentPaletteSetting) =
        preferences.setAccentPalette(palette)
}
