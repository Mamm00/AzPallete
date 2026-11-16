package com.alazhartex.shadecart.data.local

import androidx.annotation.WorkerThread
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds authentic Al AzharTex fabric catalog records (FabricType) and warehouse Roll inventory
 * records so swatches detected or manually edited can immediately link to real inventory rows.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val fabricTypeDao: FabricTypeDao,
    private val rollDao: RollDao,
) {
    @WorkerThread
    suspend fun ensureSeeded() {
        val now = System.currentTimeMillis()
        if (fabricTypeDao.count() == 0) {
            fabricTypeDao.upsertAll(defaultFabricTypes(now))
        }
        if (rollDao.count() == 0) {
            rollDao.upsertAll(defaultRolls(now))
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
                status = RollStatus.RESERVED,
                version = 1L,
                updatedAt = now,
            ),
        )
    }
}
