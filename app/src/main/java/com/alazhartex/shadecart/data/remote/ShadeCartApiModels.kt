package com.alazhartex.shadecart.data.remote

import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.google.gson.annotations.SerializedName

/**
 * Retrofit/Gson data transfer object representing a single swatch on the remote mill API.
 *
 * Uses `@SerializedName` to remain resilient to R8 minification in release builds
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.1 & Chapter 12.1).
 */
data class RemoteSwatchDto(
    @SerializedName("id") val id: String,
    @SerializedName("fabric_type_id") val fabricTypeId: String = "",
    @SerializedName("roll_id") val rollId: String? = null,
    @SerializedName("swatch_number") val swatchNumber: String,
    @SerializedName("color_hex") val colorHex: String,
    @SerializedName("color_name") val colorName: String = "",
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
 * Mapped strictly at the data-layer boundary to [ShadeCartSnapshot] and domain models
 * (Pyre, Chapter 8.1 & Chapter 10.1).
 */
data class RemoteShadeCartDto(
    @SerializedName("id") val id: String,
    @SerializedName("fabric_type_id") val fabricTypeId: String? = null,
    @SerializedName("fabric_name") val fabricName: String,
    @SerializedName("fabric_code") val fabricCode: String,
    @SerializedName("weight_gsm") val weightGsm: Int,
    @SerializedName("width_cm") val widthCm: Int,
    @SerializedName("original_image_path") val originalImagePath: String? = null,
    @SerializedName("edited_image_path") val editedImagePath: String? = null,
    @SerializedName("edit_stack_json") val editStackJson: String = "{}",
    @SerializedName("header_ocr_raw") val headerOcrRaw: String = "",
    @SerializedName("version") val version: Long,
    @SerializedName("swatches") val swatches: List<RemoteSwatchDto> = emptyList(),
    @SerializedName("updated_at") val updatedAt: Long = 0L,
)

/**
 * Request envelope sent to `PUT carts/{cartId}` when pushing queued shade cart mutations.
 *
 * (Pyre, Chapter 8.1 & Chapter 8.2).
 */
data class RemotePushRequestDto(
    @SerializedName("expected_version") val expectedVersion: Long,
    @SerializedName("cart") val cart: RemoteShadeCartDto,
)

/**
 * Response payload returned by `PUT carts/{cartId}`, containing either the accepted server
 * version or the conflicting remote snapshot.
 *
 * (Pyre, Chapter 8.1 & Chapter 8.2).
 */
data class RemotePushResponseDto(
    @SerializedName("cart_id") val cartId: String,
    @SerializedName("accepted_version") val acceptedVersion: Long? = null,
    @SerializedName("remote_cart") val remoteCart: RemoteShadeCartDto? = null,
)

/** Maps a [RemoteShadeCartDto] wire response to an internal [ShadeCartSnapshot]. */
fun RemoteShadeCartDto.toSnapshot(): ShadeCartSnapshot = ShadeCartSnapshot(
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
