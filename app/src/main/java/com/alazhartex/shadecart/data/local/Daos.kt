package com.alazhartex.shadecart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FabricTypeDao {
    @Query("SELECT * FROM fabric_types ORDER BY name ASC")
    fun observeAll(): Flow<List<FabricType>>

    @Query("SELECT * FROM fabric_types ORDER BY name ASC")
    suspend fun getAll(): List<FabricType>

    @Query("SELECT * FROM fabric_types WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FabricType?

    @Query("SELECT * FROM fabric_types WHERE LOWER(code) = LOWER(:code) LIMIT 1")
    suspend fun findByCode(code: String): FabricType?

    @Query("SELECT COUNT(*) FROM fabric_types")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FabricType>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FabricType)
}

@Dao
interface RollDao {
    @Query("SELECT * FROM rolls ORDER BY roll_code ASC")
    fun observeAll(): Flow<List<Roll>>

    @Query("SELECT * FROM rolls WHERE fabric_type_id = :fabricTypeId ORDER BY roll_code ASC")
    fun observeForFabric(fabricTypeId: String): Flow<List<Roll>>

    @Query("SELECT * FROM rolls WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Roll?

    @Query("SELECT * FROM rolls WHERE roll_code = :rollCode LIMIT 1")
    suspend fun getByRollCode(rollCode: String): Roll?

    @Query("SELECT * FROM rolls WHERE fabric_type_id = :fabricTypeId")
    suspend fun getForFabric(fabricTypeId: String): List<Roll>

    @Query("SELECT * FROM rolls WHERE available_meters < low_stock_threshold_meters AND status != 'OUT_OF_STOCK'")
    suspend fun getLowStockRolls(): List<Roll>

    @Query("SELECT COUNT(*) FROM rolls")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Roll)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Roll>)
}

@Dao
interface ShadeCartDao {
    @Transaction
    @Query("SELECT * FROM shade_carts WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun observeAllWithDetails(): Flow<List<ShadeCartWithDetails>>

    @Transaction
    @Query("SELECT * FROM shade_carts ORDER BY updated_at DESC")
    suspend fun getAllWithDetailsIncludingArchived(): List<ShadeCartWithDetails>

    @Transaction
    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    fun observeByIdWithDetails(cartId: String): Flow<ShadeCartWithDetails?>

    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    suspend fun getById(cartId: String): ShadeCartEntity?

    @Transaction
    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    suspend fun getByIdWithDetails(cartId: String): ShadeCartWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cart: ShadeCartEntity)

    @Update
    suspend fun update(cart: ShadeCartEntity)

    @Query("DELETE FROM shade_carts WHERE id = :cartId")
    suspend fun deleteById(cartId: String)
}

@Dao
interface SwatchDao {
    @Transaction
    @Query("SELECT * FROM swatches ORDER BY shade_cart_id ASC, position_order ASC")
    fun observeAllWithRoll(): Flow<List<SwatchWithRoll>>

    @Query("SELECT * FROM swatches WHERE shade_cart_id = :cartId ORDER BY position_order ASC")
    suspend fun getForCart(cartId: String): List<SwatchEntity>

    @Query("SELECT * FROM swatches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SwatchEntity?

    @Query("UPDATE swatches SET price_per_meter = :pricePerMeter, updated_at = :updatedAt WHERE id = :swatchId")
    suspend fun updatePrice(swatchId: String, pricePerMeter: Double?, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(swatches: List<SwatchEntity>)

    @Query("DELETE FROM swatches WHERE shade_cart_id = :cartId")
    suspend fun deleteForCart(cartId: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY updated_at DESC")
    fun observeByStatus(status: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE entity_id = :entityId AND status = 'CONFLICT' LIMIT 1")
    suspend fun getConflictForEntity(entityId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE entity_id = :entityId AND status = 'PENDING'")
    suspend fun deletePendingForEntity(entityId: String)

    @Query("DELETE FROM sync_queue WHERE entity_id = :entityId AND status = 'CONFLICT'")
    suspend fun deleteConflictsForEntity(entityId: String)
}

@Dao
interface DyeLotDao {
    @Query("SELECT * FROM lots ORDER BY dye_date DESC")
    fun observeAll(): Flow<List<DyeLotEntity>>

    @Query("SELECT * FROM lots WHERE roll_id = :rollId ORDER BY dye_date DESC")
    fun observeForRoll(rollId: String): Flow<List<DyeLotEntity>>

    @Query("SELECT COUNT(*) FROM lots")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DyeLotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DyeLotEntity>)
}

@Dao
interface ReservationDao {
    @Query("SELECT * FROM reservations WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    fun observeActive(): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReservationEntity>)
}

@Dao
interface CutLogDao {
    @Query("SELECT * FROM cut_log ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CutLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CutLogEntity>)
}

@Dao
interface PriceTierDao {
    @Query("SELECT * FROM price_tiers ORDER BY min_meters ASC")
    fun observeAll(): Flow<List<PriceTierEntity>>

    @Query("SELECT COUNT(*) FROM price_tiers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PriceTierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PriceTierEntity>)
}
