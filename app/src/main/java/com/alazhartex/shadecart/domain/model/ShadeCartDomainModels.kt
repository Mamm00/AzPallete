package com.alazhartex.shadecart.domain.model

import java.util.UUID

/**
 * Represents the synchronization lifecycle state of a shade cart or queued sync operation.
 *
 * Pure Kotlin domain enum with no Android, Room, or Retrofit dependencies
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1 — Clean Architecture).
 */
enum class SyncState {
    PENDING,
    SYNCED,
    CONFLICT,
    FAILED,
}

/**
 * Represents the inventory stock availability status of a warehouse fabric roll.
 *
 * Framework-independent domain enum (Pyre, Chapter 10.1).
 */
enum class RollStockStatus {
    IN_STOCK,
    LOW_STOCK,
    RESERVED,
}

/**
 * Pure Kotlin domain model for a textile fabric specification (e.g., Silk 180, Warsaw L).
 *
 * Kept free of Room `@Entity` or Retrofit serialization annotations so business logic remains
 * portable and JVM-testable (Pyre, Chapter 10.1).
 */
data class FabricTypeModel(
    val id: String,
    val name: String,
    val code: String,
    val weightGsm: Int,
    val widthCm: Int,
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

/**
 * Pure Kotlin domain model representing a physical fabric roll stored in an Al AzharTex warehouse shed.
 *
 * Framework-independent model mapped from local/remote data layers (Pyre, Chapter 10.1).
 */
data class WarehouseRollModel(
    val id: String,
    val rollCode: String,
    val fabricTypeId: String,
    val shadeLabel: String,
    val colorName: String,
    val colorHex: String,
    val warehouseShed: String,
    val lengthMeters: Double,
    val status: RollStockStatus = RollStockStatus.IN_STOCK,
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

/**
 * Pure Kotlin domain model for a single color swatch within a shade cart, optionally linked
 * to a [WarehouseRollModel] inventory roll.
 *
 * Decoupled from Room relations so UI and use-case layers remain infrastructure-agnostic
 * (Pyre, Chapter 10.1).
 */
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
    val version: Long = 1L,
    val updatedAt: Long = 0L,
)

/**
 * Pure Kotlin aggregate domain model for a Digital Shade Cart card, pairing header specifications
 * with ordered [SwatchModel] items and optional [FabricTypeModel] reference.
 *
 * Replaces direct exposure of Room `ShadeCartWithDetails` in presentation/ViewModel state
 * (Pyre, Chapter 1.2 & Chapter 10.1).
 */
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
    val syncStatus: SyncState = SyncState.PENDING,
    val version: Long = 1L,
    val lastSyncedVersion: Long = 0L,
    val updatedAt: Long = 0L,
    val swatches: List<SwatchModel> = emptyList(),
)

/**
 * Pure Kotlin representation of a pending, synced, or conflicted item in the offline sync queue.
 *
 * Exposed to presentation via use cases so UI sheets never import Room `@Entity` types
 * (Pyre, Chapter 10.1 & Chapter 10.3).
 */
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

/**
 * Domain classification of how an optimistic-locking version conflict originated.
 *
 * (Pyre, Chapter 10.1 — Clean Architecture domain abstractions).
 */
enum class ConflictOrigin {
    CONCURRENT_LOCAL_WRITE,
    REMOTE_SYNC_VERSION_MISMATCH,
}

/**
 * Domain user choice when resolving an active synchronization or concurrent-write conflict.
 *
 * (Pyre, Chapter 10.1).
 */
enum class ConflictDecision {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

/**
 * Domain summary of an unresolved optimistic-locking or remote-sync conflict on a shade cart.
 *
 * Provides the presentation layer with conflict comparison fields without exposing storage JSON
 * (Pyre, Chapter 10.1).
 */
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

/**
 * Domain input model representing a swatch mutation payload passed to repository/use-case boundaries.
 *
 * (Pyre, Chapter 10.1).
 */
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
    val version: Long = 1L,
)

/**
 * Domain input model for creating or retrieving a new shade cart draft.
 *
 * (Pyre, Chapter 10.1).
 */
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
    val swatches: List<SwatchInput> = emptyList(),
)

/**
 * Sealed outcome for shade-cart write/conflict-resolution operations, enabling exhaustive `when`
 * handling in ViewModels without unchecked exceptions (Pyre, Chapter 8.3 & Chapter 10.1).
 */
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

/**
 * Domain summary reported after draining the offline sync queue against local/remote adapters.
 *
 * (Pyre, Chapter 8.2 & Chapter 10.1).
 */
data class SyncBatchSummary(
    val processed: Int,
    val synced: Int,
    val conflicts: Int,
    val failed: Int,
)

/**
 * Domain theme appearance mode persisted via DataStore Preferences.
 *
 * Decouples UI theme rendering from `data/prefs` implementation (Pyre, Chapter 5.4 & Chapter 10.4).
 */
enum class ThemeModeSetting {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Curated Al AzharTex brand accent palette options for non-dynamic-color or fallback Material 3
 * schemes (Pyre, Chapter 5.2 & Chapter 5.4).
 */
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

/**
 * Pure Kotlin domain representation of user appearance settings (Pyre, Chapter 10.4).
 */
data class ThemeSettings(
    val themeMode: ThemeModeSetting = ThemeModeSetting.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentPalette: AccentPaletteSetting = AccentPaletteSetting.AZHAR_NAVY_CRIMSON,
)

/**
 * Generic sealed asynchronous state wrapper modeling Loading, Success, and Error states
 * for reactive UI flows (Pyre, Chapter 8.3 — Handling Loading and Error States).
 */
sealed interface AsyncResult<out T> {
    data object Loading : AsyncResult<Nothing>
    data class Success<T>(val data: T) : AsyncResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : AsyncResult<Nothing>
}
