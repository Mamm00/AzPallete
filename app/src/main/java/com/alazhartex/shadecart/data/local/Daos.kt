package com.alazhartex.shadecart.data.local

import androidx.annotation.WorkerThread
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

    @WorkerThread
    @Query("SELECT * FROM fabric_types ORDER BY name ASC")
    suspend fun getAll(): List<FabricType>

    @WorkerThread
    @Query("SELECT * FROM fabric_types WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FabricType?

    @WorkerThread
    @Query("SELECT * FROM fabric_types WHERE LOWER(code) = LOWER(:code) LIMIT 1")
    suspend fun findByCode(code: String): FabricType?

    @WorkerThread
    @Query("SELECT COUNT(*) FROM fabric_types")
    suspend fun count(): Int

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FabricType>)

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FabricType)
}

@Dao
interface RollDao {
    @Query("SELECT * FROM rolls ORDER BY roll_code ASC")
    fun observeAll(): Flow<List<Roll>>

    @Query("SELECT * FROM rolls WHERE fabric_type_id = :fabricTypeId ORDER BY roll_code ASC")
    fun observeForFabric(fabricTypeId: String): Flow<List<Roll>>

    @WorkerThread
    @Query("SELECT * FROM rolls WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Roll?

    @WorkerThread
    @Query("SELECT * FROM rolls WHERE fabric_type_id = :fabricTypeId")
    suspend fun getForFabric(fabricTypeId: String): List<Roll>

    @WorkerThread
    @Query("SELECT COUNT(*) FROM rolls")
    suspend fun count(): Int

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Roll>)
}

@Dao
interface ShadeCartDao {
    @Transaction
    @Query("SELECT * FROM shade_carts ORDER BY updated_at DESC")
    fun observeAllWithDetails(): Flow<List<ShadeCartWithDetails>>

    @Transaction
    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    fun observeByIdWithDetails(cartId: String): Flow<ShadeCartWithDetails?>

    @WorkerThread
    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    suspend fun getById(cartId: String): ShadeCartEntity?

    @WorkerThread
    @Transaction
    @Query("SELECT * FROM shade_carts WHERE id = :cartId LIMIT 1")
    suspend fun getByIdWithDetails(cartId: String): ShadeCartWithDetails?

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cart: ShadeCartEntity)

    @WorkerThread
    @Update
    suspend fun update(cart: ShadeCartEntity)

    @WorkerThread
    @Query("DELETE FROM shade_carts WHERE id = :cartId")
    suspend fun deleteById(cartId: String)
}

@Dao
interface SwatchDao {
    @WorkerThread
    @Query("SELECT * FROM swatches WHERE shade_cart_id = :cartId ORDER BY position_order ASC")
    suspend fun getForCart(cartId: String): List<SwatchEntity>

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(swatches: List<SwatchEntity>)

    @WorkerThread
    @Query("DELETE FROM swatches WHERE shade_cart_id = :cartId")
    suspend fun deleteForCart(cartId: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY updated_at DESC")
    fun observeByStatus(status: String): Flow<List<SyncQueueEntity>>

    @WorkerThread
    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @WorkerThread
    @Query("SELECT * FROM sync_queue WHERE entity_id = :entityId AND status = 'CONFLICT' LIMIT 1")
    suspend fun getConflictForEntity(entityId: String): SyncQueueEntity?

    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncQueueEntity)

    @WorkerThread
    @Query("DELETE FROM sync_queue WHERE entity_id = :entityId AND status = 'PENDING'")
    suspend fun deletePendingForEntity(entityId: String)

    @WorkerThread
    @Query("DELETE FROM sync_queue WHERE entity_id = :entityId AND status = 'CONFLICT'")
    suspend fun deleteConflictsForEntity(entityId: String)
}
