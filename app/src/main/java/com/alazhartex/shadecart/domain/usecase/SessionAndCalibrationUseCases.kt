package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.AppLanguageSetting
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AppSessionPreferences
import com.alazhartex.shadecart.domain.model.BuyerSessionState
import com.alazhartex.shadecart.domain.model.CalibrationPreferencesState
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.repository.AppSessionPreferencesContract
import com.alazhartex.shadecart.domain.repository.BuyerSessionContract
import com.alazhartex.shadecart.domain.repository.CalibrationPreferencesContract
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// A1 Calibration UseCases
class ObserveCalibrationStateUseCase @Inject constructor(
    private val prefs: CalibrationPreferencesContract,
) {
    operator fun invoke(): Flow<CalibrationPreferencesState> = prefs.state
}

class SaveCalibrationProfileUseCase @Inject constructor(
    private val prefs: CalibrationPreferencesContract,
) {
    suspend operator fun invoke(profile: CalibrationProfile, setAsActive: Boolean = true) =
        prefs.saveProfile(profile, setAsActive)
}

class SelectCalibrationProfileUseCase @Inject constructor(
    private val prefs: CalibrationPreferencesContract,
) {
    suspend operator fun invoke(profileId: String) = prefs.selectProfile(profileId)
}

class DeleteCalibrationProfileUseCase @Inject constructor(
    private val prefs: CalibrationPreferencesContract,
) {
    suspend operator fun invoke(profileId: String) = prefs.deleteProfile(profileId)
}

// B2 Buyer Mode UseCases
class ObserveBuyerSessionUseCase @Inject constructor(
    private val repo: BuyerSessionContract,
) {
    operator fun invoke(): Flow<BuyerSessionState> = repo.session
}

class ActivateBuyerModeUseCase @Inject constructor(
    private val repo: BuyerSessionContract,
) {
    suspend operator fun invoke(pin4Digits: String, companyName: String) =
        repo.activateBuyerMode(pin4Digits, companyName)
}

class ExitBuyerModeUseCase @Inject constructor(
    private val repo: BuyerSessionContract,
) {
    suspend operator fun invoke(pin4Digits: String): Boolean =
        repo.verifyAndExitBuyerMode(pin4Digits)
}

class ToggleBuyerFavoriteUseCase @Inject constructor(
    private val repo: BuyerSessionContract,
) {
    suspend operator fun invoke(swatchId: String) = repo.toggleFavorite(swatchId)
}

// D3, D4, E3 App Session UseCases
class ObserveAppSessionPreferencesUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    operator fun invoke(): Flow<AppSessionPreferences> = repo.preferences
}

class UpdateAppRoleUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    suspend operator fun invoke(role: AppRole) = repo.setRole(role)
}

class UpdateAppLanguageUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    suspend operator fun invoke(language: AppLanguageSetting) = repo.setLanguage(language)
}

class UpdateGloveModeUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    suspend operator fun invoke(enabled: Boolean) = repo.setGloveMode(enabled)
}

class UpdateLastOperatorNameUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    suspend operator fun invoke(name: String) = repo.setLastOperatorName(name)
}

class RecordBackupMetadataUseCase @Inject constructor(
    private val repo: AppSessionPreferencesContract,
) {
    suspend operator fun invoke(timestampMillis: Long, sizeBytes: Long) =
        repo.recordBackupMetadata(timestampMillis, sizeBytes)
}
