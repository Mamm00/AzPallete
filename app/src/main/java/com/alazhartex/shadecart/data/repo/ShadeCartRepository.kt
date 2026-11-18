package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.ShadeCartEntity
import com.alazhartex.shadecart.data.local.SwatchEntity
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import com.alazhartex.shadecart.data.local.SyncStatus
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.SyncPushResult
import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.ConflictOrigin
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface SyncWorkScheduler {
    fun enqueueSync()
}

/**
 * Offline-first repository implementing [ShadeCartRepositoryContract] over [ShadeCartStore]
 * (Room) and [ShadeCartSyncAdapter] (Retrofit + OkHttp + offline stub fallback).
 */
class ShadeCartRepository(
    private val store: ShadeCartStore,
    private val syncAdapter: ShadeCartSyncAdapter,
    private val syncScheduler: SyncWorkScheduler,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ShadeCartRepositoryContract {

    private val writeMutex = Mutex()

    override fun observeCarts(): Flow<List<ShadeCartModel>> =
        store.observeCartsWithDetails().map { carts ->
            carts.map { it.toDomain() }
        }

    override fun observeCart(cartId: String): Flow<ShadeCartModel?> =
        store.observeCartWithDetails(cartId).map { it?.toDomain() }

    override fun observeFabricTypes(): Flow<List<FabricTypeModel>> =
        store.observeFabricTypes().map { list -> list.map { it.toDomain() } }

    override fun observeRolls(fabricTypeId: String?): Flow<List<WarehouseRollModel>> =
        (if (fabricTypeId != null) store.observeRollsForFabric(fabricTypeId) else store.observeRolls())
            .map { list -> list.map { it.toDomain() } }

    override fun observeSyncQueue(): Flow<List<SyncQueueEntryModel>> =
        store.observeSyncQueue().map { list -> list.map { it.toDomain() } }

    override fun observeConflicts(): Flow<List<SyncConflictModel>> =
        store.observeConflicts().map { rows ->
            rows.mapNotNull { row -> decodeConflictRow(row)?.toDomain() }
        }

    override suspend fun createOrGetDraftCart(
        draft: ShadeCartCreateInput,
    ): ShadeCartSaveOutcome = withContext(ioDispatcher) {
        writeMutex.withLock {
            store.runInTransaction {
                val now = nowProvider()
                val resolvedFabric = resolveOrCreateFabricType(
                    requestedId = draft.fabricTypeId,
                    name = draft.fabricName,
                    code = draft.fabricCode,
                    weightGsm = draft.weightGsm,
                    widthCm = draft.widthCm,
                    now = now,
                )
                val availableRolls = store.getRollsForFabric(resolvedFabric.id)
                val cartEntity = ShadeCartEntity(
                    id = draft.id,
                    fabricTypeId = resolvedFabric.id,
                    fabricName = draft.fabricName.ifBlank { resolvedFabric.name },
                    fabricCode = draft.fabricCode.ifBlank { resolvedFabric.code },
                    weightGsm = if (draft.weightGsm > 0) draft.weightGsm else resolvedFabric.weightGsm,
                    widthCm = if (draft.widthCm > 0) draft.widthCm else resolvedFabric.widthCm,
                    originalImagePath = draft.originalImagePath,
                    editedImagePath = draft.editedImagePath,
                    editStackJson = draft.editStackJson,
                    headerOcrRaw = draft.headerOcrRaw,
                    calibrationProfileId = draft.calibrationProfileId,
                    ambientLux = draft.ambientLux,
                    qrDeepLink = draft.qrDeepLink.ifBlank { "azpallete://cart/${draft.id}" },
                    isArchived = false,
                    syncStatus = SyncStatus.PENDING,
                    version = 1L,
                    lastSyncedVersion = 0L,
                    updatedAt = now,
                )
                val swatchEntities = draft.swatches.mapIndexed { index, sw ->
                    val matchedRollId = sw.rollId ?: availableRolls.firstOrNull {
                        it.shadeLabel.equals(sw.swatchNumber.trim(), ignoreCase = true)
                    }?.id
                    val (l, a, b) = resolveSwatchLab(sw)
                    SwatchEntity(
                        id = sw.id,
                        shadeCartId = cartEntity.id,
                        fabricTypeId = resolvedFabric.id,
                        rollId = matchedRollId,
                        swatchNumber = sw.swatchNumber,
                        colorHex = sw.colorHex,
                        colorName = sw.colorName,
                        positionOrder = index,
                        bboxLeft = sw.bboxLeft,
                        bboxTop = sw.bboxTop,
                        bboxRight = sw.bboxRight,
                        bboxBottom = sw.bboxBottom,
                        isOverridden = sw.isOverridden,
                        labL = l,
                        labA = a,
                        labB = b,
                        weaveTag = sw.weaveTag.name,
                        sheenScore = sw.sheenScore,
                        pricePerMeter = sw.pricePerMeter,
                        currencyCode = sw.currencyCode,
                        version = 1L,
                        updatedAt = now,
                    )
                }

                store.upsertCart(cartEntity)
                store.replaceSwatches(cartEntity.id, swatchEntities)
                enqueueSnapshotLocked(
                    snapshot = toSnapshot(cartEntity, swatchEntities),
                    baseVersion = 0L,
                    now = now,
                )
                ShadeCartSaveOutcome.Saved(cartId = cartEntity.id, newVersion = cartEntity.version)
            }
        }.also {
            if (it is ShadeCartSaveOutcome.Saved) {
                syncScheduler.enqueueSync()
            }
        }
    }

    override suspend fun saveCartWithOptimisticLock(
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
    ): ShadeCartSaveOutcome = withContext(ioDispatcher) {
        val result = writeMutex.withLock {
            store.runInTransaction {
                val existing = store.getCart(cartId)
                    ?: return@runInTransaction ShadeCartSaveOutcome.NotFound(cartId)
                val existingSwatches = store.getSwatchesForCart(cartId)
                val now = nowProvider()

                val resolvedFabric = resolveOrCreateFabricType(
                    requestedId = fabricTypeId ?: existing.fabricTypeId,
                    name = fabricName,
                    code = fabricCode,
                    weightGsm = weightGsm,
                    widthCm = widthCm,
                    now = now,
                )

                val proposedSnapshot = ShadeCartSnapshot(
                    id = cartId,
                    fabricTypeId = resolvedFabric.id,
                    fabricName = fabricName,
                    fabricCode = fabricCode,
                    weightGsm = weightGsm,
                    widthCm = widthCm,
                    originalImagePath = originalImagePath,
                    editedImagePath = editedImagePath,
                    editStackJson = editStackJson,
                    headerOcrRaw = headerOcrRaw,
                    version = expectedVersion + 1L,
                    swatches = swatches.mapIndexed { idx, sw -> sw.toDraft(resolvedFabric.id, idx) },
                    updatedAt = now,
                    calibrationProfileId = existing.calibrationProfileId,
                    ambientLux = existing.ambientLux,
                    qrDeepLink = existing.qrDeepLink.ifBlank { "azpallete://cart/$cartId" },
                    isArchived = existing.isArchived,
                )

                if (existing.version != expectedVersion) {
                    val currentSnapshot = toSnapshot(existing, existingSwatches)
                    val record = SyncConflictRecord(
                        cartId = cartId,
                        fabricName = existing.fabricName,
                        fabricCode = existing.fabricCode,
                        localSnapshot = proposedSnapshot,
                        remoteSnapshot = currentSnapshot,
                        source = ConflictOrigin.CONCURRENT_LOCAL_WRITE,
                        detectedAt = now,
                    )
                    persistConflictLocked(record, now)
                    return@runInTransaction ShadeCartSaveOutcome.Conflict(record.toDomain())
                }

                val newVersion = existing.version + 1L
                val availableRolls = store.getRollsForFabric(resolvedFabric.id)
                val updatedCart = existing.copy(
                    fabricTypeId = resolvedFabric.id,
                    fabricName = fabricName,
                    fabricCode = fabricCode,
                    weightGsm = weightGsm,
                    widthCm = widthCm,
                    originalImagePath = originalImagePath,
                    editedImagePath = editedImagePath,
                    editStackJson = editStackJson,
                    headerOcrRaw = headerOcrRaw,
                    qrDeepLink = existing.qrDeepLink.ifBlank { "azpallete://cart/$cartId" },
                    syncStatus = SyncStatus.PENDING,
                    version = newVersion,
                    updatedAt = now,
                )
                val existingById = existingSwatches.associateBy { it.id }
                val updatedSwatches = swatches.mapIndexed { idx, sw ->
                    val prev = existingById[sw.id]
                    val nextSwatchVersion = if (prev == null) 1L else prev.version + 1L
                    val matchedRollId = sw.rollId ?: availableRolls.firstOrNull {
                        it.shadeLabel.equals(sw.swatchNumber.trim(), ignoreCase = true)
                    }?.id
                    val (l, a, b) = resolveSwatchLab(sw)
                    SwatchEntity(
                        id = sw.id,
                        shadeCartId = cartId,
                        fabricTypeId = resolvedFabric.id,
                        rollId = matchedRollId,
                        swatchNumber = sw.swatchNumber,
                        colorHex = sw.colorHex,
                        colorName = sw.colorName,
                        positionOrder = idx,
                        bboxLeft = sw.bboxLeft,
                        bboxTop = sw.bboxTop,
                        bboxRight = sw.bboxRight,
                        bboxBottom = sw.bboxBottom,
                        isOverridden = sw.isOverridden,
                        labL = l,
                        labA = a,
                        labB = b,
                        weaveTag = sw.weaveTag.name,
                        sheenScore = sw.sheenScore,
                        pricePerMeter = sw.pricePerMeter ?: prev?.pricePerMeter,
                        currencyCode = sw.currencyCode,
                        version = nextSwatchVersion,
                        updatedAt = now,
                    )
                }

                store.upsertCart(updatedCart)
                store.replaceSwatches(cartId, updatedSwatches)
                store.deleteConflictSyncItems(cartId)
                enqueueSnapshotLocked(
                    snapshot = toSnapshot(updatedCart, updatedSwatches),
                    baseVersion = existing.lastSyncedVersion,
                    now = now,
                )
                ShadeCartSaveOutcome.Saved(cartId = cartId, newVersion = newVersion)
            }
        }
        if (result is ShadeCartSaveOutcome.Saved) {
            syncScheduler.enqueueSync()
        }
        result
    }

    override suspend fun updateSwatchesWithOptimisticLock(
        cartId: String,
        expectedCartVersion: Long,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome = withContext(ioDispatcher) {
        val current = store.getCart(cartId) ?: return@withContext ShadeCartSaveOutcome.NotFound(cartId)
        saveCartWithOptimisticLock(
            cartId = cartId,
            expectedVersion = expectedCartVersion,
            fabricTypeId = current.fabricTypeId,
            fabricName = current.fabricName,
            fabricCode = current.fabricCode,
            weightGsm = current.weightGsm,
            widthCm = current.widthCm,
            originalImagePath = current.originalImagePath,
            editedImagePath = current.editedImagePath,
            editStackJson = current.editStackJson,
            headerOcrRaw = current.headerOcrRaw,
            swatches = swatches,
        )
    }

    override suspend fun syncPendingQueue(): SyncBatchSummary = withContext(ioDispatcher) {
        val pending = store.getPendingSyncItems()
        var syncedCount = 0
        var conflictCount = 0
        var failedCount = 0

        for (item in pending) {
            val localSnapshot = runCatching {
                json.decodeFromString<ShadeCartSnapshot>(item.payloadJson)
            }.getOrNull() ?: continue

            when (val push = syncAdapter.pushCart(localSnapshot, item.baseVersion)) {
                is SyncPushResult.Accepted -> {
                    writeMutex.withLock {
                        store.runInTransaction {
                            val now = nowProvider()
                            val current = store.getCart(item.entityId)
                            if (current != null && current.version <= localSnapshot.version) {
                                store.upsertCart(
                                    current.copy(
                                        syncStatus = SyncStatus.SYNCED,
                                        lastSyncedVersion = push.newRemoteVersion,
                                        updatedAt = now,
                                    ),
                                )
                            }
                            store.upsertSyncQueueItem(
                                item.copy(
                                    status = SyncStatus.SYNCED,
                                    attemptCount = item.attemptCount + 1,
                                    lastError = null,
                                    updatedAt = now,
                                ),
                            )
                        }
                    }
                    syncedCount++
                }

                is SyncPushResult.VersionConflict -> {
                    writeMutex.withLock {
                        store.runInTransaction {
                            val now = nowProvider()
                            val current = store.getCart(item.entityId)
                            if (current != null) {
                                store.upsertCart(
                                    current.copy(
                                        syncStatus = SyncStatus.CONFLICT,
                                        updatedAt = now,
                                    ),
                                )
                            }
                            store.upsertSyncQueueItem(
                                item.copy(
                                    status = SyncStatus.CONFLICT,
                                    conflictRemoteJson = json.encodeToString(push.remoteSnapshot),
                                    conflictRemoteVersion = push.remoteSnapshot.version,
                                    attemptCount = item.attemptCount + 1,
                                    lastError = "Version conflict (local v${localSnapshot.version} vs remote v${push.remoteSnapshot.version})",
                                    updatedAt = now,
                                ),
                            )
                        }
                    }
                    conflictCount++
                }

                is SyncPushResult.TransientFailure -> {
                    writeMutex.withLock {
                        store.runInTransaction {
                            store.upsertSyncQueueItem(
                                item.copy(
                                    status = SyncStatus.PENDING,
                                    attemptCount = item.attemptCount + 1,
                                    lastError = push.message,
                                    updatedAt = nowProvider(),
                                ),
                            )
                        }
                    }
                    failedCount++
                }
            }
        }

        SyncBatchSummary(
            processed = pending.size,
            synced = syncedCount,
            conflicts = conflictCount,
            failed = failedCount,
        )
    }

    override suspend fun resolveConflict(
        cartId: String,
        decision: ConflictDecision,
    ): ShadeCartSaveOutcome = withContext(ioDispatcher) {
        val result = writeMutex.withLock {
            store.runInTransaction {
                val conflictRow = store.getConflictForEntity(cartId)
                    ?: return@runInTransaction ShadeCartSaveOutcome.NotFound(cartId)
                val conflict = decodeConflictRow(conflictRow)
                    ?: return@runInTransaction ShadeCartSaveOutcome.NotFound(cartId)
                val now = nowProvider()

                val chosenSnapshot = when (decision) {
                    ConflictDecision.KEEP_LOCAL -> {
                        val bumpedVersion = maxOf(
                            conflict.localSnapshot.version,
                            conflict.remoteSnapshot.version,
                        ) + 1L
                        conflict.localSnapshot.copy(version = bumpedVersion, updatedAt = now)
                    }

                    ConflictDecision.KEEP_REMOTE -> {
                        conflict.remoteSnapshot.copy(updatedAt = now)
                    }
                }

                val fabric = resolveOrCreateFabricType(
                    requestedId = chosenSnapshot.fabricTypeId,
                    name = chosenSnapshot.fabricName,
                    code = chosenSnapshot.fabricCode,
                    weightGsm = chosenSnapshot.weightGsm,
                    widthCm = chosenSnapshot.widthCm,
                    now = now,
                )
                val syncStatus = if (decision == ConflictDecision.KEEP_LOCAL) {
                    SyncStatus.PENDING
                } else {
                    SyncStatus.SYNCED
                }
                val lastSyncedVersion = if (decision == ConflictDecision.KEEP_REMOTE) {
                    chosenSnapshot.version
                } else {
                    conflict.remoteSnapshot.version
                }

                val updatedCart = ShadeCartEntity(
                    id = cartId,
                    fabricTypeId = fabric.id,
                    fabricName = chosenSnapshot.fabricName,
                    fabricCode = chosenSnapshot.fabricCode,
                    weightGsm = chosenSnapshot.weightGsm,
                    widthCm = chosenSnapshot.widthCm,
                    originalImagePath = chosenSnapshot.originalImagePath,
                    editedImagePath = chosenSnapshot.editedImagePath,
                    editStackJson = chosenSnapshot.editStackJson,
                    headerOcrRaw = chosenSnapshot.headerOcrRaw,
                    calibrationProfileId = chosenSnapshot.calibrationProfileId,
                    ambientLux = chosenSnapshot.ambientLux,
                    qrDeepLink = chosenSnapshot.qrDeepLink.ifBlank { "azpallete://cart/$cartId" },
                    isArchived = chosenSnapshot.isArchived,
                    syncStatus = syncStatus,
                    version = chosenSnapshot.version,
                    lastSyncedVersion = lastSyncedVersion,
                    updatedAt = now,
                )

                val availableRolls = store.getRollsForFabric(fabric.id)
                val updatedSwatches = chosenSnapshot.swatches.mapIndexed { idx, sw ->
                    val matchedRollId = sw.rollId ?: availableRolls.firstOrNull {
                        it.shadeLabel.equals(sw.swatchNumber.trim(), ignoreCase = true)
                    }?.id
                    val (l, a, b) = if (sw.labL != 0.0 || sw.labA != 0.0 || sw.labB != 0.0) {
                        Triple(sw.labL, sw.labA, sw.labB)
                    } else {
                        val computed = ColorScience.hexToLab(sw.colorHex)
                        if (computed != null) {
                            Triple(computed.first.toDouble(), computed.second.toDouble(), computed.third.toDouble())
                        } else {
                            Triple(0.0, 0.0, 0.0)
                        }
                    }
                    SwatchEntity(
                        id = sw.id,
                        shadeCartId = cartId,
                        fabricTypeId = fabric.id,
                        rollId = matchedRollId,
                        swatchNumber = sw.swatchNumber,
                        colorHex = sw.colorHex,
                        colorName = sw.colorName,
                        positionOrder = idx,
                        bboxLeft = sw.bboxLeft,
                        bboxTop = sw.bboxTop,
                        bboxRight = sw.bboxRight,
                        bboxBottom = sw.bboxBottom,
                        isOverridden = sw.isOverridden,
                        labL = l,
                        labA = a,
                        labB = b,
                        weaveTag = sw.weaveTag,
                        sheenScore = sw.sheenScore,
                        pricePerMeter = sw.pricePerMeter,
                        currencyCode = sw.currencyCode,
                        version = maxOf(1L, sw.version),
                        updatedAt = now,
                    )
                }

                store.upsertCart(updatedCart)
                store.replaceSwatches(cartId, updatedSwatches)
                store.deleteConflictSyncItems(cartId)

                if (decision == ConflictDecision.KEEP_LOCAL) {
                    enqueueSnapshotLocked(
                        snapshot = toSnapshot(updatedCart, updatedSwatches),
                        baseVersion = lastSyncedVersion,
                        now = now,
                    )
                } else {
                    store.deletePendingSyncItems(cartId)
                }

                ShadeCartSaveOutcome.Saved(cartId = cartId, newVersion = updatedCart.version)
            }
        }
        if (result is ShadeCartSaveOutcome.Saved && decision == ConflictDecision.KEEP_LOCAL) {
            syncScheduler.enqueueSync()
        }
        result
    }

    override suspend fun deleteCart(cartId: String) = withContext(ioDispatcher) {
        writeMutex.withLock {
            store.runInTransaction {
                store.deleteConflictSyncItems(cartId)
                store.deletePendingSyncItems(cartId)
                store.deleteCart(cartId)
            }
        }
    }

    private fun resolveSwatchLab(sw: SwatchInput): Triple<Double, Double, Double> {
        if (sw.labL != 0.0 || sw.labA != 0.0 || sw.labB != 0.0) {
            return Triple(sw.labL, sw.labA, sw.labB)
        }
        val computed = ColorScience.hexToLab(sw.colorHex)
        return if (computed != null) {
            Triple(computed.first.toDouble(), computed.second.toDouble(), computed.third.toDouble())
        } else {
            Triple(0.0, 0.0, 0.0)
        }
    }

    private suspend fun resolveOrCreateFabricType(
        requestedId: String?,
        name: String,
        code: String,
        weightGsm: Int,
        widthCm: Int,
        now: Long,
    ): FabricType {
        if (!requestedId.isNullOrBlank()) {
            store.findFabricTypeById(requestedId)?.let { return it }
        }
        if (code.isNotBlank()) {
            store.findFabricTypeByCode(code)?.let { return it }
        }
        val cleanCode = code.ifBlank {
            "FAB-${UUID.randomUUID().toString().take(6).uppercase()}"
        }
        val cleanName = name.ifBlank { "Custom Fabric" }
        val newFabric = FabricType(
            id = requestedId ?: "fab-${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            code = cleanCode,
            weightGsm = weightGsm.coerceAtLeast(1),
            widthCm = widthCm.coerceAtLeast(1),
            version = 1L,
            updatedAt = now,
        )
        store.upsertFabricType(newFabric)
        return newFabric
    }

    private suspend fun enqueueSnapshotLocked(
        snapshot: ShadeCartSnapshot,
        baseVersion: Long,
        now: Long,
    ) {
        store.deletePendingSyncItems(snapshot.id)
        store.upsertSyncQueueItem(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                entityType = "SHADE_CART",
                entityId = snapshot.id,
                operation = "UPSERT",
                payloadJson = json.encodeToString(snapshot),
                baseVersion = baseVersion,
                status = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun persistConflictLocked(
        conflict: SyncConflictRecord,
        now: Long,
    ) {
        store.deleteConflictSyncItems(conflict.cartId)
        val existingCart = store.getCart(conflict.cartId)
        if (existingCart != null) {
            store.upsertCart(existingCart.copy(syncStatus = SyncStatus.CONFLICT, updatedAt = now))
        }
        store.upsertSyncQueueItem(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                entityType = "SHADE_CART",
                entityId = conflict.cartId,
                operation = "UPSERT",
                payloadJson = json.encodeToString(conflict.localSnapshot),
                baseVersion = conflict.localSnapshot.version,
                status = SyncStatus.CONFLICT,
                conflictRemoteJson = json.encodeToString(conflict.remoteSnapshot),
                conflictRemoteVersion = conflict.remoteSnapshot.version,
                lastError = "Stale write detected (${conflict.source.name})",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun decodeConflictRow(row: SyncQueueEntity): SyncConflictRecord? {
        val local = runCatching { json.decodeFromString<ShadeCartSnapshot>(row.payloadJson) }.getOrNull()
            ?: return null
        val remoteJson = row.conflictRemoteJson ?: return null
        val remote = runCatching { json.decodeFromString<ShadeCartSnapshot>(remoteJson) }.getOrNull()
            ?: return null
        val source = if (row.lastError?.contains(ConflictOrigin.CONCURRENT_LOCAL_WRITE.name) == true) {
            ConflictOrigin.CONCURRENT_LOCAL_WRITE
        } else {
            ConflictOrigin.REMOTE_SYNC_VERSION_MISMATCH
        }
        return SyncConflictRecord(
            cartId = row.entityId,
            fabricName = local.fabricName,
            fabricCode = local.fabricCode,
            localSnapshot = local,
            remoteSnapshot = remote,
            source = source,
            detectedAt = row.updatedAt,
        )
    }

    private fun toSnapshot(
        cart: ShadeCartEntity,
        swatches: List<SwatchEntity>,
    ): ShadeCartSnapshot = ShadeCartSnapshot(
        id = cart.id,
        fabricTypeId = cart.fabricTypeId,
        fabricName = cart.fabricName,
        fabricCode = cart.fabricCode,
        weightGsm = cart.weightGsm,
        widthCm = cart.widthCm,
        originalImagePath = cart.originalImagePath,
        editedImagePath = cart.editedImagePath,
        editStackJson = cart.editStackJson,
        headerOcrRaw = cart.headerOcrRaw,
        version = cart.version,
        calibrationProfileId = cart.calibrationProfileId,
        ambientLux = cart.ambientLux,
        qrDeepLink = cart.qrDeepLink,
        isArchived = cart.isArchived,
        swatches = swatches.sortedBy { it.positionOrder }.map { sw ->
            SwatchDraft(
                id = sw.id,
                fabricTypeId = sw.fabricTypeId,
                rollId = sw.rollId,
                swatchNumber = sw.swatchNumber,
                colorHex = sw.colorHex,
                colorName = sw.colorName,
                positionOrder = sw.positionOrder,
                bboxLeft = sw.bboxLeft,
                bboxTop = sw.bboxTop,
                bboxRight = sw.bboxRight,
                bboxBottom = sw.bboxBottom,
                isOverridden = sw.isOverridden,
                labL = sw.labL,
                labA = sw.labA,
                labB = sw.labB,
                weaveTag = sw.weaveTag,
                sheenScore = sw.sheenScore,
                pricePerMeter = sw.pricePerMeter,
                currencyCode = sw.currencyCode,
                version = sw.version,
            )
        },
        updatedAt = cart.updatedAt,
    )
}
