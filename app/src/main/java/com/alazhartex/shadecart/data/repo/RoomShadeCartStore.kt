package com.alazhartex.shadecart.data.repo

import androidx.annotation.WorkerThread
import androidx.room.withTransaction
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartEntity
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchEntity
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import com.alazhartex.shadecart.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomShadeCartStore @Inject constructor(
    private val database: AlAzharTexDatabase,
) : ShadeCartStore {

    private val cartDao = database.shadeCartDao()
    private val swatchDao = database.swatchDao()
    private val fabricTypeDao = database.fabricTypeDao()
    private val rollDao = database.rollDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun observeCartsWithDetails(): Flow<List<ShadeCartWithDetails>> =
        cartDao.observeAllWithDetails()

    override fun observeCartWithDetails(cartId: String): Flow<ShadeCartWithDetails?> =
        cartDao.observeByIdWithDetails(cartId)

    override fun observeFabricTypes(): Flow<List<FabricType>> =
        fabricTypeDao.observeAll()

    override fun observeRolls(): Flow<List<Roll>> =
        rollDao.observeAll()

    override fun observeRollsForFabric(fabricTypeId: String): Flow<List<Roll>> =
        rollDao.observeForFabric(fabricTypeId)

    override fun observeSyncQueue(): Flow<List<SyncQueueEntity>> =
        syncQueueDao.observeAll()

    override fun observeConflicts(): Flow<List<SyncQueueEntity>> =
        syncQueueDao.observeByStatus(SyncStatus.CONFLICT)

    @WorkerThread
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    @WorkerThread
    override suspend fun getCart(cartId: String): ShadeCartEntity? =
        cartDao.getById(cartId)

    @WorkerThread
    override suspend fun getCartWithDetails(cartId: String): ShadeCartWithDetails? =
        cartDao.getByIdWithDetails(cartId)

    @WorkerThread
    override suspend fun getSwatchesForCart(cartId: String): List<SwatchEntity> =
        swatchDao.getForCart(cartId)

    @WorkerThread
    override suspend fun upsertCart(cart: ShadeCartEntity) {
        cartDao.upsert(cart)
    }

    @WorkerThread
    override suspend fun replaceSwatches(cartId: String, swatches: List<SwatchEntity>) {
        swatchDao.deleteForCart(cartId)
        if (swatches.isNotEmpty()) {
            swatchDao.upsertAll(swatches)
        }
    }

    @WorkerThread
    override suspend fun deleteCart(cartId: String) {
        cartDao.deleteById(cartId)
    }

    @WorkerThread
    override suspend fun findFabricTypeById(id: String): FabricType? =
        fabricTypeDao.getById(id)

    @WorkerThread
    override suspend fun findFabricTypeByCode(code: String): FabricType? =
        fabricTypeDao.findByCode(code)

    @WorkerThread
    override suspend fun getAllFabricTypes(): List<FabricType> =
        fabricTypeDao.getAll()

    @WorkerThread
    override suspend fun upsertFabricType(fabricType: FabricType) {
        fabricTypeDao.upsert(fabricType)
    }

    @WorkerThread
    override suspend fun getRollsForFabric(fabricTypeId: String): List<Roll> =
        rollDao.getForFabric(fabricTypeId)

    @WorkerThread
    override suspend fun upsertRolls(rolls: List<Roll>) {
        rollDao.upsertAll(rolls)
    }

    @WorkerThread
    override suspend fun getPendingSyncItems(): List<SyncQueueEntity> =
        syncQueueDao.getPendingItems()

    @WorkerThread
    override suspend fun getConflictForEntity(entityId: String): SyncQueueEntity? =
        syncQueueDao.getConflictForEntity(entityId)

    @WorkerThread
    override suspend fun upsertSyncQueueItem(item: SyncQueueEntity) {
        syncQueueDao.upsert(item)
    }

    @WorkerThread
    override suspend fun deletePendingSyncItems(entityId: String) {
        syncQueueDao.deletePendingForEntity(entityId)
    }

    @WorkerThread
    override suspend fun deleteConflictSyncItems(entityId: String) {
        syncQueueDao.deleteConflictsForEntity(entityId)
    }
}
