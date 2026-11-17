package com.alazhartex.shadecart.domain.repository

import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SyncBatchSummary
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository boundary for reading and mutating Digital Shade Cart data, fabric specifications,
 * warehouse rolls, and offline/remote sync queues.
 *
 * Follows Dependency Inversion in Clean Architecture so `domain/usecase` and `ui/` depend solely
 * on pure Kotlin abstractions rather than Room or Retrofit implementations
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 1.2 & Chapter 10.1).
 */
interface ShadeCartRepositoryContract {

    /** Streams all saved shade carts with ordered swatches and linked roll metadata. */
    fun observeCarts(): Flow<List<ShadeCartModel>>

    /** Streams a single shade cart by [cartId], or `null` when absent. */
    fun observeCart(cartId: String): Flow<ShadeCartModel?>

    /** Streams all catalogued fabric types sorted alphabetically. */
    fun observeFabricTypes(): Flow<List<FabricTypeModel>>

    /** Streams warehouse rolls, optionally filtered to [fabricTypeId]. */
    fun observeRolls(fabricTypeId: String? = null): Flow<List<WarehouseRollModel>>

    /** Streams queued offline synchronization records. */
    fun observeSyncQueue(): Flow<List<SyncQueueEntryModel>>

    /** Streams unresolved synchronization or concurrent-write conflicts. */
    fun observeConflicts(): Flow<List<SyncConflictModel>>

    /** Creates or replaces an initial draft cart and schedules background sync. */
    suspend fun createOrGetDraftCart(draft: ShadeCartCreateInput): ShadeCartSaveOutcome

    /** Updates a shade cart while enforcing optimistic locking on [expectedVersion]. */
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

    /** Updates only the swatch list of [cartId] while preserving header fields and optimistic locking. */
    suspend fun updateSwatchesWithOptimisticLock(
        cartId: String,
        expectedCartVersion: Long,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome

    /** Resolves an active conflict on [cartId] by keeping local changes or accepting remote state. */
    suspend fun resolveConflict(
        cartId: String,
        decision: ConflictDecision,
    ): ShadeCartSaveOutcome

    /** Executes pending sync queue items through the configured remote sync adapter. */
    suspend fun syncPendingQueue(): SyncBatchSummary

    /** Deletes [cartId] and its associated pending/conflicted sync items. */
    suspend fun deleteCart(cartId: String)
}

/**
 * Domain repository contract for persisted user theme & Material You preferences.
 *
 * Isolates Jetpack DataStore details from ViewModels and Theme composables
 * (Pyre, Chapter 10.1 & Chapter 10.4).
 */
interface ThemePreferencesContract {

    /** Reactive stream of persisted theme appearance settings. */
    val settings: Flow<ThemeSettings>

    /** Persists the selected light/dark/system [ThemeModeSetting]. */
    suspend fun setThemeMode(mode: ThemeModeSetting)

    /** Persists whether Android 12+ Material You dynamic colors should be active. */
    suspend fun setUseDynamicColor(enabled: Boolean)

    /** Persists the fallback brand [AccentPaletteSetting] palette. */
    suspend fun setAccentPalette(palette: AccentPaletteSetting)
}

/**
 * Optional debug-only control surface for testing remote optimistic-locking version conflicts
 * without exposing concrete sync adapter classes to the UI layer (Pyre, Chapter 10.1 & 12.1).
 */
interface SyncDebugController {
    fun armSimulatedConflict()
    fun isConflictArmed(): Boolean
}
