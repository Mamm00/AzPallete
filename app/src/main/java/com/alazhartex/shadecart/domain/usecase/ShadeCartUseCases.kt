package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SyncBatchSummary
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams all saved Digital Shade Carts in reactive domain form.
 *
 * Single-responsibility UseCase following Clean Architecture (`operator fun invoke` convention;
 * Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1).
 */
class ObserveShadeCartsUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Returns a cold [Flow] emitting updated lists of [ShadeCartModel] sorted by recency. */
    operator fun invoke(): Flow<List<ShadeCartModel>> = repository.observeCarts()
}

/**
 * Streams a single Digital Shade Cart identified by `cartId`.
 *
 * (Pyre, Chapter 10.1 — UseCases encapsulate discrete domain operations).
 */
class ObserveShadeCartByIdUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Emits the [ShadeCartModel] matching [cartId], or `null` if deleted/not found. */
    operator fun invoke(cartId: String): Flow<ShadeCartModel?> = repository.observeCart(cartId)
}

/**
 * Streams all available textile fabric catalog definitions.
 *
 * (Pyre, Chapter 10.1).
 */
class ObserveFabricTypesUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Emits ordered [FabricTypeModel] records from local persistence. */
    operator fun invoke(): Flow<List<FabricTypeModel>> = repository.observeFabricTypes()
}

/**
 * Streams warehouse fabric rolls, optionally narrowed to a single fabric type.
 *
 * (Pyre, Chapter 10.1).
 */
class ObserveWarehouseRollsUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Emits [WarehouseRollModel] records filtered by [fabricTypeId] when non-null. */
    operator fun invoke(fabricTypeId: String? = null): Flow<List<WarehouseRollModel>> =
        repository.observeRolls(fabricTypeId)
}

/**
 * Streams unresolved optimistic-locking or remote-sync conflicts requiring operator decision.
 *
 * (Pyre, Chapter 10.1 & Chapter 6.3).
 */
class ObserveSyncConflictsUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Emits active [SyncConflictModel] alerts. */
    operator fun invoke(): Flow<List<SyncConflictModel>> = repository.observeConflicts()
}

/**
 * Streams offline synchronization queue items for status inspection.
 *
 * (Pyre, Chapter 10.1 & Chapter 10.3).
 */
class ObserveSyncQueueUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Emits current [SyncQueueEntryModel] entries ordered by creation timestamp. */
    operator fun invoke(): Flow<List<SyncQueueEntryModel>> = repository.observeSyncQueue()
}

/**
 * Creates a new draft shade cart, persisting it locally and queuing background synchronization.
 *
 * (Pyre, Chapter 10.1 — suspend UseCase executing off the main thread).
 */
class CreateOrGetShadeCartUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Validates and persists [input], returning a [ShadeCartSaveOutcome]. */
    suspend operator fun invoke(input: ShadeCartCreateInput): ShadeCartSaveOutcome =
        repository.createOrGetDraftCart(input)
}

/**
 * Persists full header metadata, non-destructive edit stack JSON, and swatches with optimistic locking.
 *
 * (Pyre, Chapter 10.1 & Chapter 10.3).
 */
class SaveShadeCartUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Saves [cartId] against [expectedVersion], returning [ShadeCartSaveOutcome.Conflict] if stale. */
    suspend operator fun invoke(
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
    ): ShadeCartSaveOutcome = repository.saveCartWithOptimisticLock(
        cartId = cartId,
        expectedVersion = expectedVersion,
        fabricTypeId = fabricTypeId,
        fabricName = fabricName,
        fabricCode = fabricCode,
        weightGsm = weightGsm,
        widthCm = widthCm,
        originalImagePath = originalImagePath,
        editedImagePath = editedImagePath,
        editStackJson = editStackJson,
        headerOcrRaw = headerOcrRaw,
        swatches = swatches,
    )
}

/**
 * Persists edited swatch colors, order, or roll links with optimistic version checks.
 *
 * (Pyre, Chapter 10.1).
 */
class UpdateShadeCartSwatchesUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Applies [swatches] to [cartId] when stored version equals [expectedCartVersion]. */
    suspend operator fun invoke(
        cartId: String,
        expectedCartVersion: Long,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome = repository.updateSwatchesWithOptimisticLock(
        cartId = cartId,
        expectedCartVersion = expectedCartVersion,
        swatches = swatches,
    )
}

/**
 * Applies the operator's conflict-resolution choice (`KEEP_LOCAL` or `KEEP_REMOTE`) to a cart.
 *
 * (Pyre, Chapter 10.1).
 */
class ResolveSyncConflictUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Resolves conflict on [cartId] according to [decision]. */
    suspend operator fun invoke(
        cartId: String,
        decision: ConflictDecision,
    ): ShadeCartSaveOutcome = repository.resolveConflict(cartId, decision)
}

/**
 * Triggers immediate draining of pending sync queue items through the repository sync boundary.
 *
 * (Pyre, Chapter 8.2 & Chapter 10.1).
 */
class TriggerSyncUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    /** Runs [ShadeCartRepositoryContract.syncPendingQueue] and returns a [SyncBatchSummary]. */
    suspend operator fun invoke(): SyncBatchSummary = repository.syncPendingQueue()
}
