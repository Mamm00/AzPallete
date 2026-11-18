package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.PriceTierModel
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAllLotsUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    operator fun invoke(): Flow<List<DyeLotModel>> = repo.observeAllLots()
}

class ObserveLotsForRollUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    operator fun invoke(rollId: String): Flow<List<DyeLotModel>> = repo.observeLotsForRoll(rollId)
}

class ObserveActiveReservationsUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    operator fun invoke(): Flow<List<ReservationModel>> = repo.observeActiveReservations()
}

class ObserveCutLogsUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    operator fun invoke(): Flow<List<CutLogModel>> = repo.observeCutLogs()
}

class ObservePriceTiersUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    operator fun invoke(): Flow<List<PriceTierModel>> = repo.observePriceTiers()
}

class CreateWarehouseRollUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(roll: WarehouseRollModel): Result<WarehouseRollModel> =
        repo.createRoll(roll)
}

class CreateDyeLotUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(lot: DyeLotModel): Result<DyeLotModel> = repo.createDyeLot(lot)
}

class ReserveRollMetersUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(
        rollId: String,
        swatchId: String,
        meters: Double,
        operatorName: String,
        note: String = "",
    ): Result<ReservationModel> = repo.reserveMeters(
        rollId = rollId,
        swatchId = swatchId,
        meters = meters,
        operatorName = operatorName,
        note = note,
    )
}

class ReleaseReservationUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(reservationId: String): Result<Unit> =
        repo.releaseReservation(reservationId)
}

class LogRollCutUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(
        rollId: String,
        swatchId: String,
        metersCut: Double,
        pieceCount: Int,
        operatorName: String,
    ): Result<CutLogModel> = repo.logCut(
        rollId = rollId,
        swatchId = swatchId,
        metersCut = metersCut,
        pieceCount = pieceCount,
        operatorName = operatorName,
    )
}

class UpdateSwatchPriceUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(swatchId: String, pricePerMeter: Double?) =
        repo.updateSwatchPrice(swatchId, pricePerMeter)
}

class UpsertPriceTierUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(tier: PriceTierModel) = repo.upsertPriceTier(tier)
}

class GetLowStockRollsUseCase @Inject constructor(
    private val repo: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(): List<WarehouseRollModel> = repo.getLowStockRolls()
}
