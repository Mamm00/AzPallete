package com.alazhartex.shadecart.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds authentic Al AzharTex fabric catalog records (`FabricType`), warehouse `Roll` inventory
 * records, initial `DyeLotEntity` batches, and `PriceTierEntity` tiers so swatches, QR codes,
 * dye-lot checks, and pricing tiers work immediately out of the box.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val fabricTypeDao: FabricTypeDao,
    private val rollDao: RollDao,
    private val dyeLotDao: DyeLotDao,
    private val priceTierDao: PriceTierDao,
) {
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (fabricTypeDao.count() == 0) {
            fabricTypeDao.upsertAll(defaultFabricTypes(now))
        }
        if (rollDao.count() == 0) {
            rollDao.upsertAll(defaultRolls(now))
        }
        if (dyeLotDao.count() == 0) {
            dyeLotDao.upsertAll(defaultDyeLots(now))
        }
        if (priceTierDao.count() == 0) {
            priceTierDao.upsertAll(defaultPriceTiers(now))
        }
    }

    companion object {
        fun defaultFabricTypes(now: Long = 1_700_000_000_000L): List<FabricType> = listOf(
            FabricType(
                id = "fab-silk-180",
                name = "Silk 180",
                code = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
                version = 1L,
                updatedAt = now,
            ),
            FabricType(
                id = "fab-warsaw-l",
                name = "Warsaw L",
                code = "WRS-L",
                weightGsm = 220,
                widthCm = 150,
                version = 1L,
                updatedAt = now,
            ),
            FabricType(
                id = "fab-pirlanta",
                name = "Pirlanta",
                code = "PRL-165",
                weightGsm = 165,
                widthCm = 148,
                version = 1L,
                updatedAt = now,
            ),
            FabricType(
                id = "fab-rotana-180",
                name = "Rotana 180",
                code = "RTN-180",
                weightGsm = 180,
                widthCm = 160,
                version = 1L,
                updatedAt = now,
            ),
            FabricType(
                id = "fab-lexus-150",
                name = "Lexus 150",
                code = "LXS-150",
                weightGsm = 150,
                widthCm = 150,
                version = 1L,
                updatedAt = now,
            ),
        )

        fun defaultRolls(now: Long = 1_700_000_000_000L): List<Roll> = listOf(
            Roll(
                id = "roll-slk-5",
                rollCode = "RL-SLK-005",
                fabricTypeId = "fab-silk-180",
                shadeLabel = "5",
                colorName = "Azhar Navy",
                colorHex = "#102A71",
                warehouseShed = "wh-alwa",
                lengthMeters = 480.0,
                availableMeters = 480.0,
                lotCode = "LOT-2026-05A",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-SLK-005",
                status = RollStatus.IN_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-slk-7",
                rollCode = "RL-SLK-007",
                fabricTypeId = "fab-silk-180",
                shadeLabel = "7",
                colorName = "Crimson Star",
                colorHex = "#CD1820",
                warehouseShed = "wh-showroom",
                lengthMeters = 310.0,
                availableMeters = 310.0,
                lotCode = "LOT-2026-07B",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-SLK-007",
                status = RollStatus.IN_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-slk-11",
                rollCode = "RL-SLK-011",
                fabricTypeId = "fab-silk-180",
                shadeLabel = "11",
                colorName = "Azhar Emerald",
                colorHex = "#0F5132",
                warehouseShed = "wh-625",
                lengthMeters = 520.0,
                availableMeters = 520.0,
                lotCode = "LOT-2026-11A",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-SLK-011",
                status = RollStatus.IN_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-slk-12",
                rollCode = "RL-SLK-012",
                fabricTypeId = "fab-silk-180",
                shadeLabel = "12",
                colorName = "Cairo Sand",
                colorHex = "#D4A017",
                warehouseShed = "wh-zibda",
                lengthMeters = 125.0,
                availableMeters = 38.0,
                lotCode = "LOT-2026-12A",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-SLK-012",
                status = RollStatus.LOW_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-slk-18",
                rollCode = "RL-SLK-018",
                fabricTypeId = "fab-silk-180",
                shadeLabel = "18",
                colorName = "Nile Teal",
                colorHex = "#187480",
                warehouseShed = "wh-leon",
                lengthMeters = 390.0,
                availableMeters = 390.0,
                lotCode = "LOT-2026-18A",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-SLK-018",
                status = RollStatus.IN_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-wrs-3",
                rollCode = "RL-WRS-003",
                fabricTypeId = "fab-warsaw-l",
                shadeLabel = "3",
                colorName = "Deep Charcoal",
                colorHex = "#383E4A",
                warehouseShed = "wh-alwa",
                lengthMeters = 640.0,
                availableMeters = 640.0,
                lotCode = "LOT-2026-03W",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-WRS-003",
                status = RollStatus.IN_STOCK,
                version = 1L,
                updatedAt = now,
            ),
            Roll(
                id = "roll-prl-9",
                rollCode = "RL-PRL-009",
                fabricTypeId = "fab-pirlanta",
                shadeLabel = "9",
                colorName = "Burgundy",
                colorHex = "#761D3A",
                warehouseShed = "wh-azouz",
                lengthMeters = 215.0,
                availableMeters = 140.0,
                lotCode = "LOT-2026-09P",
                lowStockThresholdMeters = 50.0,
                qrPayload = "azpallete://roll/RL-PRL-009",
                status = RollStatus.RESERVED,
                version = 1L,
                updatedAt = now,
            ),
        )

        fun defaultDyeLots(now: Long = 1_700_000_000_000L): List<DyeLotEntity> = listOf(
            DyeLotEntity(
                id = "lot-slk5-a",
                rollId = "roll-slk-5",
                lotCode = "LOT-2026-05A",
                dyeDate = now - 86_400_000L * 5,
                masterDeltaE = 0.65,
                operatorName = "Ahmed",
                notes = "Master approved under D65",
                version = 1L,
                updatedAt = now,
            ),
            DyeLotEntity(
                id = "lot-slk7-b",
                rollId = "roll-slk-7",
                lotCode = "LOT-2026-07B",
                dyeDate = now - 86_400_000L * 2,
                masterDeltaE = 1.15,
                operatorName = "Hassan",
                notes = "Reactive dye vat #3",
                version = 1L,
                updatedAt = now,
            ),
        )

        fun defaultPriceTiers(now: Long = 1_700_000_000_000L): List<PriceTierEntity> = listOf(
            PriceTierEntity(
                id = "tier-slk-retail",
                fabricTypeId = "fab-silk-180",
                tierName = "Sample / Cut",
                minMeters = 1.0,
                pricePerMeter = 18.0,
                currencyCode = "EGP",
                updatedAt = now,
            ),
            PriceTierEntity(
                id = "tier-slk-wholesale",
                fabricTypeId = "fab-silk-180",
                tierName = "Roll Wholesale",
                minMeters = 50.0,
                pricePerMeter = 14.5,
                currencyCode = "EGP",
                updatedAt = now,
            ),
            PriceTierEntity(
                id = "tier-slk-factory",
                fabricTypeId = "fab-silk-180",
                tierName = "Mill Carton (500m+)",
                minMeters = 500.0,
                pricePerMeter = 12.25,
                currencyCode = "EGP",
                updatedAt = now,
            ),
        )
    }
}
