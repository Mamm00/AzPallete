package com.alazhartex.shadecart.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.repository.ThemePreferencesContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "alazhartex_theme_prefs")

/**
 * Preferences DataStore implementation of [ThemePreferencesContract].
 *
 * Persists lightweight theme mode, Material You dynamic color toggle, and brand accent palette
 * choices asynchronously off the main thread without using deprecated `SharedPreferences`
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.4).
 */
@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemePreferencesContract {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    }

    override val settings: Flow<ThemeSettings> = context.themeDataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeModeSetting.valueOf(it) }.getOrNull()
        } ?: ThemeModeSetting.SYSTEM
        val dynamic = prefs[Keys.DYNAMIC_COLOR] ?: true
        val accent = prefs[Keys.ACCENT_PALETTE]?.let {
            runCatching { AccentPaletteSetting.valueOf(it) }.getOrNull()
        } ?: AccentPaletteSetting.AZHAR_NAVY_CRIMSON
        ThemeSettings(
            themeMode = mode,
            useDynamicColor = dynamic,
            accentPalette = accent,
        )
    }

    override suspend fun setThemeMode(mode: ThemeModeSetting) {
        context.themeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAccentPalette(palette: AccentPaletteSetting) {
        context.themeDataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }
}
