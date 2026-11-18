package com.alazhartex.shadecart.data.sync

import com.alazhartex.shadecart.data.remote.RemotePushRequestDto
import com.alazhartex.shadecart.data.remote.RemotePushResponseDto
import com.alazhartex.shadecart.data.remote.ShadeCartApiService
import com.alazhartex.shadecart.data.remote.SyncBaseUrl
import com.alazhartex.shadecart.data.remote.toRemoteDto
import com.alazhartex.shadecart.data.remote.toSnapshot
import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import com.alazhartex.shadecart.di.IoDispatcher
import com.alazhartex.shadecart.domain.repository.SyncDebugController
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit-backed implementation of [ShadeCartSyncAdapter] bridging offline Room mutations to
 * [ShadeCartApiService] over OkHttp + Gson.
 *
 * When [syncBaseUrl] is blank (the default offline-first distribution mode), safely delegates to
 * [StubShadeCartSyncAdapter] so local mill operations and QA conflict tools remain functional
 * without an external server (Pyre, *Android Development with Jetpack Compose & Kotlin*,
 * Chapter 8.1, 8.2, 8.3, & 10.2).
 */
@Singleton
class RetrofitShadeCartSyncAdapter @Inject constructor(
    private val apiService: ShadeCartApiService,
    private val stubAdapter: StubShadeCartSyncAdapter,
    private val gson: Gson,
    @SyncBaseUrl private val syncBaseUrl: String,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ShadeCartSyncAdapter, SyncDebugController {

    private val isRemoteConfigured: Boolean
        get() = syncBaseUrl.isNotBlank()

    override fun armSimulatedConflict() {
        stubAdapter.armSimulatedConflict()
    }

    override fun isConflictArmed(): Boolean = stubAdapter.isConflictArmed()

    override suspend fun checkConnection(): Boolean = withContext(ioDispatcher) {
        if (!isRemoteConfigured) {
            return@withContext stubAdapter.checkConnection()
        }
        try {
            apiService.checkHealth().isSuccessful
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    override suspend fun pushCart(
        snapshot: ShadeCartSnapshot,
        expectedRemoteVersion: Long,
    ): SyncPushResult = withContext(ioDispatcher) {
        if (!isRemoteConfigured) {
            return@withContext stubAdapter.pushCart(snapshot, expectedRemoteVersion)
        }
        try {
            val response = apiService.pushCart(
                cartId = snapshot.id,
                expectedVersion = expectedRemoteVersion,
                request = RemotePushRequestDto(
                    expectedVersion = expectedRemoteVersion,
                    cart = snapshot.toRemoteDto(),
                ),
            )
            when {
                response.isSuccessful -> {
                    val acceptedVersion = response.body()?.acceptedVersion ?: snapshot.version
                    SyncPushResult.Accepted(
                        cartId = snapshot.id,
                        newRemoteVersion = acceptedVersion,
                    )
                }

                response.code() == HTTP_CONFLICT -> {
                    val conflictSnapshot = parseConflictSnapshot(
                        errorJson = response.errorBody()?.string(),
                        cartId = snapshot.id,
                        fallbackSnapshot = snapshot,
                        expectedRemoteVersion = expectedRemoteVersion,
                    )
                    SyncPushResult.VersionConflict(
                        cartId = snapshot.id,
                        remoteSnapshot = conflictSnapshot,
                    )
                }

                else -> {
                    SyncPushResult.TransientFailure(
                        message = "Sync HTTP ${response.code()}: ${response.message()}",
                    )
                }
            }
        } catch (io: IOException) {
            SyncPushResult.TransientFailure(io.message ?: "Network I/O error during sync")
        } catch (ex: RuntimeException) {
            SyncPushResult.TransientFailure(ex.message ?: "Unexpected sync failure")
        }
    }

    override suspend fun pullCart(cartId: String): ShadeCartSnapshot? = withContext(ioDispatcher) {
        if (!isRemoteConfigured) {
            return@withContext stubAdapter.pullCart(cartId)
        }
        try {
            val response = apiService.getCart(cartId)
            if (response.isSuccessful) {
                response.body()?.toSnapshot()
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private suspend fun parseConflictSnapshot(
        errorJson: String?,
        cartId: String,
        fallbackSnapshot: ShadeCartSnapshot,
        expectedRemoteVersion: Long,
    ): ShadeCartSnapshot {
        val fromBody = if (!errorJson.isNullOrBlank()) {
            runCatching {
                gson.fromJson(errorJson, RemotePushResponseDto::class.java)?.remoteCart?.toSnapshot()
            }.getOrNull()
        } else {
            null
        }
        if (fromBody != null) return fromBody

        val pulled = pullCart(cartId)
        if (pulled != null) return pulled

        return fallbackSnapshot.copy(version = maxOf(fallbackSnapshot.version, expectedRemoteVersion) + 1L)
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
