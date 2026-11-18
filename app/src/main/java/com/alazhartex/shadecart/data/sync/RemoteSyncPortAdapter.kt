package com.alazhartex.shadecart.data.sync

import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.alazhartex.shadecart.domain.model.RemoteSyncPacket
import com.alazhartex.shadecart.domain.model.RemoteSyncPacketResult
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.repository.RemoteSyncPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E3: Concrete offline-first adapter bridging [RemoteSyncPort] (carts, dye lots, reservations,
 * and cut logs) onto [ShadeCartSyncAdapter] plus an in-memory multi-entity staging log ready to be
 * swapped with Firestore / Supabase / Cloudflare Workers without UI or UseCase changes.
 */
@Singleton
class RemoteSyncPortAdapter @Inject constructor(
    private val syncAdapter: ShadeCartSyncAdapter,
) : RemoteSyncPort {

    private val mutex = Mutex()
    private val packetLog = mutableListOf<RemoteSyncPacket>()

    override suspend fun isConnected(): Boolean = syncAdapter.checkConnection()

    override suspend fun pushPacket(packet: RemoteSyncPacket): Result<RemoteSyncPacketResult> =
        mutex.withLock {
            runCatching {
                if (!syncAdapter.checkConnection()) {
                    return@runCatching RemoteSyncPacketResult.TransientFailure("Sync adapter offline")
                }
                packetLog.add(packet)
                val cart = packet.cart
                if (cart != null) {
                    val snapshot = cart.toSnapshot()
                    when (val res = syncAdapter.pushCart(snapshot, maxOf(0L, cart.lastSyncedVersion))) {
                        is SyncPushResult.Accepted ->
                            RemoteSyncPacketResult.Accepted(res.newRemoteVersion)
                        is SyncPushResult.VersionConflict ->
                            RemoteSyncPacketResult.Conflict(res.remoteSnapshot.version)
                        is SyncPushResult.TransientFailure ->
                            RemoteSyncPacketResult.TransientFailure(res.message)
                    }
                } else {
                    RemoteSyncPacketResult.Accepted(remoteVersion = System.currentTimeMillis())
                }
            }
        }

    override suspend fun pullDeltaSince(sinceTimestampMillis: Long): Result<List<RemoteSyncPacket>> =
        mutex.withLock {
            runCatching {
                packetLog.filter { it.timestampMillis > sinceTimestampMillis }
            }
        }

    private fun ShadeCartModel.toSnapshot(): ShadeCartSnapshot = ShadeCartSnapshot(
        id = id,
        fabricTypeId = fabricTypeId,
        fabricName = fabricName,
        fabricCode = fabricCode,
        weightGsm = weightGsm,
        widthCm = widthCm,
        originalImagePath = originalImagePath,
        editedImagePath = editedImagePath,
        editStackJson = editStackJson,
        headerOcrRaw = headerOcrRaw,
        version = version,
        calibrationProfileId = calibrationProfileId,
        ambientLux = ambientLux,
        qrDeepLink = qrDeepLink,
        isArchived = isArchived,
        swatches = swatches.map { sw ->
            SwatchDraft(
                id = sw.id,
                fabricTypeId = sw.fabricTypeId,
                rollId = sw.rollId,
                swatchNumber = sw.swatchNumber,
                colorHex = sw.colorHex,
                colorName = sw.colorName,
                positionOrder = sw.positionOrder,
                bboxLeft = sw.bboxLeft,
                bboxTop = sw.bboxTop,
                bboxRight = sw.bboxRight,
                bboxBottom = sw.bboxBottom,
                isOverridden = sw.isOverridden,
                labL = sw.labL,
                labA = sw.labA,
                labB = sw.labB,
                weaveTag = sw.weaveTag.name,
                sheenScore = sw.sheenScore,
                pricePerMeter = sw.pricePerMeter,
                currencyCode = sw.currencyCode,
                version = sw.version,
            )
        },
        updatedAt = updatedAt,
    )
}
