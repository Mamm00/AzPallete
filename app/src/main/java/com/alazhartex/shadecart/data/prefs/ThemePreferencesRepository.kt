package com.alazhartex.shadecart.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "alazhartex_theme_prefs")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentPaletteId(val displayName: String, val primaryHex: String, val secondaryHex: String) {
    AZHAR_NAVY_CRIMSON("Al Azhar Navy & Crimson", "#102A71", "#D90A0A"),
    AZHAR_EMERALD_SAND("Azhar Emerald & Sand", "#0F5132", "#D4A017"),
    ROYAL_INDIGO_GOLD("Royal Indigo & Gold", "#1E3A8A", "#CA8A04"),
    TERRACOTTA_CHARCOAL("Terracotta & Charcoal", "#9A3412", "#334155"),
}

data class ThemePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentPalette: AccentPaletteId = AccentPaletteId.AZHAR_NAVY_CRIMSON,
)

@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    }

    val preferences: Flow<ThemePreferences> = context.themeDataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val dynamic = prefs[Keys.DYNAMIC_COLOR] ?: true
        val accent = prefs[Keys.ACCENT_PALETTE]?.let {
            runCatching { AccentPaletteId.valueOf(it) }.getOrNull()
        } ?: AccentPaletteId.AZHAR_NAVY_CRIMSON
        ThemePreferences(
            themeMode = mode,
            useDynamicColor = dynamic,
            accentPalette = accent,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccentPalette(palette: AccentPaletteId) {
        context.themeDataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }
}
