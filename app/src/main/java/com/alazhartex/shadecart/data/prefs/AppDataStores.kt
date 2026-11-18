package com.alazhartex.shadecart.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alazhartex.shadecart.domain.model.AppLanguageSetting
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AppSessionPreferences
import com.alazhartex.shadecart.domain.model.BuyerSessionState
import com.alazhartex.shadecart.domain.model.CalibrationPreferencesState
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.repository.AppSessionPreferencesContract
import com.alazhartex.shadecart.domain.repository.BuyerSessionContract
import com.alazhartex.shadecart.domain.repository.CalibrationPreferencesContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "alazhartex_calibration_prefs")
private val Context.buyerSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "alazhartex_buyer_session")
private val Context.appSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "alazhartex_app_session")

/**
 * A1: DataStore-backed calibration profile repository persisting active ShadeLock profile & presets.
 */
@Singleton
class CalibrationPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : CalibrationPreferencesContract {

    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }

    override val state: Flow<CalibrationPreferencesState> = context.calibrationDataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON]
        val list = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<CalibrationProfile>>(raw) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        CalibrationPreferencesState(
            profiles = list,
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID],
        )
    }

    override suspend fun saveProfile(profile: CalibrationProfile, setAsActive: Boolean) {
        context.calibrationDataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<CalibrationProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == profile.id } + profile
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            if (setAsActive) {
                prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
            }
        }
    }

    override suspend fun selectProfile(profileId: String) {
        context.calibrationDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        context.calibrationDataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<CalibrationProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == profileId }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
            }
        }
    }
}

/**
 * B2: DataStore-backed Buyer Showcase Mode session & PIN repository.
 */
@Singleton
class BuyerSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : BuyerSessionContract {

    private object Keys {
        val IS_BUYER_MODE = booleanPreferencesKey("is_buyer_mode")
        val COMPANY_NAME = stringPreferencesKey("company_name")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val FAVORITES = stringSetPreferencesKey("favorite_swatch_ids")
    }

    override val session: Flow<BuyerSessionState> = context.buyerSessionDataStore.data.map { prefs ->
        BuyerSessionState(
            isBuyerMode = prefs[Keys.IS_BUYER_MODE] ?: false,
            companyName = prefs[Keys.COMPANY_NAME] ?: "Al AzharTex Partner",
            favoritedSwatchIds = prefs[Keys.FAVORITES] ?: emptySet(),
            pinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
        )
    }

    override suspend fun activateBuyerMode(pin4Digits: String, companyName: String) {
        val cleanPin = pin4Digits.filter { it.isDigit() }.take(4).padStart(4, '0')
        context.buyerSessionDataStore.edit { prefs ->
            prefs[Keys.IS_BUYER_MODE] = true
            prefs[Keys.COMPANY_NAME] = companyName.ifBlank { "Al AzharTex Partner" }
            prefs[Keys.PIN_HASH] = hashPin(cleanPin)
        }
    }

    override suspend fun verifyAndExitBuyerMode(pin4Digits: String): Boolean {
        val prefs = context.buyerSessionDataStore.data.first()
        val storedHash = prefs[Keys.PIN_HASH]
        if (storedHash.isNullOrBlank() || storedHash == hashPin(pin4Digits.trim())) {
            context.buyerSessionDataStore.edit { mutable ->
                mutable[Keys.IS_BUYER_MODE] = false
            }
            return true
        }
        return false
    }

    override suspend fun toggleFavorite(swatchId: String) {
        context.buyerSessionDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(swatchId)) {
                current.remove(swatchId)
            }
            prefs[Keys.FAVORITES] = current
        }
    }

    override suspend fun clearFavorites() {
        context.buyerSessionDataStore.edit { prefs ->
            prefs.remove(Keys.FAVORITES)
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("AzPallete:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * D3, D4, E3: DataStore-backed session settings for RBAC role, locale selection, Glove Mode,
 * operator identity, and last encrypted backup timestamp.
 */
@Singleton
class AppSessionPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppSessionPreferencesContract {

    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val LANGUAGE = stringPreferencesKey("language")
        val GLOVE_MODE = booleanPreferencesKey("glove_mode")
        val OPERATOR_NAME = stringPreferencesKey("operator_name")
        val LAST_BACKUP_TS = longPreferencesKey("last_backup_ts")
        val LAST_BACKUP_BYTES = longPreferencesKey("last_backup_bytes")
        val DELTA_E_TOLERANCE = floatPreferencesKey("delta_e_tolerance")
    }

    override val preferences: Flow<AppSessionPreferences> = context.appSessionDataStore.data.map { prefs ->
        val role = prefs[Keys.ROLE]?.let {
            runCatching { AppRole.valueOf(it) }.getOrNull()
        } ?: AppRole.MANAGER
        val language = prefs[Keys.LANGUAGE]?.let {
            runCatching { AppLanguageSetting.valueOf(it) }.getOrNull()
        } ?: AppLanguageSetting.SYSTEM
        AppSessionPreferences(
            role = role,
            language = language,
            gloveMode = prefs[Keys.GLOVE_MODE] ?: false,
            lastOperatorName = prefs[Keys.OPERATOR_NAME] ?: "Mill Operator",
            lastBackupTimestampMillis = prefs[Keys.LAST_BACKUP_TS],
            lastBackupSizeBytes = prefs[Keys.LAST_BACKUP_BYTES],
            deltaETolerance = prefs[Keys.DELTA_E_TOLERANCE] ?: 2.0f,
        )
    }

    override suspend fun setRole(role: AppRole) {
        context.appSessionDataStore.edit { it[Keys.ROLE] = role.name }
    }

    override suspend fun setLanguage(language: AppLanguageSetting) {
        context.appSessionDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setGloveMode(enabled: Boolean) {
        context.appSessionDataStore.edit { it[Keys.GLOVE_MODE] = enabled }
    }

    override suspend fun setLastOperatorName(name: String) {
        context.appSessionDataStore.edit { it[Keys.OPERATOR_NAME] = name.trim().ifBlank { "Mill Operator" } }
    }

    override suspend fun recordBackupMetadata(timestampMillis: Long, sizeBytes: Long) {
        context.appSessionDataStore.edit {
            it[Keys.LAST_BACKUP_TS] = timestampMillis
            it[Keys.LAST_BACKUP_BYTES] = sizeBytes
        }
    }

    override suspend fun setDeltaETolerance(tolerance: Float) {
        context.appSessionDataStore.edit { it[Keys.DELTA_E_TOLERANCE] = tolerance.coerceIn(0.5f, 15.0f) }
    }
}
