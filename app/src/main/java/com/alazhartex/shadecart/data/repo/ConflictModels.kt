package com.alazhartex.shadecart.data.repo

import kotlinx.serialization.Serializable

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

enum class ConflictSource {
    CONCURRENT_LOCAL_WRITE,
    REMOTE_SYNC_VERSION_MISMATCH,
}

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

data class SyncConflict(
    val cartId: String,
    val fabricName: String,
    val fabricCode: String,
    val localSnapshot: ShadeCartSnapshot,
    val remoteSnapshot: ShadeCartSnapshot,
    val source: ConflictSource,
    val detectedAt: Long,
)

sealed interface SaveResult {
    data class Success(
        val cartId: String,
        val newVersion: Long,
    ) : SaveResult

    data class Conflict(
        val conflict: SyncConflict,
    ) : SaveResult

    data class NotFound(
        val cartId: String,
    ) : SaveResult
}

data class SyncBatchReport(
    val processed: Int,
    val synced: Int,
    val conflicts: Int,
    val failed: Int,
)
