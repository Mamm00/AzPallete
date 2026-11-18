package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.ColorSearchResult
import com.alazhartex.shadecart.domain.model.DuplicateShadeWarning
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * C4: "Reverse Shade Finder" — searches all digitized swatches across saved carts by CIEDE2000
 * perceptual color distance from [targetLab], filtering by [maxDeltaE] and optional fabric type.
 */
class SearchShadesByColorUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    suspend operator fun invoke(
        targetLab: Triple<Float, Float, Float>,
        maxDeltaE: Float = 10.0f,
        fabricTypeIdFilter: String? = null,
        includeOutOfStock: Boolean = false,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): List<ColorSearchResult> = withContext(ioDispatcher) {
        val carts = repository.observeCarts().first()
        val rollsById = repository.observeRolls(null).first().associateBy { it.id }
        rankMatches(
            targetLab = targetLab,
            carts = carts,
            rollsById = rollsById,
            maxDeltaE = maxDeltaE,
            fabricTypeIdFilter = fabricTypeIdFilter,
            includeOutOfStock = includeOutOfStock,
        )
    }

    fun rankMatches(
        targetLab: Triple<Float, Float, Float>,
        carts: List<ShadeCartModel>,
        rollsById: Map<String, com.alazhartex.shadecart.domain.model.WarehouseRollModel>,
        maxDeltaE: Float = 10.0f,
        fabricTypeIdFilter: String? = null,
        includeOutOfStock: Boolean = false,
    ): List<ColorSearchResult> {
        val results = mutableListOf<ColorSearchResult>()
        for (cart in carts) {
            if (!fabricTypeIdFilter.isNullOrBlank() && cart.fabricTypeId != fabricTypeIdFilter) {
                continue
            }
            for (swatch in cart.swatches) {
                val roll = swatch.rollId?.let { rollsById[it] } ?: swatch.linkedRoll
                if (!includeOutOfStock && roll != null && roll.status == RollStockStatus.OUT_OF_STOCK) {
                    continue
                }
                val swatchLab = resolveLab(swatch) ?: continue
                val dE = ColorScience.ciede2000(targetLab, swatchLab)
                if (dE <= maxDeltaE) {
                    results.add(
                        ColorSearchResult(
                            swatch = swatch,
                            cartName = cart.fabricName,
                            cartCode = cart.fabricCode,
                            fabricTypeName = cart.fabricType?.name ?: cart.fabricName,
                            roll = roll,
                            deltaE = dE,
                        ),
                    )
                }
            }
        }
        return results.sortedBy { it.deltaE }
    }

    private fun resolveLab(swatch: SwatchModel): Triple<Float, Float, Float>? {
        val hasStored = swatch.labL != 0.0 || swatch.labA != 0.0 || swatch.labB != 0.0
        return if (hasStored) {
            Triple(swatch.labL.toFloat(), swatch.labA.toFloat(), swatch.labB.toFloat())
        } else {
            ColorScience.hexToLab(swatch.colorHex)
        }
    }
}

/**
 * A2: On-save duplicate shade guard checking whether any proposed swatch is perceptually closer
 * than [thresholdDeltaE] (default `< 2.0`) to an already cataloged swatch in another cart.
 */
class CheckDuplicateShadeUseCase @Inject constructor(
    private val repository: ShadeCartRepositoryContract,
) {
    suspend operator fun invoke(
        candidateHexes: List<Pair<String, String>>,
        excludeCartId: String? = null,
        thresholdDeltaE: Float = 2.0f,
    ): List<DuplicateShadeWarning> {
        val carts = repository.observeCarts().first()
        return findDuplicates(
            candidateHexes = candidateHexes,
            existingCarts = carts,
            excludeCartId = excludeCartId,
            thresholdDeltaE = thresholdDeltaE,
        )
    }

    fun findDuplicates(
        candidateHexes: List<Pair<String, String>>,
        existingCarts: List<ShadeCartModel>,
        excludeCartId: String? = null,
        thresholdDeltaE: Float = 2.0f,
    ): List<DuplicateShadeWarning> {
        val warnings = mutableListOf<DuplicateShadeWarning>()
        for ((_, hex) in candidateHexes) {
            val candidateLab = ColorScience.hexToLab(hex) ?: continue
            for (cart in existingCarts) {
                if (excludeCartId != null && cart.id == excludeCartId) continue
                for (sw in cart.swatches) {
                    val existingLab = if (sw.labL != 0.0 || sw.labA != 0.0 || sw.labB != 0.0) {
                        Triple(sw.labL.toFloat(), sw.labA.toFloat(), sw.labB.toFloat())
                    } else {
                        ColorScience.hexToLab(sw.colorHex)
                    } ?: continue
                    val dE = ColorScience.ciede2000(candidateLab, existingLab)
                    if (dE < thresholdDeltaE) {
                        warnings.add(
                            DuplicateShadeWarning(
                                existingSwatchId = sw.id,
                                existingSwatchNumber = sw.swatchNumber,
                                existingColorName = sw.colorName.ifBlank { sw.colorHex },
                                existingCartName = cart.fabricName,
                                deltaE = dE,
                            ),
                        )
                    }
                }
            }
        }
        return warnings.distinctBy { it.existingSwatchId }.sortedBy { it.deltaE }
    }
}
