package com.alazhartex.shadecart.data.sync

import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory deterministic stub for [ShadeCartSyncAdapter].
 *
 * Keeps an isolated remote snapshot table and supports simulating a remote version conflict
 * so QA and automated tests can verify optimistic locking & conflict resolution without a live backend.
 */
@Singleton
class StubShadeCartSyncAdapter @Inject constructor() : ShadeCartSyncAdapter {

    private val mutex = Mutex()
    private val remoteStore = ConcurrentHashMap<String, ShadeCartSnapshot>()
    private val simulateConflictNext = AtomicBoolean(false)
    private val online = AtomicBoolean(true)

    fun armSimulatedConflict() {
        simulateConflictNext.set(true)
    }

    fun isConflictArmed(): Boolean = simulateConflictNext.get()

    fun setOnline(isOnline: Boolean) {
        online.set(isOnline)
    }

    override suspend fun checkConnection(): Boolean = online.get()

    override suspend fun pushCart(
        snapshot: ShadeCartSnapshot,
        expectedRemoteVersion: Long,
    ): SyncPushResult = mutex.withLock {
        if (!online.get()) {
            return@withLock SyncPushResult.TransientFailure("Offline mode active in sync stub")
        }

        if (simulateConflictNext.compareAndSet(true, false)) {
            val remoteVersion = maxOf(expectedRemoteVersion + 1L, snapshot.version + 1L)
            val remoteSwatches = snapshot.swatches.mapIndexed { idx, sw ->
                if (idx == 0) {
                    sw.copy(
                        colorHex = "#0F5132",
                        colorName = "${sw.colorName.ifBlank { "Shade ${sw.swatchNumber}" }} (Remote Mill)",
                        version = sw.version + 1L,
                    )
                } else {
                    sw.copy(version = sw.version + 1L)
                }
            }
            val remoteCopy = snapshot.copy(
                fabricName = "${snapshot.fabricName} [Mill Rev v$remoteVersion]",
                version = remoteVersion,
                swatches = remoteSwatches,
                updatedAt = System.currentTimeMillis(),
            )
            remoteStore[snapshot.id] = remoteCopy
            return@withLock SyncPushResult.VersionConflict(
                cartId = snapshot.id,
                remoteSnapshot = remoteCopy,
            )
        }

        val existing = remoteStore[snapshot.id]
        if (existing != null && existing.version > expectedRemoteVersion && existing.version != snapshot.version) {
            return@withLock SyncPushResult.VersionConflict(
                cartId = snapshot.id,
                remoteSnapshot = existing,
            )
        }

        val acceptedVersion = maxOf(snapshot.version, (existing?.version ?: 0L) + 1L)
        val stored = snapshot.copy(version = acceptedVersion, updatedAt = System.currentTimeMillis())
        remoteStore[snapshot.id] = stored
        SyncPushResult.Accepted(cartId = snapshot.id, newRemoteVersion = acceptedVersion)
    }

    override suspend fun pullCart(cartId: String): ShadeCartSnapshot? = mutex.withLock {
        remoteStore[cartId]
    }
}
