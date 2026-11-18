package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.data.local.CutLogEntity
import com.alazhartex.shadecart.data.local.DyeLotEntity
import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.PriceTierEntity
import com.alazhartex.shadecart.data.local.ReservationEntity
import com.alazhartex.shadecart.data.local.ReservationStatus
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.RollStatus
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchWithRoll
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import com.alazhartex.shadecart.data.local.SyncStatus
import com.alazhartex.shadecart.domain.model.ConflictOrigin
import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.PriceTierModel
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.ReservationStatusModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.model.WeaveTag

internal fun String.toSyncState(): SyncState = when (this) {
    SyncStatus.SYNCED -> SyncState.SYNCED
    SyncStatus.CONFLICT -> SyncState.CONFLICT
    SyncStatus.FAILED -> SyncState.FAILED
    else -> SyncState.PENDING
}

internal fun String.toRollStockStatus(): RollStockStatus = when (this) {
    RollStatus.LOW_STOCK -> RollStockStatus.LOW_STOCK
    RollStatus.RESERVED -> RollStockStatus.RESERVED
    RollStatus.OUT_OF_STOCK -> RollStockStatus.OUT_OF_STOCK
    else -> RollStockStatus.IN_STOCK
}

internal fun String.toWeaveTag(): WeaveTag =
    runCatching { WeaveTag.valueOf(this) }.getOrElse { WeaveTag.MATTE }

internal fun String.toReservationStatusModel(): ReservationStatusModel = when (this) {
    ReservationStatus.RELEASED -> ReservationStatusModel.RELEASED
    ReservationStatus.FULFILLED -> ReservationStatusModel.FULFILLED
    else -> ReservationStatusModel.ACTIVE
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
    availableMeters = availableMeters,
    lotCode = lotCode,
    lowStockThresholdMeters = lowStockThresholdMeters,
    qrPayload = qrPayload,
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
    labL = swatch.labL,
    labA = swatch.labA,
    labB = swatch.labB,
    weaveTag = swatch.weaveTag.toWeaveTag(),
    sheenScore = swatch.sheenScore,
    pricePerMeter = swatch.pricePerMeter,
    currencyCode = swatch.currencyCode,
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
    calibrationProfileId = cart.calibrationProfileId,
    ambientLux = cart.ambientLux,
    qrDeepLink = cart.qrDeepLink,
    isArchived = cart.isArchived,
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

internal fun DyeLotEntity.toDomain(): DyeLotModel = DyeLotModel(
    id = id,
    rollId = rollId,
    lotCode = lotCode,
    dyeDate = dyeDate,
    masterDeltaE = masterDeltaE,
    operatorName = operatorName,
    notes = notes,
    version = version,
    updatedAt = updatedAt,
)

internal fun ReservationEntity.toDomain(): ReservationModel = ReservationModel(
    id = id,
    rollId = rollId,
    swatchId = swatchId,
    metersReserved = metersReserved,
    reservedBy = reservedBy,
    note = note,
    status = status.toReservationStatusModel(),
    createdAt = createdAt,
    expiresAt = expiresAt,
)

internal fun CutLogEntity.toDomain(): CutLogModel = CutLogModel(
    id = id,
    rollId = rollId,
    swatchId = swatchId,
    metersCut = metersCut,
    pieceCount = pieceCount,
    operatorName = operatorName,
    createdAt = createdAt,
)

internal fun PriceTierEntity.toDomain(): PriceTierModel = PriceTierModel(
    id = id,
    fabricTypeId = fabricTypeId,
    tierName = tierName,
    minMeters = minMeters,
    pricePerMeter = pricePerMeter,
    currencyCode = currencyCode,
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
        labL = labL,
        labA = labA,
        labB = labB,
        weaveTag = weaveTag.name,
        sheenScore = sheenScore,
        pricePerMeter = pricePerMeter,
        currencyCode = currencyCode,
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
