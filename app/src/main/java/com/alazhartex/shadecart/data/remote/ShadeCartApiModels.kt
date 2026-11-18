package com.alazhartex.shadecart.data.remote

import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.google.gson.annotations.SerializedName

/**
 * Retrofit/Gson data transfer object representing a single swatch on the remote mill API.
 *
 * Uses nullable wire properties for optional JSON fields alongside `@SerializedName` so Gson's
 * reflection-based deserialization never injects `null` into non-null Kotlin constructor parameters
 * when fields are omitted in server payloads or stripped under R8
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.1 & Chapter 12.1).
 */
data class RemoteSwatchDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("fabric_type_id") val fabricTypeId: String? = null,
    @SerializedName("roll_id") val rollId: String? = null,
    @SerializedName("swatch_number") val swatchNumber: String? = null,
    @SerializedName("color_hex") val colorHex: String? = null,
    @SerializedName("color_name") val colorName: String? = null,
    @SerializedName("position_order") val positionOrder: Int = 0,
    @SerializedName("bbox_left") val bboxLeft: Float = 0f,
    @SerializedName("bbox_top") val bboxTop: Float = 0f,
    @SerializedName("bbox_right") val bboxRight: Float = 0f,
    @SerializedName("bbox_bottom") val bboxBottom: Float = 0f,
    @SerializedName("is_overridden") val isOverridden: Boolean = false,
    @SerializedName("version") val version: Long = 1L,
)

/**
 * Retrofit/Gson data transfer object representing a full shade cart payload over HTTPS.
 *
 * Mapped defensively at the data-layer boundary to [ShadeCartSnapshot] and domain models
 * (Pyre, Chapter 8.1 & 10.1).
 */
data class RemoteShadeCartDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("fabric_type_id") val fabricTypeId: String? = null,
    @SerializedName("fabric_name") val fabricName: String? = null,
    @SerializedName("fabric_code") val fabricCode: String? = null,
    @SerializedName("weight_gsm") val weightGsm: Int = 180,
    @SerializedName("width_cm") val widthCm: Int = 150,
    @SerializedName("original_image_path") val originalImagePath: String? = null,
    @SerializedName("edited_image_path") val editedImagePath: String? = null,
    @SerializedName("edit_stack_json") val editStackJson: String? = null,
    @SerializedName("header_ocr_raw") val headerOcrRaw: String? = null,
    @SerializedName("version") val version: Long = 1L,
    @SerializedName("swatches") val swatches: List<RemoteSwatchDto>? = null,
    @SerializedName("updated_at") val updatedAt: Long = 0L,
)

/**
 * Request envelope sent to `PUT carts/{cartId}` when pushing queued shade cart mutations.
 *
 * (Pyre, Chapter 8.1 & 8.2).
 */
data class RemotePushRequestDto(
    @SerializedName("expected_version") val expectedVersion: Long,
    @SerializedName("cart") val cart: RemoteShadeCartDto,
)

/**
 * Response payload returned by `PUT carts/{cartId}`, containing either the accepted server
 * version or the conflicting remote snapshot.
 *
 * (Pyre, Chapter 8.1 & 8.2).
 */
data class RemotePushResponseDto(
    @SerializedName("cart_id") val cartId: String? = null,
    @SerializedName("accepted_version") val acceptedVersion: Long? = null,
    @SerializedName("remote_cart") val remoteCart: RemoteShadeCartDto? = null,
)

/** Maps a [RemoteShadeCartDto] wire response to a null-safe internal [ShadeCartSnapshot]. */
fun RemoteShadeCartDto.toSnapshot(): ShadeCartSnapshot = ShadeCartSnapshot(
    id = id.orEmpty(),
    fabricTypeId = fabricTypeId,
    fabricName = fabricName.orEmpty(),
    fabricCode = fabricCode.orEmpty(),
    weightGsm = weightGsm,
    widthCm = widthCm,
    originalImagePath = originalImagePath,
    editedImagePath = editedImagePath,
    editStackJson = editStackJson?.takeIf { it.isNotBlank() } ?: "{}",
    headerOcrRaw = headerOcrRaw.orEmpty(),
    version = version,
    swatches = swatches.orEmpty().map { sw ->
        SwatchDraft(
            id = sw.id.orEmpty(),
            fabricTypeId = sw.fabricTypeId.orEmpty(),
            rollId = sw.rollId,
            swatchNumber = sw.swatchNumber.orEmpty(),
            colorHex = sw.colorHex.orEmpty(),
            colorName = sw.colorName.orEmpty(),
            positionOrder = sw.positionOrder,
            bboxLeft = sw.bboxLeft,
            bboxTop = sw.bboxTop,
            bboxRight = sw.bboxRight,
            bboxBottom = sw.bboxBottom,
            isOverridden = sw.isOverridden,
            version = sw.version,
        )
    },
    updatedAt = updatedAt,
)

/** Maps an internal [ShadeCartSnapshot] to a Gson-serializable [RemoteShadeCartDto]. */
fun ShadeCartSnapshot.toRemoteDto(): RemoteShadeCartDto = RemoteShadeCartDto(
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
    swatches = swatches.map { sw ->
        RemoteSwatchDto(
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
            version = sw.version,
        )
    },
    updatedAt = updatedAt,
)
