package com.alazhartex.shadecart.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FabricType::class,
        Roll::class,
        ShadeCartEntity::class,
        SwatchEntity::class,
        SyncQueueEntity::class,
        DyeLotEntity::class,
        ReservationEntity::class,
        CutLogEntity::class,
        PriceTierEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AlAzharTexDatabase : RoomDatabase() {
    abstract fun fabricTypeDao(): FabricTypeDao
    abstract fun rollDao(): RollDao
    abstract fun shadeCartDao(): ShadeCartDao
    abstract fun swatchDao(): SwatchDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun dyeLotDao(): DyeLotDao
    abstract fun reservationDao(): ReservationDao
    abstract fun cutLogDao(): CutLogDao
    abstract fun priceTierDao(): PriceTierDao

    companion object {
        /**
         * Additive v1 -> v2 Room migration (§F):
         * - Preserves existing `fabric_types`, `rolls`, `shade_carts`, `swatches`, `sync_queue`
         * - Appends v2 columns to `shade_carts`, `swatches`, and `rolls`
         * - Backfills `available_meters = length_meters` on existing rolls
         * - Creates `lots`, `reservations`, `cut_log`, and `price_tiers` tables + indices.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shade_carts ADD COLUMN calibration_profile_id TEXT")
                db.execSQL("ALTER TABLE shade_carts ADD COLUMN ambient_lux REAL NOT NULL DEFAULT -1.0")
                db.execSQL("ALTER TABLE shade_carts ADD COLUMN qr_deep_link TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shade_carts ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE swatches ADD COLUMN lab_l REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE swatches ADD COLUMN lab_a REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE swatches ADD COLUMN lab_b REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE swatches ADD COLUMN weave_tag TEXT NOT NULL DEFAULT 'MATTE'")
                db.execSQL("ALTER TABLE swatches ADD COLUMN sheen_score REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE swatches ADD COLUMN price_per_meter REAL")
                db.execSQL("ALTER TABLE swatches ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'EGP'")

                db.execSQL("ALTER TABLE rolls ADD COLUMN available_meters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE rolls SET available_meters = length_meters")
                db.execSQL("ALTER TABLE rolls ADD COLUMN lot_code TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rolls ADD COLUMN low_stock_threshold_meters REAL NOT NULL DEFAULT 50.0")
                db.execSQL("ALTER TABLE rolls ADD COLUMN qr_payload TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lots (
                        id TEXT NOT NULL PRIMARY KEY,
                        roll_id TEXT NOT NULL,
                        lot_code TEXT NOT NULL,
                        dye_date INTEGER NOT NULL,
                        master_delta_e REAL NOT NULL,
                        operator_name TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(roll_id) REFERENCES rolls(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lots_roll_id ON lots(roll_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lots_lot_code ON lots(lot_code)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reservations (
                        id TEXT NOT NULL PRIMARY KEY,
                        roll_id TEXT NOT NULL,
                        swatch_id TEXT NOT NULL,
                        meters_reserved REAL NOT NULL,
                        reserved_by TEXT NOT NULL,
                        note TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        FOREIGN KEY(roll_id) REFERENCES rolls(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reservations_roll_id ON reservations(roll_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reservations_status ON reservations(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cut_log (
                        id TEXT NOT NULL PRIMARY KEY,
                        roll_id TEXT NOT NULL,
                        swatch_id TEXT NOT NULL,
                        meters_cut REAL NOT NULL,
                        piece_count INTEGER NOT NULL,
                        operator_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(roll_id) REFERENCES rolls(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cut_log_roll_id ON cut_log(roll_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_tiers (
                        id TEXT NOT NULL PRIMARY KEY,
                        fabric_type_id TEXT NOT NULL,
                        tier_name TEXT NOT NULL,
                        min_meters REAL NOT NULL,
                        price_per_meter REAL NOT NULL,
                        currency_code TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(fabric_type_id) REFERENCES fabric_types(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_tiers_fabric_type_id ON price_tiers(fabric_type_id)")
            }
        }
    }
}
