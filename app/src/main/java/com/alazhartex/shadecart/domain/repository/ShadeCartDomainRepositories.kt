package com.alazhartex.shadecart.domain.repository

import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.AppLanguageSetting
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AppSessionPreferences
import com.alazhartex.shadecart.domain.model.AskAzPalleteResult
import com.alazhartex.shadecart.domain.model.BuyerSessionState
import com.alazhartex.shadecart.domain.model.CalibrationPreferencesState
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.PriceTierModel
import com.alazhartex.shadecart.domain.model.RemoteSyncPacket
import com.alazhartex.shadecart.domain.model.RemoteSyncPacketResult
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.ScannedBarcodeTarget
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncBatchSummary
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import kotlinx.coroutines.flow.Flow

interface ShadeCartRepositoryContract {
    fun observeCarts(): Flow<List<ShadeCartModel>>
    fun observeCart(cartId: String): Flow<ShadeCartModel?>
    fun observeFabricTypes(): Flow<List<FabricTypeModel>>
    fun observeRolls(fabricTypeId: String? = null): Flow<List<WarehouseRollModel>>
    fun observeSyncQueue(): Flow<List<SyncQueueEntryModel>>
    fun observeConflicts(): Flow<List<SyncConflictModel>>

    suspend fun createOrGetDraftCart(draft: ShadeCartCreateInput): ShadeCartSaveOutcome

    suspend fun saveCartWithOptimisticLock(
        cartId: String,
        expectedVersion: Long,
        fabricTypeId: String?,
        fabricName: String,
        fabricCode: String,
        weightGsm: Int,
        widthCm: Int,
        originalImagePath: String?,
        editedImagePath: String?,
        editStackJson: String,
        headerOcrRaw: String,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome

    suspend fun updateSwatchesWithOptimisticLock(
        cartId: String,
        expectedCartVersion: Long,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome

    suspend fun resolveConflict(
        cartId: String,
        decision: ConflictDecision,
    ): ShadeCartSaveOutcome

    suspend fun syncPendingQueue(): SyncBatchSummary

    suspend fun deleteCart(cartId: String)
}

interface ThemePreferencesContract {
    val settings: Flow<ThemeSettings>
    suspend fun setThemeMode(mode: ThemeModeSetting)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setAccentPalette(palette: AccentPaletteSetting)
}

interface SyncDebugController {
    fun armSimulatedConflict()
    fun isConflictArmed(): Boolean
}

/** A1: Persisted device calibration profiles in DataStore. */
interface CalibrationPreferencesContract {
    val state: Flow<CalibrationPreferencesState>
    suspend fun saveProfile(profile: CalibrationProfile, setAsActive: Boolean = true)
    suspend fun selectProfile(profileId: String)
    suspend fun deleteProfile(profileId: String)
}

/** B2: Buyer Mode presentation session + favorites in DataStore. */
interface BuyerSessionContract {
    val session: Flow<BuyerSessionState>
    suspend fun activateBuyerMode(pin4Digits: String, companyName: String)
    suspend fun verifyAndExitBuyerMode(pin4Digits: String): Boolean
    suspend fun toggleFavorite(swatchId: String)
    suspend fun clearFavorites()
}

/** D3, D4, E3: App role, locale, glove mode, operator history & backup timestamps. */
interface AppSessionPreferencesContract {
    val preferences: Flow<AppSessionPreferences>
    suspend fun setRole(role: AppRole)
    suspend fun setLanguage(language: AppLanguageSetting)
    suspend fun setGloveMode(enabled: Boolean)
    suspend fun setLastOperatorName(name: String)
    suspend fun recordBackupMetadata(timestampMillis: Long, sizeBytes: Long)
    suspend fun setDeltaETolerance(tolerance: Float)
}

/** C2, C3, C5c: Additive mill operations repository contract (lots, reservations, cuts, pricing). */
interface MillInventoryRepositoryContract {
    fun observeAllLots(): Flow<List<DyeLotModel>>
    fun observeLotsForRoll(rollId: String): Flow<List<DyeLotModel>>
    fun observeActiveReservations(): Flow<List<ReservationModel>>
    fun observeAllReservations(): Flow<List<ReservationModel>>
    fun observeCutLogs(): Flow<List<CutLogModel>>
    fun observePriceTiers(): Flow<List<PriceTierModel>>
    fun observeAllSwatchesWithLab(): Flow<List<SwatchModel>>

    suspend fun createRoll(roll: WarehouseRollModel): Result<WarehouseRollModel>
    suspend fun createDyeLot(lot: DyeLotModel): Result<DyeLotModel>
    suspend fun reserveMeters(
        rollId: String,
        swatchId: String,
        meters: Double,
        operatorName: String,
        note: String = "",
    ): Result<ReservationModel>

    suspend fun releaseReservation(reservationId: String): Result<Unit>
    suspend fun logCut(
        rollId: String,
        swatchId: String,
        metersCut: Double,
        pieceCount: Int,
        operatorName: String,
    ): Result<CutLogModel>

    suspend fun updateSwatchPrice(swatchId: String, pricePerMeter: Double?)
    suspend fun upsertPriceTier(tier: PriceTierModel)
    suspend fun getLowStockRolls(): List<WarehouseRollModel>
}

/** E3: Backend readiness remote port implemented by stub & REST adapters. */
interface RemoteSyncPort {
    fun isConnected(): Boolean
    suspend fun pushPacket(packet: RemoteSyncPacket): Result<RemoteSyncPacketResult>
    suspend fun pullDeltaSince(sinceTimestampMillis: Long): Result<List<ShadeCartModel>>
}

/** A2: Optional BLE spectrophotometer integration hook (Nix Pro 2 class). */
interface SpectrophotometerGateway {
    fun isHardwareAvailable(): Boolean
    fun statusDescription(): String
    suspend fun readSingleLabSample(): Result<Triple<Float, Float, Float>>
}

/** C1: QR / Barcode deep-link scanner gateway. */
interface BarcodeScannerGateway {
    suspend fun scanSingleCode(): Result<ScannedBarcodeTarget?>
}

/** B3: Future BLE spectrophotometer port (e.g. Nix Pro / Datacolor). */
interface SpectrophotometerBlePort {
    fun isAvailable(): Boolean
    fun observeReadings(): Flow<Triple<Float, Float, Float>?>
}

/** B3: Future AR Depth fabric overlay port. */
interface ArFabricOverlayPort {
    fun isSupported(): Boolean
}

/** E1: Natural Language Inventory Query gateway (Gemini Nano AICore check + template fallback). */
interface NaturalLanguageQueryGateway {
    suspend fun isNanoAvailable(): Boolean
    suspend fun query(
        naturalQuery: String,
        carts: List<ShadeCartModel>,
        rolls: List<WarehouseRollModel>,
        lots: List<DyeLotModel>,
    ): AskAzPalleteResult
}
