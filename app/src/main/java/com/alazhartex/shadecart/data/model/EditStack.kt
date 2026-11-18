package com.alazhartex.shadecart.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CropAspectPreset(val label: String, val ratioX: Int?, val ratioY: Int?) {
    FREE("Free", null, null),
    A4_PORTRAIT("A4 Card (5:7)", 5, 7),
    SQUARE("Square (1:1)", 1, 1),
    FOUR_THREE("4:3", 4, 3),
}

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "NormalizedRect bounds must stay within [0, 1]"
        }
    }

    fun width(): Float = (right - left).coerceAtLeast(0f)
    fun height(): Float = (bottom - top).coerceAtLeast(0f)
}

@Serializable
data class EditStep(
    val id: String,
    val type: EditStepType,
    val description: String,
    val timestampMillis: Long,
    val kernelFraction: Float? = null,
)

@Serializable
enum class EditStepType {
    CROP,
    ASPECT_PRESET,
    ROTATION,
    BACKGROUND_REMOVAL,
    FLATTEN_LIGHTING,
    SWATCH_OVERLAY_EDIT,
}

/**
 * Non-destructive edit stack persisted alongside [com.alazhartex.shadecart.data.local.ShadeCartEntity].
 * The original captured/picked image file is never modified or overwritten.
 */
@Serializable
data class EditStack(
    val cropRect: NormalizedRect? = null,
    val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
    val backgroundRemoved: Boolean = false,
    val segmentationMaskPath: String? = null,
    val rotationDegrees: Int = 0,
    val flattenLighting: Boolean = false,
    val lightingKernelFraction: Float = 0.10f,
    val history: List<EditStep> = emptyList(),
)
