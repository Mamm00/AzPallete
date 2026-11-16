package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartEntity
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchEntity
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for [ShadeCartRepository].
 * Backed by Room (`@Transaction` via `withTransaction`) in production and a lightweight
 * transactional store in JVM unit tests.
 */
interface ShadeCartStore {
    fun observeCartsWithDetails(): Flow<List<ShadeCartWithDetails>>
    fun observeCartWithDetails(cartId: String): Flow<ShadeCartWithDetails?>
    fun observeFabricTypes(): Flow<List<FabricType>>
    fun observeRolls(): Flow<List<Roll>>
    fun observeRollsForFabric(fabricTypeId: String): Flow<List<Roll>>
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>
    fun observeConflicts(): Flow<List<SyncQueueEntity>>

    suspend fun <T> runInTransaction(block: suspend () -> T): T

    suspend fun getCart(cartId: String): ShadeCartEntity?
    suspend fun getCartWithDetails(cartId: String): ShadeCartWithDetails?
    suspend fun getSwatchesForCart(cartId: String): List<SwatchEntity>
    suspend fun upsertCart(cart: ShadeCartEntity)
    suspend fun replaceSwatches(cartId: String, swatches: List<SwatchEntity>)
    suspend fun deleteCart(cartId: String)

    suspend fun findFabricTypeById(id: String): FabricType?
    suspend fun findFabricTypeByCode(code: String): FabricType?
    suspend fun getAllFabricTypes(): List<FabricType>
    suspend fun upsertFabricType(fabricType: FabricType)

    suspend fun getRollsForFabric(fabricTypeId: String): List<Roll>
    suspend fun upsertRolls(rolls: List<Roll>)

    suspend fun getPendingSyncItems(): List<SyncQueueEntity>
    suspend fun getConflictForEntity(entityId: String): SyncQueueEntity?
    suspend fun upsertSyncQueueItem(item: SyncQueueEntity)
    suspend fun deletePendingSyncItems(entityId: String)
    suspend fun deleteConflictSyncItems(entityId: String)
}
