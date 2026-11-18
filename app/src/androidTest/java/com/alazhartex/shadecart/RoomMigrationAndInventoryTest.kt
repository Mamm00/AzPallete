package com.alazhartex.shadecart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.DatabaseSeeder
import com.alazhartex.shadecart.data.repo.MillInventoryRepository
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationAndInventoryTest {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AlAzharTexDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingV1DataAndBackfillsAvailableMeters() {
        val dbName = "azpallete-migration-test.db"

        // Create version 1 DB and seed legacy rows
        migrationTestHelper.createDatabase(dbName, 1).apply {
            insert(
                "fabric_types",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "fab-v1")
                    put("name", "Silk 180")
                    put("code", "SLK-180")
                    put("weight_gsm", 180)
                    put("width_cm", 150)
                    put("version", 1L)
                    put("updated_at", 1_700_000_000_000L)
                },
            )
            insert(
                "rolls",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "roll-v1")
                    put("roll_code", "RL-SLK-005")
                    put("fabric_type_id", "fab-v1")
                    put("shade_label", "5")
                    put("color_name", "Azhar Navy")
                    put("color_hex", "#102A71")
                    put("warehouse_shed", "wh-alwa")
                    put("length_meters", 480.0)
                    put("status", "IN_STOCK")
                    put("version", 1L)
                    put("updated_at", 1_700_000_000_000L)
                },
            )
            insert(
                "shade_carts",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "cart-v1")
                    put("fabric_type_id", "fab-v1")
                    put("fabric_name", "Silk 180")
                    put("fabric_code", "SLK-180")
                    put("weight_gsm", 180)
                    put("width_cm", 150)
                    put("edit_stack_json", "{}")
                    put("header_ocr_raw", "")
                    put("sync_status", "SYNCED")
                    put("version", 1L)
                    put("last_synced_version", 1L)
                    put("updated_at", 1_700_000_000_000L)
                },
            )
            insert(
                "swatches",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", "sw-v1")
                    put("shade_cart_id", "cart-v1")
                    put("fabric_type_id", "fab-v1")
                    put("roll_id", "roll-v1")
                    put("swatch_number", "5")
                    put("color_hex", "#102A71")
                    put("color_name", "Azhar Navy")
                    put("position_order", 0)
                    put("bbox_left", 0.1f)
                    put("bbox_top", 0.2f)
                    put("bbox_right", 0.4f)
                    put("bbox_bottom", 0.3f)
                    put("is_overridden", 0)
                    put("version", 1L)
                    put("updated_at", 1_700_000_000_000L)
                },
            )
            close()
        }

        // Migrate v1 -> v2 and validate schema + preserved & backfilled rows
        val migratedDb = migrationTestHelper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            AlAzharTexDatabase.MIGRATION_1_2,
        )

        migratedDb.query("SELECT available_meters, roll_code FROM rolls WHERE id = 'roll-v1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(480.0, c.getDouble(0), 0.001)
            assertEquals("RL-SLK-005", c.getString(1))
        }
        migratedDb.query("SELECT weave_tag, currency_code FROM swatches WHERE id = 'sw-v1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("MATTE", c.getString(0))
            assertEquals("EGP", c.getString(1))
        }
        migratedDb.close()
    }

    @Test
    fun millInventoryRepository_transactionsManageRollsLotsReservationsAndCuts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlAzharTexDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val seeder = DatabaseSeeder(
                fabricTypeDao = db.fabricTypeDao(),
                rollDao = db.rollDao(),
                dyeLotDao = db.dyeLotDao(),
                priceTierDao = db.priceTierDao(),
            )
            seeder.ensureSeeded()

            val repo = MillInventoryRepository(db, Dispatchers.IO)
            val createdRoll = repo.createRoll(
                WarehouseRollModel(
                    id = "roll-test-99",
                    rollCode = "RL-TST-099",
                    fabricTypeId = "fab-silk-180",
                    shadeLabel = "99",
                    colorName = "Nile Teal",
                    colorHex = "#187480",
                    warehouseShed = "wh-alwa",
                    lengthMeters = 100.0,
                    availableMeters = 100.0,
                    lotCode = "LOT-99A",
                ),
            ).getOrThrow()
            assertEquals("azpallete://roll/RL-TST-099", createdRoll.qrPayload)

            val lot = repo.createDyeLot(
                DyeLotModel(
                    id = "lot-test-1",
                    rollId = createdRoll.id,
                    lotCode = "LOT-99B",
                    dyeDate = System.currentTimeMillis(),
                    masterDeltaE = 1.4,
                    operatorName = "Tariq",
                ),
            ).getOrThrow()
            assertEquals("LOT-99B", lot.lotCode)

            // Reserve 40m -> available drops to 60m
            val res = repo.reserveMeters(createdRoll.id, "sw-99", 40.0, "Tariq", "Buyer Hold").getOrThrow()
            assertEquals(60.0, db.rollDao().getById(createdRoll.id)!!.availableMeters, 0.01)

            // Release reservation -> available restores to 100m
            repo.releaseReservation(res.id).getOrThrow()
            assertEquals(100.0, db.rollDao().getById(createdRoll.id)!!.availableMeters, 0.01)

            // Log cut 65m -> available drops to 35m and status transitions to LOW_STOCK
            repo.logCut(createdRoll.id, "sw-99", 65.0, 2, "Tariq").getOrThrow()
            val afterCut = db.rollDao().getById(createdRoll.id)!!
            assertEquals(35.0, afterCut.availableMeters, 0.01)
            assertEquals("LOW_STOCK", afterCut.status)

            val lowRolls = repo.getLowStockRolls()
            assertTrue(lowRolls.any { it.id == createdRoll.id && it.status == RollStockStatus.LOW_STOCK })
            assertEquals(1, repo.observeCutLogs().first().size)
        } finally {
            db.close()
        }
    }
}
