package com.alazhartex.shadecart.data.repo

import androidx.room.withTransaction
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.CutLogEntity
import com.alazhartex.shadecart.data.local.DyeLotEntity
import com.alazhartex.shadecart.data.local.PriceTierEntity
import com.alazhartex.shadecart.data.local.ReservationEntity
import com.alazhartex.shadecart.data.local.ReservationStatus
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.RollStatus
import com.alazhartex.shadecart.di.IoDispatcher
import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.PriceTierModel
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed transactional repository for v2.1–v3.0 mill warehouse operations:
 * - Warehouse Roll creation & QR mapping (C1 / C5)
 * - Dye Lots (`lots`, C2)
 * - Live Meterage Reservations (`reservations`, C3)
 * - Cut-piece ledger (`cut_log`, C3)
 * - Volume Pricing Tiers & Swatch Pricing (`price_tiers`, C5c)
 */
@Singleton
class MillInventoryRepository @Inject constructor(
    private val database: AlAzharTexDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MillInventoryRepositoryContract {

    private val rollDao = database.rollDao()
    private val dyeLotDao = database.dyeLotDao()
    private val reservationDao = database.reservationDao()
    private val cutLogDao = database.cutLogDao()
    private val priceTierDao = database.priceTierDao()
    private val swatchDao = database.swatchDao()

    override fun observeAllLots(): Flow<List<DyeLotModel>> =
        dyeLotDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeLotsForRoll(rollId: String): Flow<List<DyeLotModel>> =
        dyeLotDao.observeForRoll(rollId).map { list -> list.map { it.toDomain() } }

    override fun observeActiveReservations(): Flow<List<ReservationModel>> =
        reservationDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllReservations(): Flow<List<ReservationModel>> =
        reservationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCutLogs(): Flow<List<CutLogModel>> =
        cutLogDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePriceTiers(): Flow<List<PriceTierModel>> =
        priceTierDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAllSwatchesWithLab(): Flow<List<SwatchModel>> =
        swatchDao.observeAllWithRoll().map { list -> list.map { it.toDomain() } }

    override suspend fun createRoll(roll: WarehouseRollModel): Result<WarehouseRollModel> =
        withContext(ioDispatcher) {
            runCatching {
                val now = System.currentTimeMillis()
                val entity = Roll(
                    id = roll.id.ifBlank { "roll-${UUID.randomUUID().toString().take(8)}" },
                    rollCode = roll.rollCode.trim(),
                    fabricTypeId = roll.fabricTypeId,
                    shadeLabel = roll.shadeLabel.trim(),
                    colorName = roll.colorName.trim(),
                    colorHex = roll.colorHex.trim(),
                    warehouseShed = roll.warehouseShed,
                    lengthMeters = roll.lengthMeters,
                    availableMeters = roll.availableMeters,
                    lotCode = roll.lotCode.trim(),
                    lowStockThresholdMeters = roll.lowStockThresholdMeters,
                    qrPayload = roll.qrPayload.ifBlank { "azpallete://roll/${roll.rollCode.trim()}" },
                    status = when {
                        roll.availableMeters <= 0.0 -> RollStatus.OUT_OF_STOCK
                        roll.availableMeters < roll.lowStockThresholdMeters -> RollStatus.LOW_STOCK
                        roll.status == RollStockStatus.RESERVED -> RollStatus.RESERVED
                        else -> RollStatus.IN_STOCK
                    },
                    version = 1L,
                    updatedAt = now,
                )
                rollDao.upsert(entity)
                entity.toDomain()
            }
        }

    override suspend fun createDyeLot(lot: DyeLotModel): Result<DyeLotModel> =
        withContext(ioDispatcher) {
            runCatching {
                val now = System.currentTimeMillis()
                val entity = DyeLotEntity(
                    id = lot.id.ifBlank { "lot-${UUID.randomUUID().toString().take(8)}" },
                    rollId = lot.rollId,
                    lotCode = lot.lotCode.trim(),
                    dyeDate = if (lot.dyeDate > 0L) lot.dyeDate else now,
                    masterDeltaE = lot.masterDeltaE,
                    operatorName = lot.operatorName.ifBlank { "Mill Operator" },
                    notes = lot.notes,
                    version = 1L,
                    updatedAt = now,
                )
                dyeLotDao.upsert(entity)
                entity.toDomain()
            }
        }

    override suspend fun reserveMeters(
        rollId: String,
        swatchId: String,
        meters: Double,
        operatorName: String,
        note: String,
    ): Result<ReservationModel> = withContext(ioDispatcher) {
        runCatching {
            require(meters > 0.0) { "Reservation meters must be positive." }
            database.withTransaction {
                val roll = rollDao.getById(rollId)
                    ?: error("Warehouse roll $rollId not found")
                require(roll.availableMeters >= meters) {
                    "Insufficient stock on ${roll.rollCode}: ${roll.availableMeters}m available."
                }
                val now = System.currentTimeMillis()
                val remaining = (roll.availableMeters - meters).coerceAtLeast(0.0)
                val newStatus = when {
                    remaining <= 0.0 -> RollStatus.OUT_OF_STOCK
                    remaining < roll.lowStockThresholdMeters -> RollStatus.LOW_STOCK
                    else -> RollStatus.RESERVED
                }
                rollDao.upsert(
                    roll.copy(
                        availableMeters = remaining,
                        status = newStatus,
                        version = roll.version + 1L,
                        updatedAt = now,
                    ),
                )
                val entity = ReservationEntity(
                    id = "res-${UUID.randomUUID().toString().take(8)}",
                    rollId = rollId,
                    swatchId = swatchId,
                    metersReserved = meters,
                    reservedBy = operatorName.ifBlank { "Operator" },
                    note = note,
                    status = ReservationStatus.ACTIVE,
                    createdAt = now,
                    expiresAt = now + TimeUnit.DAYS.toMillis(3),
                )
                reservationDao.upsert(entity)
                entity.toDomain()
            }
        }
    }

    override suspend fun releaseReservation(reservationId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                database.withTransaction {
                    val res = reservationDao.getById(reservationId)
                        ?: error("Reservation $reservationId not found")
                    if (res.status != ReservationStatus.ACTIVE) return@withTransaction
                    val now = System.currentTimeMillis()
                    reservationDao.upsert(res.copy(status = ReservationStatus.RELEASED))
                    val roll = rollDao.getById(res.rollId) ?: return@withTransaction
                    val restored = (roll.availableMeters + res.metersReserved).coerceAtMost(roll.lengthMeters)
                    val newStatus = when {
                        restored <= 0.0 -> RollStatus.OUT_OF_STOCK
                        restored < roll.lowStockThresholdMeters -> RollStatus.LOW_STOCK
                        else -> RollStatus.IN_STOCK
                    }
                    rollDao.upsert(
                        roll.copy(
                            availableMeters = restored,
                            status = newStatus,
                            version = roll.version + 1L,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }

    override suspend fun logCut(
        rollId: String,
        swatchId: String,
        metersCut: Double,
        pieceCount: Int,
        operatorName: String,
    ): Result<CutLogModel> = withContext(ioDispatcher) {
        runCatching {
            require(metersCut > 0.0) { "Cut meters must be positive." }
            database.withTransaction {
                val roll = rollDao.getById(rollId) ?: error("Warehouse roll $rollId not found")
                val now = System.currentTimeMillis()
                val remaining = (roll.availableMeters - metersCut).coerceAtLeast(0.0)
                val newStatus = when {
                    remaining <= 0.0 -> RollStatus.OUT_OF_STOCK
                    remaining < roll.lowStockThresholdMeters -> RollStatus.LOW_STOCK
                    else -> RollStatus.IN_STOCK
                }
                rollDao.upsert(
                    roll.copy(
                        availableMeters = remaining,
                        status = newStatus,
                        version = roll.version + 1L,
                        updatedAt = now,
                    ),
                )
                val entry = CutLogEntity(
                    id = "cut-${UUID.randomUUID().toString().take(8)}",
                    rollId = rollId,
                    swatchId = swatchId,
                    metersCut = metersCut,
                    pieceCount = pieceCount.coerceAtLeast(1),
                    operatorName = operatorName.ifBlank { "Operator" },
                    createdAt = now,
                )
                cutLogDao.insert(entry)
                entry.toDomain()
            }
        }
    }

    override suspend fun updateSwatchPrice(swatchId: String, pricePerMeter: Double?) =
        withContext(ioDispatcher) {
            swatchDao.updatePrice(swatchId, pricePerMeter, System.currentTimeMillis())
        }

    override suspend fun upsertPriceTier(tier: PriceTierModel) = withContext(ioDispatcher) {
        priceTierDao.upsert(
            PriceTierEntity(
                id = tier.id.ifBlank { "tier-${UUID.randomUUID().toString().take(8)}" },
                fabricTypeId = tier.fabricTypeId,
                tierName = tier.tierName,
                minMeters = tier.minMeters,
                pricePerMeter = tier.pricePerMeter,
                currencyCode = tier.currencyCode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun getLowStockRolls(): List<WarehouseRollModel> = withContext(ioDispatcher) {
        rollDao.getLowStockRolls().map { it.toDomain() }
    }
}
