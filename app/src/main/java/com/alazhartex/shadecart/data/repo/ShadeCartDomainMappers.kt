package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.RollStatus
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchWithRoll
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import com.alazhartex.shadecart.data.local.SyncStatus
import com.alazhartex.shadecart.domain.model.ConflictOrigin
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.WarehouseRollModel

/**
 * Data-to-domain mappers enforcing strict Clean Architecture separation between Room persistence
 * entities (`data/local`) and pure Kotlin domain models (`domain/model`)
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1 & Chapter 10.3).
 */

internal fun String.toSyncState(): SyncState = when (this) {
    SyncStatus.SYNCED -> SyncState.SYNCED
    SyncStatus.CONFLICT -> SyncState.CONFLICT
    SyncStatus.FAILED -> SyncState.FAILED
    else -> SyncState.PENDING
}

internal fun String.toRollStockStatus(): RollStockStatus = when (this) {
    RollStatus.LOW_STOCK -> RollStockStatus.LOW_STOCK
    RollStatus.RESERVED -> RollStockStatus.RESERVED
    else -> RollStockStatus.IN_STOCK
}

internal fun FabricType.toDomain(): FabricTypeModel = FabricTypeModel(
    id = id,
    name = name,
    code = code,
    weightGsm = weightGsm,
    widthCm = widthCm,
    version = version,
    updatedAt = updatedAt,
)

internal fun Roll.toDomain(): WarehouseRollModel = WarehouseRollModel(
    id = id,
    rollCode = rollCode,
    fabricTypeId = fabricTypeId,
    shadeLabel = shadeLabel,
    colorName = colorName,
    colorHex = colorHex,
    warehouseShed = warehouseShed,
    lengthMeters = lengthMeters,
    status = status.toRollStockStatus(),
    version = version,
    updatedAt = updatedAt,
)

internal fun SwatchWithRoll.toDomain(): SwatchModel = SwatchModel(
    id = swatch.id,
    shadeCartId = swatch.shadeCartId,
    fabricTypeId = swatch.fabricTypeId,
    rollId = swatch.rollId,
    linkedRoll = roll?.toDomain(),
    swatchNumber = swatch.swatchNumber,
    colorHex = swatch.colorHex,
    colorName = swatch.colorName,
    positionOrder = swatch.positionOrder,
    bboxLeft = swatch.bboxLeft,
    bboxTop = swatch.bboxTop,
    bboxRight = swatch.bboxRight,
    bboxBottom = swatch.bboxBottom,
    isOverridden = swatch.isOverridden,
    version = swatch.version,
    updatedAt = swatch.updatedAt,
)

internal fun ShadeCartWithDetails.toDomain(): ShadeCartModel = ShadeCartModel(
    id = cart.id,
    fabricTypeId = cart.fabricTypeId,
    fabricType = fabricType?.toDomain(),
    fabricName = cart.fabricName,
    fabricCode = cart.fabricCode,
    weightGsm = cart.weightGsm,
    widthCm = cart.widthCm,
    originalImagePath = cart.originalImagePath,
    editedImagePath = cart.editedImagePath,
    editStackJson = cart.editStackJson,
    headerOcrRaw = cart.headerOcrRaw,
    syncStatus = cart.syncStatus.toSyncState(),
    version = cart.version,
    lastSyncedVersion = cart.lastSyncedVersion,
    updatedAt = cart.updatedAt,
    swatches = swatches.sortedBy { it.swatch.positionOrder }.map { it.toDomain() },
)

internal fun SyncQueueEntity.toDomain(): SyncQueueEntryModel = SyncQueueEntryModel(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    status = status.toSyncState(),
    attemptCount = attemptCount,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SwatchInput.toDraft(resolvedFabricTypeId: String, positionIndex: Int): SwatchDraft =
    SwatchDraft(
        id = id,
        fabricTypeId = fabricTypeId.ifBlank { resolvedFabricTypeId },
        rollId = rollId,
        swatchNumber = swatchNumber,
        colorHex = colorHex,
        colorName = colorName,
        positionOrder = positionIndex,
        bboxLeft = bboxLeft,
        bboxTop = bboxTop,
        bboxRight = bboxRight,
        bboxBottom = bboxBottom,
        isOverridden = isOverridden,
        version = version,
    )

internal fun SyncConflictRecord.toDomain(): SyncConflictModel = SyncConflictModel(
    cartId = cartId,
    fabricName = fabricName,
    fabricCode = fabricCode,
    localVersion = localSnapshot.version,
    remoteVersion = remoteSnapshot.version,
    localSwatchCount = localSnapshot.swatches.size,
    remoteSwatchCount = remoteSnapshot.swatches.size,
    source = when (source) {
        ConflictOrigin.CONCURRENT_LOCAL_WRITE -> ConflictOrigin.CONCURRENT_LOCAL_WRITE
        ConflictOrigin.REMOTE_SYNC_VERSION_MISMATCH -> ConflictOrigin.REMOTE_SYNC_VERSION_MISMATCH
    },
    detectedAt = detectedAt,
)
