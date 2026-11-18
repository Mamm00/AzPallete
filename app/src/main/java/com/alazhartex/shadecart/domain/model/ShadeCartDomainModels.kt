package com.alazhartex.shadecart.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Pure Kotlin bidi isolation helper preventing RTL reversal of HEX codes, roll codes, and metric
 * numbers in Arabic/mixed-script UI and exports (D3).
 */
fun String.ltrIsolate(): String = "\u2066$this\u2069"

enum class SyncState {
    PENDING,
    SYNCED,
    CONFLICT,
    FAILED,
}

enum class RollStockStatus {
    IN_STOCK,
    LOW_STOCK,
    RESERVED,
    OUT_OF_STOCK,
}

enum class WeaveTag(val label: String) {
    MATTE("Matte"),
    TWILL("Twill"),
    SATIN("Satin / Silk"),
    LINEN("Linen"),
}

enum class ReservationStatusModel {
    ACTIVE,
    RELEASED,
    FULFILLED,
}

data class FabricTypeModel(
    val id: String,
    val name: String,
    val code: String,
    val weightGsm: Int,
    val widthCm: Int,
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

data class WarehouseRollModel(
    val id: String,
    val rollCode: String,
    val fabricTypeId: String,
    val shadeLabel: String,
    val colorName: String,
    val colorHex: String,
    val warehouseShed: String,
    val lengthMeters: Double,
    val availableMeters: Double = lengthMeters,
    val lotCode: String = "",
    val lowStockThresholdMeters: Double = 50.0,
    val qrPayload: String = "",
    val status: RollStockStatus = RollStockStatus.IN_STOCK,
    val version: Long = 1L,
    val updatedAt: Long = 0L,
) {
    val reservedMeters: Double
        get() = (lengthMeters - availableMeters).coerceAtLeast(0.0)
}

data class SwatchModel(
    val id: String,
    val shadeCartId: String,
    val fabricTypeId: String,
    val rollId: String? = null,
    val linkedRoll: WarehouseRollModel? = null,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String = "",
    val positionOrder: Int,
    val bboxLeft: Float = 0f,
    val bboxTop: Float = 0f,
    val bboxRight: Float = 0f,
    val bboxBottom: Float = 0f,
    val isOverridden: Boolean = false,
    val labL: Double = 0.0,
    val labA: Double = 0.0,
    val labB: Double = 0.0,
    val weaveTag: WeaveTag = WeaveTag.MATTE,
    val sheenScore: Float = 0f,
    val pricePerMeter: Double? = null,
    val currencyCode: String = "EGP",
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

data class ShadeCartModel(
    val id: String,
    val fabricTypeId: String?,
    val fabricType: FabricTypeModel? = null,
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int,
    val widthCm: Int,
    val originalImagePath: String?,
    val editedImagePath: String?,
    val editStackJson: String,
    val headerOcrRaw: String = "",
    val calibrationProfileId: String? = null,
    val ambientLux: Float = -1f,
    val qrDeepLink: String = "",
    val isArchived: Boolean = false,
    val syncStatus: SyncState = SyncState.PENDING,
    val version: Long = 1L,
    val lastSyncedVersion: Long = 0L,
    val updatedAt: Long = 0L,
    val swatches: List<SwatchModel> = emptyList(),
)

data class SyncQueueEntryModel(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val status: SyncState,
    val attemptCount: Int,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ConflictOrigin {
    CONCURRENT_LOCAL_WRITE,
    REMOTE_SYNC_VERSION_MISMATCH,
}

enum class ConflictDecision {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

data class SyncConflictModel(
    val cartId: String,
    val fabricName: String,
    val fabricCode: String,
    val localVersion: Long,
    val remoteVersion: Long,
    val localSwatchCount: Int,
    val remoteSwatchCount: Int,
    val source: ConflictOrigin,
    val detectedAt: Long,
)

data class SwatchInput(
    val id: String = UUID.randomUUID().toString(),
    val fabricTypeId: String = "",
    val rollId: String? = null,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String = "",
    val positionOrder: Int = 0,
    val bboxLeft: Float = 0f,
    val bboxTop: Float = 0f,
    val bboxRight: Float = 0f,
    val bboxBottom: Float = 0f,
    val isOverridden: Boolean = false,
    val labL: Double = 0.0,
    val labA: Double = 0.0,
    val labB: Double = 0.0,
    val weaveTag: WeaveTag = WeaveTag.MATTE,
    val sheenScore: Float = 0f,
    val pricePerMeter: Double? = null,
    val currencyCode: String = "EGP",
    val version: Long = 1L,
)

data class ShadeCartCreateInput(
    val id: String = UUID.randomUUID().toString(),
    val fabricTypeId: String? = null,
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int,
    val widthCm: Int,
    val originalImagePath: String? = null,
    val editedImagePath: String? = null,
    val editStackJson: String = "{}",
    val headerOcrRaw: String = "",
    val calibrationProfileId: String? = null,
    val ambientLux: Float = -1f,
    val qrDeepLink: String = "",
    val swatches: List<SwatchInput> = emptyList(),
)

sealed interface ShadeCartSaveOutcome {
    data class Saved(
        val cartId: String,
        val newVersion: Long,
    ) : ShadeCartSaveOutcome

    data class Conflict(
        val conflict: SyncConflictModel,
    ) : ShadeCartSaveOutcome

    data class NotFound(
        val cartId: String,
    ) : ShadeCartSaveOutcome
}

data class SyncBatchSummary(
    val processed: Int,
    val synced: Int,
    val conflicts: Int,
    val failed: Int,
)

enum class ThemeModeSetting {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentPaletteSetting(
    val displayName: String,
    val primaryHex: String,
    val secondaryHex: String,
) {
    AZHAR_NAVY_CRIMSON("Al Azhar Navy & Crimson", "#102A71", "#D90A0A"),
    AZHAR_EMERALD_SAND("Azhar Emerald & Sand", "#0F5132", "#D4A017"),
    ROYAL_INDIGO_GOLD("Royal Indigo & Gold", "#1E3A8A", "#CA8A04"),
    TERRACOTTA_CHARCOAL("Terracotta & Charcoal", "#9A3412", "#334155"),
}

data class ThemeSettings(
    val themeMode: ThemeModeSetting = ThemeModeSetting.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentPalette: AccentPaletteSetting = AccentPaletteSetting.AZHAR_NAVY_CRIMSON,
)

sealed interface AsyncResult<out T> {
    data object Loading : AsyncResult<Nothing>
    data class Success<T>(val data: T) : AsyncResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : AsyncResult<Nothing>
}

// ---------------------------------------------------------------------------
// A1 — ShadeLock Calibration Profiles
// ---------------------------------------------------------------------------

@Serializable
data class CalibrationProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Showroom D65",
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    val ambientLux: Float = -1f,
    val matrix3x3: List<Float>? = null,
    val deviceId: String = "android-device",
    val capturedAt: Long = System.currentTimeMillis(),
) {
    val profileName: String get() = name
    val luxLevel: Float? get() = if (ambientLux >= 0f) ambientLux else null
}

data class CalibrationPreferencesState(
    val profiles: List<CalibrationProfile> = emptyList(),
    val activeProfileId: String? = null,
) {
    val activeProfile: CalibrationProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    fun isRecalibrationRecommended(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val active = activeProfile ?: return false
        val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
        return (nowMillis - active.capturedAt) > thirtyDaysMillis
    }
}

// ---------------------------------------------------------------------------
// A2 & C4 — Perceptual ΔE Duplicate Guard & Reverse Shade Finder
// ---------------------------------------------------------------------------

data class DuplicateShadeWarning(
    val existingSwatchId: String,
    val existingSwatchNumber: String,
    val existingColorName: String,
    val existingCartName: String,
    val deltaE: Float,
)

data class ColorSearchResult(
    val swatch: SwatchModel,
    val cartName: String,
    val cartCode: String,
    val fabricTypeName: String,
    val roll: WarehouseRollModel?,
    val deltaE: Float,
)

// ---------------------------------------------------------------------------
// B1 & B2 — Multi-Format Exports & Buyer Presentation Session
// ---------------------------------------------------------------------------

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    PNG("png", "image/png"),
    WHATSAPP_PACK("png", "image/png"),
    GIMP_GPL("gpl", "text/plain"),
    PROCREATE_SWATCHES("swatches", "application/zip"),
    CSV("csv", "text/csv"),
    HTML_LOOKBOOK("html", "text/html"),
}

data class ExportDocumentResult(
    val filePath: String,
    val format: ExportFormat,
    val shareCaption: String,
    val sha256Hex: String,
)

data class BuyerSessionState(
    val isBuyerMode: Boolean = false,
    val companyName: String = "Al AzharTex Partner",
    val favoritedSwatchIds: Set<String> = emptySet(),
    val pinConfigured: Boolean = false,
)

// ---------------------------------------------------------------------------
// B3 — 2.5D Drape Visualization
// ---------------------------------------------------------------------------

enum class DrapeScene(val label: String, val labelAr: String, val assetPath: String) {
    CURTAIN_FOLD("Curtain", "ستائر", "drape/curtain_fold_gray.webp"),
    SHIRT_DRAPE("Shirt", "قميص", "drape/shirt_drape_gray.webp"),
    UPHOLSTERY("Upholstery", "تنجيد", "drape/upholstery_gray.webp"),
}

enum class DrapeLightingMode(val label: String) {
    DAYLIGHT_6500K("6500K D65"),
    STORE_LED_4000K("4000K LED"),
    INCANDESCENT_2800K("2800K Warm"),
}

// ---------------------------------------------------------------------------
// C1 — QR / Barcode / NFC Deep-Link Targets
// ---------------------------------------------------------------------------

sealed interface ScannedBarcodeTarget {
    data class CartDeepLink(val cartId: String) : ScannedBarcodeTarget
    data class RollCode(val rollCode: String) : ScannedBarcodeTarget
    data class RawText(val value: String) : ScannedBarcodeTarget
}

// ---------------------------------------------------------------------------
// C2 — Dye-Lot Tracking & Shade Consistency ("ΔE Guard")
// ---------------------------------------------------------------------------

data class DyeLotModel(
    val id: String,
    val rollId: String,
    val lotCode: String,
    val dyeDate: Long,
    val masterDeltaE: Double = -1.0,
    val operatorName: String = "Dye Master",
    val notes: String = "",
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

data class LotDeltaE(
    val lot: DyeLotModel,
    val rollCode: String,
    val deltaE: Float,
)

data class MixedLotWarning(
    val swatchId: String,
    val swatchNumber: String,
    val lotCodeA: String,
    val lotCodeB: String,
    val deltaEDiff: Float,
)

data class ShadeConsistencyReport(
    val cartId: String,
    val worstDeltaE: Float,
    val mixedLotWarnings: List<MixedLotWarning>,
    val perShadeVariance: Map<String, List<LotDeltaE>>,
)

// ---------------------------------------------------------------------------
// C3 & C5c — Reservations, Cut Log & Pricing Tiers
// ---------------------------------------------------------------------------

data class ReservationModel(
    val id: String,
    val rollId: String,
    val swatchId: String,
    val metersReserved: Double,
    val reservedBy: String,
    val note: String = "",
    val status: ReservationStatusModel = ReservationStatusModel.ACTIVE,
    val createdAt: Long,
    val expiresAt: Long,
)

data class CutLogModel(
    val id: String,
    val rollId: String,
    val swatchId: String,
    val metersCut: Double,
    val pieceCount: Int,
    val operatorName: String,
    val createdAt: Long,
)

data class PriceTierModel(
    val id: String,
    val fabricTypeId: String,
    val tierName: String,
    val minMeters: Double,
    val pricePerMeter: Double,
    val currencyCode: String = "EGP",
    val updatedAt: Long = 0L,
)

// ---------------------------------------------------------------------------
// D3, D4, & E3 — Locale, Glove Mode, Roles & Backend Readiness Contracts
// ---------------------------------------------------------------------------

enum class AppLanguageSetting(val tag: String, val displayName: String) {
    SYSTEM("", "System Default"),
    ENGLISH("en", "English"),
    ARABIC_EG("ar-EG", "العربية (مصر)"),
}

enum class AppRole(val displayName: String) {
    MANAGER("Manager"),
    ADMIN("Admin"),
    MILL_OPERATOR("Mill Operator"),
    WAREHOUSE("Warehouse"),
    SALES_REP("Sales Rep"),
    BUYER("Buyer"),
}

object RoleGate {
    fun canEditCarts(role: AppRole): Boolean =
        role == AppRole.MANAGER || role == AppRole.ADMIN || role == AppRole.MILL_OPERATOR

    fun canReserveOrCut(role: AppRole): Boolean =
        role == AppRole.MANAGER || role == AppRole.ADMIN || role == AppRole.MILL_OPERATOR || role == AppRole.WAREHOUSE

    fun canViewCosts(role: AppRole): Boolean =
        role == AppRole.MANAGER || role == AppRole.ADMIN

    fun canSyncManually(role: AppRole): Boolean =
        role != AppRole.BUYER && role != AppRole.SALES_REP

    fun canExport(role: AppRole): Boolean =
        role != AppRole.WAREHOUSE
}

data class AppSessionPreferences(
    val role: AppRole = AppRole.MANAGER,
    val language: AppLanguageSetting = AppLanguageSetting.SYSTEM,
    val gloveMode: Boolean = false,
    val lastOperatorName: String = "Mill Operator",
    val lastBackupTimestampMillis: Long? = null,
    val lastBackupSizeBytes: Long? = null,
    val deltaETolerance: Float = 2.0f,
) {
    fun isBackupReminderDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = lastBackupTimestampMillis ?: return true
        val sevenDays = 7L * 24L * 60L * 60L * 1000L
        return (nowMillis - last) > sevenDays
    }
}

// ---------------------------------------------------------------------------
// E1 & E2 — Natural Language Query & Mill Insights
// ---------------------------------------------------------------------------

enum class AskAzPalleteSource {
    GEMINI_NANO,
    OFFLINE_TEMPLATE,
}

data class AskInventoryItem(
    val swatchId: String,
    val swatchNumber: String,
    val colorName: String,
    val colorHex: String,
    val fabricName: String,
    val rollCode: String?,
    val warehouseShed: String?,
    val availableMeters: Double?,
    val deltaE: Float?,
)

data class AskAzPalleteResult(
    val query: String,
    val source: AskAzPalleteSource,
    val summary: String,
    val items: List<AskInventoryItem>,
)

data class SlowMovingShadeMetric(
    val swatch: SwatchModel,
    val daysInStock: Long,
    val metersRemaining: Double,
)

data class FastMovingShadeMetric(
    val swatch: SwatchModel,
    val reservationsCount: Int,
    val cutCount: Int,
)

data class FavoritedShadeMetric(
    val swatch: SwatchModel,
    val favoriteCount: Int,
)

data class LotVarianceSummaryItem(
    val colorFamily: String,
    val worstDeltaE: Float,
    val lotCount: Int,
)

data class MillInsightsReport(
    val totalActiveCarts: Int,
    val lowStockCount: Int,
    val pendingSyncCount: Int,
    val syncHealthScore: Float,
    val stockByFabricType: Map<String, Double>,
    val stockValuePerShed: Map<String, Double>,
    val totalInventoryValue: Double,
    val slowestMovingShades: List<SlowMovingShadeMetric>,
    val fastestMovingShades: List<FastMovingShadeMetric>,
    val mostFavoritedShades: List<FavoritedShadeMetric>,
    val lotVarianceSummary: List<LotVarianceSummaryItem>,
)

// ---------------------------------------------------------------------------
// E3 — Remote Backend Readiness Delta Packets
// ---------------------------------------------------------------------------

data class RemoteSyncPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val cart: ShadeCartModel? = null,
    val lots: List<DyeLotModel> = emptyList(),
    val reservations: List<ReservationModel> = emptyList(),
    val cutLogs: List<CutLogModel> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis(),
)

sealed interface RemoteSyncPacketResult {
    data class Accepted(val remoteVersion: Long) : RemoteSyncPacketResult
    data class Conflict(val remoteVersion: Long) : RemoteSyncPacketResult
    data class TransientFailure(val message: String) : RemoteSyncPacketResult
}
