package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.LotDeltaE
import com.alazhartex.shadecart.domain.model.MixedLotWarning
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeConsistencyReport
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

/**
 * C2: Evaluates dye-lot variance and shade consistency across all linked rolls on a Shade Cart,
 * generating mixed-lot warnings whenever variance exceeds [toleranceDeltaE].
 */
class ShadeConsistencyUseCase @Inject constructor(
    private val shadeCartRepository: ShadeCartRepositoryContract,
    private val millInventoryRepository: MillInventoryRepositoryContract,
) {
    suspend operator fun invoke(
        cartId: String,
        toleranceDeltaE: Float = 2.0f,
    ): ShadeConsistencyReport? {
        val cart = shadeCartRepository.observeCart(cartId).first() ?: return null
        val rolls = shadeCartRepository.observeRolls(null).first()
        val lots = millInventoryRepository.observeAllLots().first()
        return evaluate(cart, rolls, lots, toleranceDeltaE)
    }

    fun evaluate(
        cart: ShadeCartModel,
        rolls: List<WarehouseRollModel>,
        lots: List<DyeLotModel>,
        toleranceDeltaE: Float = 2.0f,
    ): ShadeConsistencyReport {
        val rollsById = rolls.associateBy { it.id }
        val lotsByRollId = lots.groupBy { it.rollId }

        var worstDeltaE = 0f
        val perShadeVariance = linkedMapOf<String, List<LotDeltaE>>()
        val warnings = mutableListOf<MixedLotWarning>()

        for (swatch in cart.swatches) {
            val swatchLab = if (swatch.labL != 0.0 || swatch.labA != 0.0 || swatch.labB != 0.0) {
                Triple(swatch.labL.toFloat(), swatch.labA.toFloat(), swatch.labB.toFloat())
            } else {
                ColorScience.hexToLab(swatch.colorHex)
            }

            val linkedRoll = swatch.rollId?.let { rollsById[it] } ?: swatch.linkedRoll
            val matchingRolls = buildList {
                if (linkedRoll != null) add(linkedRoll)
                rolls.filter {
                    it.fabricTypeId == swatch.fabricTypeId &&
                        it.shadeLabel.equals(swatch.swatchNumber, ignoreCase = true) &&
                        it.id != linkedRoll?.id
                }.forEach { add(it) }
            }

            val lotEntries = mutableListOf<LotDeltaE>()
            for (roll in matchingRolls) {
                val rollLots = lotsByRollId[roll.id].orEmpty()
                if (rollLots.isEmpty()) {
                    val rollLab = ColorScience.hexToLab(roll.colorHex)
                    if (swatchLab != null && rollLab != null) {
                        val dE = ColorScience.ciede2000(swatchLab, rollLab)
                        if (dE > worstDeltaE) worstDeltaE = dE
                    }
                } else {
                    for (lot in rollLots) {
                        val dE = if (lot.masterDeltaE >= 0.0) {
                            lot.masterDeltaE.toFloat()
                        } else {
                            val rollLab = ColorScience.hexToLab(roll.colorHex)
                            if (swatchLab != null && rollLab != null) {
                                ColorScience.ciede2000(swatchLab, rollLab)
                            } else {
                                0f
                            }
                        }
                        if (dE > worstDeltaE) worstDeltaE = dE
                        lotEntries.add(LotDeltaE(lot = lot, rollCode = roll.rollCode, deltaE = dE))
                    }
                }
            }

            if (lotEntries.isNotEmpty()) {
                perShadeVariance[swatch.id] = lotEntries
                if (lotEntries.size >= 2) {
                    val minLot = lotEntries.minBy { it.deltaE }
                    val maxLot = lotEntries.maxBy { it.deltaE }
                    val diff = abs(maxLot.deltaE - minLot.deltaE)
                    if (diff > toleranceDeltaE || maxLot.deltaE > toleranceDeltaE) {
                        warnings.add(
                            MixedLotWarning(
                                swatchId = swatch.id,
                                swatchNumber = swatch.swatchNumber,
                                lotCodeA = minLot.lot.lotCode,
                                lotCodeB = maxLot.lot.lotCode,
                                deltaEDiff = maxOf(diff, maxLot.deltaE),
                            ),
                        )
                    }
                } else if (lotEntries.first().deltaE > toleranceDeltaE) {
                    val single = lotEntries.first()
                    warnings.add(
                        MixedLotWarning(
                            swatchId = swatch.id,
                            swatchNumber = swatch.swatchNumber,
                            lotCodeA = single.lot.lotCode,
                            lotCodeB = "Master",
                            deltaEDiff = single.deltaE,
                        ),
                    )
                }
            }
        }

        return ShadeConsistencyReport(
            cartId = cart.id,
            worstDeltaE = worstDeltaE,
            mixedLotWarnings = warnings,
            perShadeVariance = perShadeVariance,
        )
    }
}
