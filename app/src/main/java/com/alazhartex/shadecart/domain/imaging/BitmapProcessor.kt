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
import com.alazhartex.shadecart.domain.model.CalibrationProfile
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
 * - `RGB_565` configuration for display previews.
 * - Non-destructive rendering of crop, rotation, flat-field illumination flattening (A3), and segmentation masks.
 * - 3-frame RGB median stacking (A5a) and calibration patch extraction (A1).
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
     * A5a: Combines 3 burst-captured frame paths using per-pixel RGB median stacking to suppress
     * sensor noise, writing a bounded <=2048px JPEG original.
     */
    suspend fun mergeThreeFramesMedian(tempPaths: List<String>): String = withContext(ioDispatcher) {
        require(tempPaths.isNotEmpty()) { "At least one frame required" }
        if (tempPaths.size < 3) {
            return@withContext finalizeCapturedTempFile(tempPaths.first())
        }
        val bitmaps = tempPaths.take(3).mapNotNull { path ->
            decodeDownsampledBitmap(path, MAX_LONG_EDGE_PX, Bitmap.Config.ARGB_8888)
        }
        try {
            if (bitmaps.size < 3) {
                return@withContext finalizeCapturedTempFile(tempPaths.first())
            }
            val w = bitmaps[0].width
            val h = bitmaps[0].height
            val b2 = if (bitmaps[1].width == w && bitmaps[1].height == h) bitmaps[1] else Bitmap.createScaledBitmap(bitmaps[1], w, h, true)
            val b3 = if (bitmaps[2].width == w && bitmaps[2].height == h) bitmaps[2] else Bitmap.createScaledBitmap(bitmaps[2], w, h, true)

            val p1 = IntArray(w * h)
            val p2 = IntArray(w * h)
            val p3 = IntArray(w * h)
            bitmaps[0].getPixels(p1, 0, w, 0, 0, w, h)
            b2.getPixels(p2, 0, w, 0, 0, w, h)
            b3.getPixels(p3, 0, w, 0, 0, w, h)

            if (b2 !== bitmaps[1]) b2.recycle()
            if (b3 !== bitmaps[2]) b3.recycle()

            val stacked = PureImageMath.medianStackThreeFrames(p1, p2, p3)
            val outBmp = Bitmap.createBitmap(stacked, w, h, Bitmap.Config.ARGB_8888)
            val dest = File(capturesDir(), "orig_${UUID.randomUUID()}.jpg")
            try {
                FileOutputStream(dest).use { fos ->
                    outBmp.compress(Bitmap.CompressFormat.JPEG, 93, fos)
                }
            } finally {
                if (!outBmp.isRecycled) outBmp.recycle()
            }
            tempPaths.forEach { File(it).delete() }
            dest.absolutePath
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * A1: Calibrates a [CalibrationProfile] from the specified gray patch [region] of an image path.
     */
    suspend fun calibrateFromImageRegion(
        imagePath: String,
        region: NormalizedRect,
        deviceId: String,
        profileName: String,
        luxLevel: Float? = null,
    ): CalibrationProfile = withContext(ioDispatcher) {
        val bmp = decodeDownsampledBitmap(imagePath, 1024, Bitmap.Config.ARGB_8888)
            ?: error("Unable to load calibration image")
        try {
            val x = (region.left * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val y = (region.top * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val r = (region.right * bmp.width).roundToInt().coerceIn(x + 1, bmp.width)
            val b = (region.bottom * bmp.height).roundToInt().coerceIn(y + 1, bmp.height)
            val w = (r - x).coerceAtLeast(1)
            val h = (b - y).coerceAtLeast(1)
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, x, y, w, h)
            ColorCalibrator.calibrateFromGrayPixels(
                pixels = pixels,
                deviceId = deviceId,
                profileName = profileName,
                luxLevel = luxLevel,
            )
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

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

            if (editStack.flattenLighting) {
                val w = current.width
                val h = current.height
                val pixels = IntArray(w * h)
                current.getPixels(pixels, 0, w, 0, 0, w, h)
                val flatPixels = PureImageMath.flattenIlluminationPixels(
                    pixels = pixels,
                    width = w,
                    height = h,
                    kernelFraction = editStack.lightingKernelFraction,
                )
                val flattened = Bitmap.createBitmap(flatPixels, w, h, Bitmap.Config.ARGB_8888)
                current.recycle()
                current = flattened
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
