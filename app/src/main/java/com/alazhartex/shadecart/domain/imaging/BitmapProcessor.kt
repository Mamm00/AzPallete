package com.alazhartex.shadecart.domain.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.alazhartex.shadecart.data.model.EditStack
import com.alazhartex.shadecart.data.model.NormalizedRect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Memory-conscious bitmap pipeline for Al AzharTex Digital Shade Cart.
 *
 * Enforces:
 * - Max 2048 px on the long edge for every stored/processed image.
 * - `RGB_565` configuration for display previews (half the heap footprint of ARGB_8888).
 * - Prompt recycling of intermediate bitmaps.
 * - Strict off-main-thread execution (`@WorkerThread` + [ioDispatcher]).
 */
class BitmapProcessor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val MAX_LONG_EDGE_PX = 2048
        const val PREVIEW_MAX_EDGE_PX = 1280
    }

    private fun capturesDir(): File =
        File(context.filesDir, "captures").apply { mkdirs() }

    private fun editedDir(): File =
        File(context.filesDir, "edited").apply { mkdirs() }

    fun newTempCaptureFile(extension: String = "jpg"): File =
        File(capturesDir(), "capture_${UUID.randomUUID()}.$extension")

    fun editedPreviewFile(sessionId: String): File =
        File(editedDir(), "edited_$sessionId.png")

    fun maskFile(sessionId: String): File =
        File(editedDir(), "mask_$sessionId.png")

    /**
     * Imports a user-selected photo URI into app-private storage, applies EXIF orientation,
     * and downsamples so its long edge never exceeds [MAX_LONG_EDGE_PX] (2048 px).
     */
    @WorkerThread
    suspend fun importUriToBoundedOriginal(uri: Uri): String = withContext(ioDispatcher) {
        val tempRaw = File(context.cacheDir, "raw_${UUID.randomUUID()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRaw).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open selected image URI: $uri")

            val dest = File(capturesDir(), "orig_${UUID.randomUUID()}.jpg")
            downsampleAndSaveJpeg(
                sourcePath = tempRaw.absolutePath,
                destFile = dest,
                maxLongEdge = MAX_LONG_EDGE_PX,
            )
            dest.absolutePath
        } finally {
            tempRaw.delete()
        }
    }

    /**
     * Normalizes an already captured CameraX temp file in-place (or to a permanent original copy)
     * capped at [MAX_LONG_EDGE_PX] (2048 px).
     */
    @WorkerThread
    suspend fun finalizeCapturedTempFile(tempPath: String): String = withContext(ioDispatcher) {
        val dest = File(capturesDir(), "orig_${UUID.randomUUID()}.jpg")
        downsampleAndSaveJpeg(
            sourcePath = tempPath,
            destFile = dest,
            maxLongEdge = MAX_LONG_EDGE_PX,
        )
        if (tempPath != dest.absolutePath) {
            File(tempPath).delete()
        }
        dest.absolutePath
    }

    /**
     * Copies a bundled asset (such as `sample_shade_cart.png`) into app-private capture storage,
     * downsampling if needed to <= 2048 px.
     */
    @WorkerThread
    suspend fun importAssetToBoundedOriginal(assetName: String): String = withContext(ioDispatcher) {
        val tempRaw = File(context.cacheDir, "asset_${UUID.randomUUID()}.tmp")
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(tempRaw).use { output ->
                    input.copyTo(output)
                }
            }
            val dest = File(capturesDir(), "orig_${UUID.randomUUID()}.jpg")
            downsampleAndSaveJpeg(
                sourcePath = tempRaw.absolutePath,
                destFile = dest,
                maxLongEdge = MAX_LONG_EDGE_PX,
            )
            dest.absolutePath
        } finally {
            tempRaw.delete()
        }
    }

    @WorkerThread
    suspend fun loadRgb565Preview(
        path: String,
        maxLongEdge: Int = PREVIEW_MAX_EDGE_PX,
    ): Bitmap? = withContext(ioDispatcher) {
        if (!File(path).exists()) return@withContext null
        decodeDownsampledBitmap(
            path = path,
            maxLongEdge = maxLongEdge,
            config = Bitmap.Config.RGB_565,
        )
    }

    @WorkerThread
    suspend fun loadProcessingBitmap(
        path: String,
        maxLongEdge: Int = MAX_LONG_EDGE_PX,
    ): Bitmap? = withContext(ioDispatcher) {
        if (!File(path).exists()) return@withContext null
        decodeDownsampledBitmap(
            path = path,
            maxLongEdge = maxLongEdge,
            config = Bitmap.Config.ARGB_8888,
        )
    }

    /**
     * Renders non-destructive [editStack] transforms from [originalPath] into [destFile].
     * Never modifies or overwrites [originalPath].
     */
    @WorkerThread
    suspend fun renderNonDestructiveEdit(
        originalPath: String,
        editStack: EditStack,
        sessionId: String,
    ): String = withContext(ioDispatcher) {
        var current: Bitmap = decodeDownsampledBitmap(
            path = originalPath,
            maxLongEdge = MAX_LONG_EDGE_PX,
            config = Bitmap.Config.ARGB_8888,
        ) ?: error("Original capture missing at $originalPath")

        try {
            if (editStack.rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate(editStack.rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    current,
                    0,
                    0,
                    current.width,
                    current.height,
                    matrix,
                    true,
                )
                if (rotated !== current) {
                    current.recycle()
                    current = rotated
                }
            }

            editStack.cropRect?.let { norm ->
                val cropped = cropNormalized(current, norm)
                if (cropped !== current) {
                    current.recycle()
                    current = cropped
                }
            }

            if (editStack.backgroundRemoved && !editStack.segmentationMaskPath.isNullOrBlank()) {
                val maskFile = File(editStack.segmentationMaskPath)
                if (maskFile.exists()) {
                    val masked = applyMaskOntoStudioBackdrop(current, maskFile)
                    if (masked !== current) {
                        current.recycle()
                        current = masked
                    }
                }
            }

            val outFile = editedPreviewFile(sessionId)
            FileOutputStream(outFile).use { fos ->
                current.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            outFile.absolutePath
        } finally {
            if (!current.isRecycled) {
                current.recycle()
            }
        }
    }

    @WorkerThread
    private fun downsampleAndSaveJpeg(
        sourcePath: String,
        destFile: File,
        maxLongEdge: Int,
    ) {
        val bitmap = decodeDownsampledBitmap(
            path = sourcePath,
            maxLongEdge = maxLongEdge,
            config = Bitmap.Config.RGB_565,
        ) ?: error("Failed to decode bitmap at $sourcePath")

        try {
            FileOutputStream(destFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    @WorkerThread
    fun decodeDownsampledBitmap(
        path: String,
        maxLongEdge: Int = MAX_LONG_EDGE_PX,
        config: Bitmap.Config = Bitmap.Config.RGB_565,
    ): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val sampleSize = computeInSampleSize(
            srcWidth = boundsOpts.outWidth,
            srcHeight = boundsOpts.outHeight,
            maxLongEdge = maxLongEdge,
        )

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = config
        }
        var bitmap = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

        val exifOrientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotationDegrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / longEdge.toFloat()
            val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }

        return bitmap
    }

    private fun cropNormalized(source: Bitmap, rect: NormalizedRect): Bitmap {
        val x = (rect.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val y = (rect.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (rect.right * source.width).roundToInt().coerceIn(x + 1, source.width)
        val bottom = (rect.bottom * source.height).roundToInt().coerceIn(y + 1, source.height)
        val w = (right - x).coerceAtLeast(1)
        val h = (bottom - y).coerceAtLeast(1)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    private fun applyMaskOntoStudioBackdrop(source: Bitmap, maskFile: File): Bitmap {
        val rawMask = BitmapFactory.decodeFile(maskFile.absolutePath) ?: return source
        val scaledMask = if (rawMask.width != source.width || rawMask.height != source.height) {
            Bitmap.createScaledBitmap(rawMask, source.width, source.height, true).also {
                rawMask.recycle()
            }
        } else {
            rawMask
        }

        val fgLayer = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val fgCanvas = Canvas(fgLayer)
            fgCanvas.drawBitmap(source, 0f, 0f, null)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            fgCanvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)

            val outCanvas = Canvas(out)
            outCanvas.drawColor(Color.rgb(248, 246, 240))
            outCanvas.drawBitmap(fgLayer, 0f, 0f, null)
            return out
        } finally {
            scaledMask.recycle()
            fgLayer.recycle()
        }
    }
}

fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxLongEdge: Int): Int {
    var inSampleSize = 1
    val longEdge = max(srcWidth, srcHeight)
    while (longEdge / (inSampleSize * 2) >= maxLongEdge) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}
