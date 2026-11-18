package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.FavoritedShadeMetric
import com.alazhartex.shadecart.domain.model.FastMovingShadeMetric
import com.alazhartex.shadecart.domain.model.LotVarianceSummaryItem
import com.alazhartex.shadecart.domain.model.MillInsightsReport
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SlowMovingShadeMetric
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * E2: Computes local-only mill insights (stock value per shed, slowest/fastest moving shades,
 * most favorited shades, sync health score, and lot variance summary) with zero external analytics.
 */
class ComputeMillInsightsUseCase @Inject constructor(
    private val shadeCartRepository: ShadeCartRepositoryContract,
    private val millInventoryRepository: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(
        favoritedIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): MillInsightsReport {
        val carts = shadeCartRepository.observeCarts().first()
        val rolls = shadeCartRepository.observeRolls(null).first()
        val fabrics = shadeCartRepository.observeFabricTypes().first()
        val syncQueue = shadeCartRepository.observeSyncQueue().first()
        val lots = millInventoryRepository.observeAllLots().first()
        val reservations = millInventoryRepository.observeAllReservations().first()
        val cuts = millInventoryRepository.observeCutLogs().first()
        return compute(
            carts = carts,
            rolls = rolls,
            fabricNamesById = fabrics.associate { it.id to it.name },
            syncQueue = syncQueue,
            lots = lots,
            reservations = reservations,
            cuts = cuts,
            favoritedIds = favoritedIds,
            nowMillis = nowMillis,
        )
    }

    fun compute(
        carts: List<ShadeCartModel>,
        rolls: List<WarehouseRollModel>,
        fabricNamesById: Map<String, String>,
        syncQueue: List<SyncQueueEntryModel>,
        lots: List<DyeLotModel>,
        reservations: List<ReservationModel>,
        cuts: List<CutLogModel>,
        favoritedIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): MillInsightsReport {
        val allSwatches = carts.flatMap { it.swatches }
        val swatchesById = allSwatches.associateBy { it.id }
        val swatchesByRollId = allSwatches.filter { it.rollId != null }.associateBy { it.rollId!! }

        val stockByFabricType = linkedMapOf<String, Double>()
        val stockValuePerShed = linkedMapOf<String, Double>()
        var totalValue = 0.0

        for (roll in rolls) {
            val fabricName = fabricNamesById[roll.fabricTypeId] ?: roll.fabricTypeId
            stockByFabricType[fabricName] = (stockByFabricType[fabricName] ?: 0.0) + roll.availableMeters
            val unitPrice = swatchesByRollId[roll.id]?.pricePerMeter ?: 14.5
            val value = roll.availableMeters * unitPrice
            stockValuePerShed[roll.warehouseShed] = (stockValuePerShed[roll.warehouseShed] ?: 0.0) + value
            totalValue += value
        }

        val lowStockCount = rolls.count {
            it.status == RollStockStatus.LOW_STOCK ||
                (it.availableMeters < it.lowStockThresholdMeters && it.status != RollStockStatus.OUT_OF_STOCK)
        }
        val pendingCount = syncQueue.count { it.status == SyncState.PENDING || it.status == SyncState.CONFLICT }
        val syncHealthScore = if (syncQueue.isEmpty()) {
            1.0f
        } else {
            (1.0f - (pendingCount.toFloat() / syncQueue.size.toFloat())).coerceIn(0f, 1f)
        }

        val reservationsBySwatch = reservations.groupBy { it.swatchId }
        val cutsBySwatch = cuts.groupBy { it.swatchId }

        val slowest = allSwatches
            .filter { (reservationsBySwatch[it.id]?.size ?: 0) == 0 && (cutsBySwatch[it.id]?.size ?: 0) == 0 }
            .map { sw ->
                val days = TimeUnit.MILLISECONDS.toDays((nowMillis - sw.updatedAt).coerceAtLeast(0L))
                SlowMovingShadeMetric(
                    swatch = sw,
                    daysInStock = days,
                    metersRemaining = sw.linkedRoll?.availableMeters ?: 0.0,
                )
            }
            .sortedByDescending { it.metersRemaining }
            .take(5)

        val fastest = allSwatches
            .map { sw ->
                FastMovingShadeMetric(
                    swatch = sw,
                    reservationsCount = reservationsBySwatch[sw.id]?.size ?: 0,
                    cutCount = cutsBySwatch[sw.id]?.size ?: 0,
                )
            }
            .filter { it.reservationsCount > 0 || it.cutCount > 0 }
            .sortedByDescending { it.reservationsCount + it.cutCount }
            .take(5)

        val mostFavorited = favoritedIds.mapNotNull { id ->
            swatchesById[id]?.let { sw -> FavoritedShadeMetric(swatch = sw, favoriteCount = 1) }
        }

        val rollsById = rolls.associateBy { it.id }
        val lotVarianceSummary = lots
            .filter { it.masterDeltaE >= 0.0 }
            .groupBy { lot -> rollsById[lot.rollId]?.colorName ?: "General" }
            .map { (family, familyLots) ->
                LotVarianceSummaryItem(
                    colorFamily = family,
                    worstDeltaE = familyLots.maxOf { it.masterDeltaE }.toFloat(),
                    lotCount = familyLots.size,
                )
            }
            .sortedByDescending { it.worstDeltaE }

        return MillInsightsReport(
            totalActiveCarts = carts.size,
            lowStockCount = lowStockCount,
            pendingSyncCount = pendingCount,
            syncHealthScore = syncHealthScore,
            stockByFabricType = stockByFabricType,
            stockValuePerShed = stockValuePerShed,
            totalInventoryValue = totalValue,
            slowestMovingShades = slowest,
            fastestMovingShades = fastest,
            mostFavoritedShades = mostFavorited,
            lotVarianceSummary = lotVarianceSummary,
        )
    }
}
