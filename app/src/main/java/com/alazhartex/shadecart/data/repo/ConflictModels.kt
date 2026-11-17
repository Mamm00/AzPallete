package com.alazhartex.shadecart.data.repo

import com.alazhartex.shadecart.domain.model.ConflictOrigin
import kotlinx.serialization.Serializable

/**
 * Serializable swatch draft payload persisted inside Room's `sync_queue` JSON column and
 * exchanged with [com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter]
 * (Pyre, Chapter 10.1 & Chapter 10.3).
 */
@Serializable
data class SwatchDraft(
    val id: String,
    val fabricTypeId: String,
    val rollId: String? = null,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String = "",
    val positionOrder: Int,
    val bboxLeft: Float = 0f,
    val bboxTop: Float = 0f,
    val bboxRight: Float = 0f,
    val bboxBottom: Float = 0f,
    val isOverridden: Boolean = false,
    val version: Long = 1L,
)

/**
 * Serializable full shade-cart snapshot stored in offline sync queue records and transmitted
 * during optimistic-locking sync operations (Pyre, Chapter 10.3).
 */
@Serializable
data class ShadeCartSnapshot(
    val id: String,
    val fabricTypeId: String?,
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int,
    val widthCm: Int,
    val originalImagePath: String?,
    val editedImagePath: String?,
    val editStackJson: String,
    val headerOcrRaw: String = "",
    val version: Long,
    val swatches: List<SwatchDraft>,
    val updatedAt: Long,
)

/**
 * Internal persistence record pairing conflicting local and remote [ShadeCartSnapshot] payloads
 * stored inside `sync_queue` until resolved by the operator.
 */
data class SyncConflictRecord(
    val cartId: String,
    val fabricName: String,
    val fabricCode: String,
    val localSnapshot: ShadeCartSnapshot,
    val remoteSnapshot: ShadeCartSnapshot,
    val source: ConflictOrigin,
    val detectedAt: Long,
)
