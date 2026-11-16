package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartEntity
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchEntity
import com.alazhartex.shadecart.data.local.SwatchWithRoll
import com.alazhartex.shadecart.data.local.SyncQueueEntity
import com.alazhartex.shadecart.data.local.SyncStatus
import com.alazhartex.shadecart.data.sync.StubShadeCartSyncAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShadeCartRepositoryConflictTest {

    private lateinit var store: InMemoryShadeCartStore
    private lateinit var syncAdapter: StubShadeCartSyncAdapter
    private lateinit var repository: ShadeCartRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        store = InMemoryShadeCartStore()
        syncAdapter = StubShadeCartSyncAdapter()
        repository = ShadeCartRepository(
            store = store,
            syncAdapter = syncAdapter,
            syncScheduler = object : SyncWorkScheduler {
                override fun enqueueSync() = Unit
            },
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `stale expectedVersion triggers Conflict and KEEP_LOCAL resolves at higher version`() = runTest {
        val created = repository.createOrGetDraftCart(
            ShadeCartDraft(
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
                swatches = listOf(
                    SwatchDraft(
                        id = "sw-1",
                        fabricTypeId = "fab-1",
                        swatchNumber = "5",
                        colorHex = "#102A71",
                        colorName = "Azhar Navy",
                        positionOrder = 0,
                    ),
                ),
            ),
        ) as SaveResult.Success

        // First valid save bumps version 1 -> 2
        val validUpdate = repository.updateSwatchesWithOptimisticLock(
            cartId = created.cartId,
            expectedCartVersion = 1L,
            swatches = listOf(
                SwatchDraft(
                    id = "sw-1",
                    fabricTypeId = "fab-1",
                    swatchNumber = "5",
                    colorHex = "#0F5132",
                    colorName = "Emerald",
                    positionOrder = 0,
                ),
            ),
        )
        assertTrue(validUpdate is SaveResult.Success)
        assertEquals(2L, (validUpdate as SaveResult.Success).newVersion)

        // Second writer attempts stale expectedVersion = 1 -> Conflict
        val staleWrite = repository.updateSwatchesWithOptimisticLock(
            cartId = created.cartId,
            expectedCartVersion = 1L,
            swatches = listOf(
                SwatchDraft(
                    id = "sw-1",
                    fabricTypeId = "fab-1",
                    swatchNumber = "5",
                    colorHex = "#CD1820",
                    colorName = "Crimson",
                    positionOrder = 0,
                ),
            ),
        )
        assertTrue(staleWrite is SaveResult.Conflict)

        val activeConflicts = repository.observeConflicts().first()
        assertEquals(1, activeConflicts.size)
        assertEquals(ConflictSource.CONCURRENT_LOCAL_WRITE, activeConflicts.first().source)
        assertEquals("#CD1820", activeConflicts.first().localSnapshot.swatches.first().colorHex)
        assertEquals("#0F5132", activeConflicts.first().remoteSnapshot.swatches.first().colorHex)

        // Keep Local wins and increments version to 3
        val resolved = repository.resolveConflict(created.cartId, ConflictResolution.KEEP_LOCAL)
        assertTrue(resolved is SaveResult.Success)
        assertTrue(repository.observeConflicts().first().isEmpty())

        val persisted = repository.observeCart(created.cartId).first()!!
        assertEquals(3L, persisted.cart.version)
        assertEquals("#CD1820", persisted.swatches.first().swatch.colorHex)
        assertEquals(SyncStatus.PENDING, persisted.cart.syncStatus)
    }

    @Test
    fun `KEEP_REMOTE resolution restores stored snapshot and clears conflict`() = runTest {
        val created = repository.createOrGetDraftCart(
            ShadeCartDraft(
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
                swatches = listOf(
                    SwatchDraft(
                        id = "sw-7",
                        fabricTypeId = "fab-1",
                        swatchNumber = "7",
                        colorHex = "#D4A017",
                        colorName = "Sand Gold",
                        positionOrder = 0,
                    ),
                ),
            ),
        ) as SaveResult.Success

        // Bump v1 -> v2
        repository.updateSwatchesWithOptimisticLock(
            cartId = created.cartId,
            expectedCartVersion = 1L,
            swatches = listOf(
                SwatchDraft(
                    id = "sw-7",
                    fabricTypeId = "fab-1",
                    swatchNumber = "7",
                    colorHex = "#102A71",
                    colorName = "Navy",
                    positionOrder = 0,
                ),
            ),
        )

        // Conflict with stale expectedVersion = 1
        repository.updateSwatchesWithOptimisticLock(
            cartId = created.cartId,
            expectedCartVersion = 1L,
            swatches = listOf(
                SwatchDraft(
                    id = "sw-7",
                    fabricTypeId = "fab-1",
                    swatchNumber = "99",
                    colorHex = "#CD1820",
                    colorName = "Crimson",
                    positionOrder = 0,
                ),
            ),
        )

        val resolved = repository.resolveConflict(created.cartId, ConflictResolution.KEEP_REMOTE)
        assertTrue(resolved is SaveResult.Success)
        assertTrue(repository.observeConflicts().first().isEmpty())

        val persisted = repository.observeCart(created.cartId).first()!!
        assertEquals("7", persisted.swatches.first().swatch.swatchNumber)
        assertEquals("#102A71", persisted.swatches.first().swatch.colorHex)
    }

    @Test
    fun `remote sync version mismatch raises conflict and preserves local until user decides`() = runTest {
        val created = repository.createOrGetDraftCart(
            ShadeCartDraft(
                fabricName = "Cotton Poplin 130",
                fabricCode = "CTN-130",
                weightGsm = 130,
                widthCm = 150,
                swatches = listOf(
                    SwatchDraft(
                        id = "sw-12",
                        fabricTypeId = "fab-ctn",
                        swatchNumber = "12",
                        colorHex = "#761D3A",
                        colorName = "Burgundy",
                        positionOrder = 0,
                    ),
                ),
            ),
        ) as SaveResult.Success

        syncAdapter.armSimulatedConflict()
        val outcome = repository.syncPendingQueue()
        assertEquals(0, outcome.synced)
        assertEquals(1, outcome.conflicts)

        val conflicts = repository.observeConflicts().first()
        assertEquals(1, conflicts.size)
        assertEquals(ConflictSource.REMOTE_SYNC_VERSION_MISMATCH, conflicts.first().source)
        assertTrue(conflicts.first().remoteSnapshot.version > conflicts.first().localSnapshot.version)

        // Keeping local bumps version past remote so subsequent sync succeeds
        assertTrue(repository.resolveConflict(created.cartId, ConflictResolution.KEEP_LOCAL) is SaveResult.Success)
        val followUp = repository.syncPendingQueue()
        assertEquals(1, followUp.synced)
        assertEquals(0, followUp.conflicts)
    }

    @Test
    fun `concurrent updates serialized by Mutex never lose an update silently`() = runTest {
        val created = repository.createOrGetDraftCart(
            ShadeCartDraft(
                fabricName = "Linen 210",
                fabricCode = "LIN-210",
                weightGsm = 210,
                widthCm = 145,
                swatches = listOf(
                    SwatchDraft(
                        id = "sw-lin-1",
                        fabricTypeId = "fab-lin",
                        swatchNumber = "1",
                        colorHex = "#112233",
                        colorName = "Slate",
                        positionOrder = 0,
                    ),
                ),
            ),
        ) as SaveResult.Success

        store.simulatedIoDelayMillis = 15L

        val results = coroutineScope {
            listOf(
                async {
                    repository.updateSwatchesWithOptimisticLock(
                        cartId = created.cartId,
                        expectedCartVersion = 1L,
                        swatches = listOf(
                            SwatchDraft(
                                id = "sw-lin-1",
                                fabricTypeId = "fab-lin",
                                swatchNumber = "1",
                                colorHex = "#AA1122",
                                colorName = "Writer A",
                                positionOrder = 0,
                            ),
                        ),
                    )
                },
                async {
                    repository.updateSwatchesWithOptimisticLock(
                        cartId = created.cartId,
                        expectedCartVersion = 1L,
                        swatches = listOf(
                            SwatchDraft(
                                id = "sw-lin-1",
                                fabricTypeId = "fab-lin",
                                swatchNumber = "1",
                                colorHex = "#22AA11",
                                colorName = "Writer B",
                                positionOrder = 0,
                            ),
                        ),
                    )
                },
            ).awaitAll()
        }

        val successes = results.filterIsInstance<SaveResult.Success>()
        val conflicts = results.filterIsInstance<SaveResult.Conflict>()
        assertEquals(1, successes.size)
        assertEquals(1, conflicts.size)
        assertEquals(1, repository.observeConflicts().first().size)
    }
}

private class InMemoryShadeCartStore : ShadeCartStore {

    var simulatedIoDelayMillis: Long = 0L

    private val fabrics = linkedMapOf<String, FabricType>()
    private val rolls = linkedMapOf<String, Roll>()
    private val carts = linkedMapOf<String, ShadeCartEntity>()
    private val swatchesByCart = linkedMapOf<String, MutableList<SwatchEntity>>()
    private val queue = linkedMapOf<String, SyncQueueEntity>()
    private val versionTick = MutableStateFlow(0L)

    override fun observeCartsWithDetails(): Flow<List<ShadeCartWithDetails>> = versionTick.map {
        carts.values.sortedByDescending { it.updatedAt }.mapNotNull { cart -> buildDetails(cart.id) }
    }

    override fun observeCartWithDetails(cartId: String): Flow<ShadeCartWithDetails?> = versionTick.map {
        buildDetails(cartId)
    }

    override fun observeFabricTypes(): Flow<List<FabricType>> = versionTick.map {
        fabrics.values.sortedBy { it.name }
    }

    override fun observeRolls(): Flow<List<Roll>> = versionTick.map {
        rolls.values.sortedBy { it.rollCode }
    }

    override fun observeRollsForFabric(fabricTypeId: String): Flow<List<Roll>> = versionTick.map {
        rolls.values.filter { it.fabricTypeId == fabricTypeId }.sortedBy { it.rollCode }
    }

    override fun observeSyncQueue(): Flow<List<SyncQueueEntity>> = versionTick.map {
        queue.values.sortedByDescending { it.createdAt }
    }

    override fun observeConflicts(): Flow<List<SyncQueueEntity>> = versionTick.map {
        queue.values.filter { it.status == SyncStatus.CONFLICT }.sortedByDescending { it.createdAt }
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        if (simulatedIoDelayMillis > 0L) delay(simulatedIoDelayMillis)
        return block()
    }

    override suspend fun getCart(cartId: String): ShadeCartEntity? = carts[cartId]

    override suspend fun getCartWithDetails(cartId: String): ShadeCartWithDetails? = buildDetails(cartId)

    override suspend fun getSwatchesForCart(cartId: String): List<SwatchEntity> =
        swatchesByCart[cartId].orEmpty().sortedBy { it.positionOrder }

    override suspend fun upsertCart(cart: ShadeCartEntity) {
        carts[cart.id] = cart
        versionTick.value++
    }

    override suspend fun replaceSwatches(cartId: String, swatches: List<SwatchEntity>) {
        swatchesByCart[cartId] = swatches.toMutableList()
        versionTick.value++
    }

    override suspend fun deleteCart(cartId: String) {
        carts.remove(cartId)
        swatchesByCart.remove(cartId)
        versionTick.value++
    }

    override suspend fun findFabricTypeById(id: String): FabricType? = fabrics[id]

    override suspend fun findFabricTypeByCode(code: String): FabricType? =
        fabrics.values.firstOrNull { it.code.equals(code, ignoreCase = true) }

    override suspend fun getAllFabricTypes(): List<FabricType> = fabrics.values.sortedBy { it.name }

    override suspend fun upsertFabricType(fabricType: FabricType) {
        fabrics[fabricType.id] = fabricType
        versionTick.value++
    }

    override suspend fun getRollsForFabric(fabricTypeId: String): List<Roll> =
        rolls.values.filter { it.fabricTypeId == fabricTypeId }

    override suspend fun upsertRolls(rolls: List<Roll>) {
        rolls.forEach { this.rolls[it.id] = it }
        versionTick.value++
    }

    override suspend fun getPendingSyncItems(): List<SyncQueueEntity> =
        queue.values.filter { it.status == SyncStatus.PENDING }.sortedBy { it.createdAt }

    override suspend fun getConflictForEntity(entityId: String): SyncQueueEntity? =
        queue.values.firstOrNull { it.entityId == entityId && it.status == SyncStatus.CONFLICT }

    override suspend fun upsertSyncQueueItem(item: SyncQueueEntity) {
        queue[item.id] = item
        versionTick.value++
    }

    override suspend fun deletePendingSyncItems(entityId: String) {
        val ids = queue.values.filter { it.entityId == entityId && it.status == SyncStatus.PENDING }.map { it.id }
        ids.forEach { queue.remove(it) }
        versionTick.value++
    }

    override suspend fun deleteConflictSyncItems(entityId: String) {
        val ids = queue.values.filter { it.entityId == entityId && it.status == SyncStatus.CONFLICT }.map { it.id }
        ids.forEach { queue.remove(it) }
        versionTick.value++
    }

    private fun buildDetails(cartId: String): ShadeCartWithDetails? {
        val cart = carts[cartId] ?: return null
        val fabric = cart.fabricTypeId?.let { fabrics[it] }
        val sw = swatchesByCart[cartId].orEmpty().sortedBy { it.positionOrder }
            .map { s -> SwatchWithRoll(swatch = s, roll = s.rollId?.let { rolls[it] }) }
        return ShadeCartWithDetails(cart = cart, fabricType = fabric, swatches = sw)
    }
}
