package com.alazhartex.shadecart.data.sync

import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot

/**
 * Pluggable sync adapter interface for Al AzharTex Digital Shade Cart.
 *
 * A future REST / Cloudflare / gRPC backend implements this interface and replaces
 * [StubShadeCartSyncAdapter] in Hilt DI without modifying repository or UI code.
 */
interface ShadeCartSyncAdapter {

    suspend fun checkConnection(): Boolean

    suspend fun pushCart(
        snapshot: ShadeCartSnapshot,
        expectedRemoteVersion: Long,
    ): SyncPushResult

    suspend fun pullCart(cartId: String): ShadeCartSnapshot?
}

sealed interface SyncPushResult {
    data class Accepted(
        val cartId: String,
        val newRemoteVersion: Long,
    ) : SyncPushResult

    data class VersionConflict(
        val cartId: String,
        val remoteSnapshot: ShadeCartSnapshot,
    ) : SyncPushResult

    data class TransientFailure(
        val message: String,
    ) : SyncPushResult
}
