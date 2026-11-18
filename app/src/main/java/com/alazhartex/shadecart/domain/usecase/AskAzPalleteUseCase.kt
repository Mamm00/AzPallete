package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.AskAzPalleteResult
import com.alazhartex.shadecart.domain.model.AskAzPalleteSource
import com.alazhartex.shadecart.domain.model.AskInventoryItem
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import com.alazhartex.shadecart.domain.repository.NaturalLanguageQueryGateway
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

/**
 * E1: Offline-first template NL query parser & Gemini Nano gateway orchestrator.
 *
 * Parses English and Arabic textile queries (color families like "navy"/"كحلي", minimum meterage
 * "more than 200 meters", stock flags "in stock"/"low stock", fabric names like "Silk 180",
 * and lot codes) identically whether AICore Gemini Nano is present or unavailable.
 */
class OfflineTemplateNaturalLanguageParser {

    companion object {
        private val METERS_MORE_THAN_REGEX =
            Regex("""(?:more than|over|above|>|أكثر من|فوق)\s*(\d+(?:\.\d+)?)\s*(?:m|meters|meter|متر)?""", RegexOption.IGNORE_CASE)
        private val METERS_UNDER_REGEX =
            Regex("""(?:less than|under|below|<|أقل من|تحت)\s*(\d+(?:\.\d+)?)\s*(?:m|meters|meter|متر)?""", RegexOption.IGNORE_CASE)

        private val COLOR_FAMILY_SYNONYMS: Map<String, Triple<Float, Float, Float>> = mapOf(
            "navy" to ColorScience.srgbToLab(16, 42, 113),
            "كحلي" to ColorScience.srgbToLab(16, 42, 113),
            "crimson" to ColorScience.srgbToLab(205, 24, 32),
            "red" to ColorScience.srgbToLab(205, 24, 32),
            "أحمر" to ColorScience.srgbToLab(205, 24, 32),
            "قرمزي" to ColorScience.srgbToLab(205, 24, 32),
            "emerald" to ColorScience.srgbToLab(15, 81, 50),
            "green" to ColorScience.srgbToLab(15, 81, 50),
            "أخضر" to ColorScience.srgbToLab(15, 81, 50),
            "زمردي" to ColorScience.srgbToLab(15, 81, 50),
            "sand" to ColorScience.srgbToLab(212, 160, 23),
            "gold" to ColorScience.srgbToLab(212, 160, 23),
            "ذهبي" to ColorScience.srgbToLab(212, 160, 23),
            "رملي" to ColorScience.srgbToLab(212, 160, 23),
            "teal" to ColorScience.srgbToLab(24, 116, 128),
            "بترولي" to ColorScience.srgbToLab(24, 116, 128),
            "burgundy" to ColorScience.srgbToLab(118, 29, 58),
            "نبيتي" to ColorScience.srgbToLab(118, 29, 58),
            "charcoal" to ColorScience.srgbToLab(56, 62, 74),
            "black" to ColorScience.srgbToLab(25, 25, 28),
            "أسود" to ColorScience.srgbToLab(25, 25, 28),
            "رمادي" to ColorScience.srgbToLab(56, 62, 74),
        )
    }

    fun execute(
        query: String,
        carts: List<ShadeCartModel>,
        rolls: List<WarehouseRollModel>,
        lots: List<DyeLotModel>,
        source: AskAzPalleteSource = AskAzPalleteSource.OFFLINE_TEMPLATE,
    ): AskAzPalleteResult {
        val clean = query.trim()
        val lower = clean.lowercase(Locale.ROOT)
        val minMeters = METERS_MORE_THAN_REGEX.find(clean)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val maxMeters = METERS_UNDER_REGEX.find(clean)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val requireInStock = lower.contains("in stock") || lower.contains("available") || lower.contains("متاح")
        val requireLowStock = lower.contains("low stock") || lower.contains("منخفض")

        val matchedColorLab = COLOR_FAMILY_SYNONYMS.entries
            .firstOrNull { (token, _) -> lower.contains(token) }
            ?.value

        val fabricTokens = carts.map { it.fabricName.lowercase(Locale.ROOT) }.distinct()
            .filter { it.isNotBlank() && lower.contains(it) }

        val rollsById = rolls.associateBy { it.id }
        val lotsByRollId = lots.groupBy { it.rollId }
        val matches = mutableListOf<AskInventoryItem>()

        // Search across swatches first, then unlinked rolls
        val seenRollIds = HashSet<String>()
        for (cart in carts) {
            for (sw in cart.swatches) {
                val roll = sw.rollId?.let { rollsById[it] } ?: sw.linkedRoll
                if (roll != null) seenRollIds.add(roll.id)

                if (fabricTokens.isNotEmpty() &&
                    fabricTokens.none { cart.fabricName.lowercase(Locale.ROOT).contains(it) }
                ) {
                    continue
                }
                if (requireInStock && roll?.status != RollStockStatus.IN_STOCK) continue
                if (requireLowStock && roll?.status != RollStockStatus.LOW_STOCK) continue
                if (minMeters != null && (roll?.availableMeters ?: 0.0) <= minMeters) continue
                if (maxMeters != null && (roll?.availableMeters ?: Double.MAX_VALUE) >= maxMeters) continue

                val swLab = ColorScience.hexToLab(sw.colorHex)
                val dE = if (matchedColorLab != null && swLab != null) {
                    ColorScience.ciede2000(matchedColorLab, swLab)
                } else {
                    null
                }
                if (matchedColorLab != null && (dE == null || dE > 26f)) {
                    val nameMatches = COLOR_FAMILY_SYNONYMS.keys.any {
                        lower.contains(it) && sw.colorName.lowercase(Locale.ROOT).contains(it)
                    }
                    if (!nameMatches) continue
                }

                if (matchedColorLab == null && fabricTokens.isEmpty() && minMeters == null &&
                    maxMeters == null && !requireInStock && !requireLowStock && clean.isNotBlank()
                ) {
                    val lotHit = roll != null && lotsByRollId[roll.id].orEmpty().any {
                        it.lotCode.lowercase(Locale.ROOT).contains(lower)
                    }
                    val textHit = sw.colorName.lowercase(Locale.ROOT).contains(lower) ||
                        sw.colorHex.lowercase(Locale.ROOT).contains(lower) ||
                        sw.swatchNumber.lowercase(Locale.ROOT).contains(lower) ||
                        cart.fabricName.lowercase(Locale.ROOT).contains(lower) ||
                        (roll?.rollCode?.lowercase(Locale.ROOT)?.contains(lower) == true) ||
                        (roll?.warehouseShed?.lowercase(Locale.ROOT)?.contains(lower) == true) ||
                        lotHit
                    if (!textHit) continue
                }

                matches.add(
                    AskInventoryItem(
                        swatchId = sw.id,
                        swatchNumber = sw.swatchNumber,
                        colorName = sw.colorName,
                        colorHex = sw.colorHex,
                        fabricName = cart.fabricName,
                        rollCode = roll?.rollCode,
                        warehouseShed = roll?.warehouseShed,
                        availableMeters = roll?.availableMeters,
                        deltaE = dE,
                    ),
                )
            }
        }

        // Include matching rolls not currently linked to a cart swatch
        for (roll in rolls) {
            if (seenRollIds.contains(roll.id)) continue
            if (requireInStock && roll.status != RollStockStatus.IN_STOCK) continue
            if (requireLowStock && roll.status != RollStockStatus.LOW_STOCK) continue
            if (minMeters != null && roll.availableMeters <= minMeters) continue
            if (maxMeters != null && roll.availableMeters >= maxMeters) continue
            val rollLab = ColorScience.hexToLab(roll.colorHex)
            val dE = if (matchedColorLab != null && rollLab != null) {
                ColorScience.ciede2000(matchedColorLab, rollLab)
            } else {
                null
            }
            if (matchedColorLab != null && (dE == null || dE > 26f)) continue
            if (matchedColorLab == null && minMeters == null && maxMeters == null &&
                !requireInStock && !requireLowStock && clean.isNotBlank()
            ) {
                val hit = roll.colorName.lowercase(Locale.ROOT).contains(lower) ||
                    roll.rollCode.lowercase(Locale.ROOT).contains(lower) ||
                    roll.warehouseShed.lowercase(Locale.ROOT).contains(lower) ||
                    roll.lotCode.lowercase(Locale.ROOT).contains(lower)
                if (!hit) continue
            }
            matches.add(
                AskInventoryItem(
                    swatchId = roll.id,
                    swatchNumber = roll.shadeLabel,
                    colorName = roll.colorName,
                    colorHex = roll.colorHex,
                    fabricName = roll.fabricTypeId,
                    rollCode = roll.rollCode,
                    warehouseShed = roll.warehouseShed,
                    availableMeters = roll.availableMeters,
                    deltaE = dE,
                ),
            )
        }

        val sorted = if (matchedColorLab != null) {
            matches.sortedBy { it.deltaE ?: Float.MAX_VALUE }
        } else {
            matches.sortedByDescending { it.availableMeters ?: 0.0 }
        }
        val rollCount = sorted.mapNotNull { it.rollCode }.distinct().size
        val summary = "Found ${sorted.size} matching shade entries across $rollCount warehouse rolls."

        return AskAzPalleteResult(
            query = clean,
            source = source,
            summary = summary,
            items = sorted,
        )
    }
}

class AskAzPalleteUseCase @Inject constructor(
    private val shadeCartRepository: ShadeCartRepositoryContract,
    private val millInventoryRepository: MillInventoryRepositoryContract,
    private val gateway: NaturalLanguageQueryGateway,
) {
    suspend fun isNanoAvailable(): Boolean = gateway.isNanoAvailable()

    suspend operator fun invoke(query: String): AskAzPalleteResult {
        val carts = shadeCartRepository.observeCarts().first()
        val rolls = shadeCartRepository.observeRolls(null).first()
        val lots = millInventoryRepository.observeAllLots().first()
        return gateway.query(
            naturalQuery = query,
            carts = carts,
            rolls = rolls,
            lots = lots,
        )
    }
}
