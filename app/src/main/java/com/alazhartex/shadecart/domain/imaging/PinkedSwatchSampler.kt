package com.alazhartex.shadecart.domain.imaging

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.model.WeaveTag
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Extracts accurate dominant fabric colors from physical swatches with zigzag/pinked scissors edges.
 *
 * Insets inward by [PINKED_EDGE_INSET_FRACTION] (18% per side), filters near-white paper glare,
 * optionally applies a [CalibrationProfile] white-balance correction, computes CIE L*a*b* coordinates
 * via [ColorScience], and estimates weave/sheen texture via [PureImageMath].
 */
class PinkedSwatchSampler(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        const val PINKED_EDGE_INSET_FRACTION = 0.18f

        fun computeInnerSampleRegion(
            bounds: NormalizedRect,
            insetFraction: Float = PINKED_EDGE_INSET_FRACTION,
        ): NormalizedRect {
            val dx = bounds.width() * insetFraction.coerceIn(0.05f, 0.40f)
            val dy = bounds.height() * insetFraction.coerceIn(0.05f, 0.40f)
            val left = (bounds.left + dx).coerceIn(0f, 1f)
            val top = (bounds.top + dy).coerceIn(0f, 1f)
            val right = (bounds.right - dx).coerceIn(left + 0.005f, 1f)
            val bottom = (bounds.bottom - dy).coerceIn(top + 0.005f, 1f)
            return NormalizedRect(left = left, top = top, right = right, bottom = bottom)
        }

        fun computeFilteredMedianArgb(
            pixels: IntArray,
            excludePaperNearWhite: Boolean = true,
        ): Int {
            if (pixels.isEmpty()) return 0xFF334155.toInt()
            val candidateR = ArrayList<Int>(pixels.size)
            val candidateG = ArrayList<Int>(pixels.size)
            val candidateB = ArrayList<Int>(pixels.size)

            for (px in pixels) {
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val maxCh = max(r, max(g, b))
                val minCh = min(r, min(g, b))
                val chroma = maxCh - minCh
                val luma = (0.299 * r + 0.587 * g + 0.114 * b)
                if (excludePaperNearWhite && luma > 234.0 && chroma < 18) {
                    continue
                }
                candidateR.add(r)
                candidateG.add(g)
                candidateB.add(b)
            }

            if (candidateR.isEmpty()) {
                for (px in pixels) {
                    candidateR.add((px ushr 16) and 0xFF)
                    candidateG.add((px ushr 8) and 0xFF)
                    candidateB.add(px and 0xFF)
                }
            }

            candidateR.sort()
            candidateG.sort()
            candidateB.sort()
            val mid = candidateR.size / 2
            val r = candidateR[mid]
            val g = candidateG[mid]
            val b = candidateB[mid]
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun formatHex(argb: Int): String =
            String.format("#%06X", argb and 0xFFFFFF)

        fun describeTextileColor(argb: Int, arabic: Boolean = false): String {
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val luma = (0.299 * r + 0.587 * g + 0.114 * b)
            val chroma = maxC - minC

            if (chroma < 22) {
                return when {
                    luma < 45 -> if (arabic) "أسود فاحم" else "Jet Black"
                    luma < 105 -> if (arabic) "رمادي فحمي" else "Charcoal"
                    luma < 185 -> if (arabic) "رمادي حجري" else "Stone Grey"
                    else -> if (arabic) "أبيض ناصع" else "Ivory White"
                }
            }

            return when {
                r >= g && r >= b && g < 85 && b < 85 ->
                    if (luma < 110) (if (arabic) "نبيتي داكن" else "Burgundy") else (if (arabic) "قرمزي" else "Crimson")
                r > 165 && g in 95..185 && b < 90 ->
                    if (luma > 160) (if (arabic) "رملي ذهبي" else "Sand Gold") else (if (arabic) "طوبي" else "Terracotta")
                r > 190 && g > 175 && b < 110 -> if (arabic) "ذهبي مغرة" else "Ochre Gold"
                g >= r && g >= b ->
                    if (b > g * 0.72f) (if (arabic) "بترولي نيلي" else "Nile Teal") else (if (arabic) "زمردي الأزهر" else "Azhar Emerald")
                b >= r && b >= g ->
                    if (luma < 85) (if (arabic) "كحلي الأزهر" else "Azhar Navy") else (if (arabic) "أزرق ملكي" else "Royal Blue")
                r > 120 && b > 100 && g < 90 -> if (arabic) "برقوقي" else "Plum"
                else -> if (arabic) "درجة نسيج" else "Textile Shade"
            }
        }
    }

    data class ExtractedSwatchColor(
        val colorHex: String,
        val colorName: String,
        val innerSampleBounds: NormalizedRect,
        val labL: Double = 0.0,
        val labA: Double = 0.0,
        val labB: Double = 0.0,
        val weaveSuggestion: PureImageMath.WeaveSuggestion = PureImageMath.WeaveSuggestion.COTTON,
        val weaveTag: WeaveTag = WeaveTag.MATTE,
        val sheenScore: Float = 0f,
    )

    data class SampledRegionResult(
        val hexColor: String,
        val colorName: String,
        val labL: Double,
        val labA: Double,
        val labB: Double,
        val weaveTag: WeaveTag,
        val sheenScore: Float,
    )

    suspend fun sampleRegion(
        sourceBitmap: Bitmap,
        swatchBounds: NormalizedRect,
        calibrationProfile: CalibrationProfile? = null,
    ): SampledRegionResult? {
        if (sourceBitmap.width < 16 || sourceBitmap.height < 16) return null
        val extracted = extractInnerDominantColor(
            sourceBitmap = sourceBitmap,
            swatchBounds = swatchBounds,
            calibrationProfile = calibrationProfile,
        )
        return SampledRegionResult(
            hexColor = extracted.colorHex,
            colorName = extracted.colorName,
            labL = extracted.labL,
            labA = extracted.labA,
            labB = extracted.labB,
            weaveTag = extracted.weaveTag,
            sheenScore = extracted.sheenScore,
        )
    }

    suspend fun extractInnerDominantColor(
        sourceBitmap: Bitmap,
        swatchBounds: NormalizedRect,
        calibrationProfile: CalibrationProfile? = null,
        arabicNames: Boolean = false,
    ): ExtractedSwatchColor = withContext(defaultDispatcher) {
        val innerBounds = computeInnerSampleRegion(swatchBounds)
        val x = (innerBounds.left * sourceBitmap.width).roundToInt().coerceIn(0, sourceBitmap.width - 1)
        val y = (innerBounds.top * sourceBitmap.height).roundToInt().coerceIn(0, sourceBitmap.height - 1)
        val right = (innerBounds.right * sourceBitmap.width).roundToInt().coerceIn(x + 1, sourceBitmap.width)
        val bottom = (innerBounds.bottom * sourceBitmap.height).roundToInt().coerceIn(y + 1, sourceBitmap.height)
        val w = (right - x).coerceAtLeast(1)
        val h = (bottom - y).coerceAtLeast(1)

        val innerBitmap = Bitmap.createBitmap(sourceBitmap, x, y, w, h)
        var calibratedBitmap: Bitmap? = null
        try {
            val rawPixels = IntArray(w * h)
            innerBitmap.getPixels(rawPixels, 0, w, 0, 0, w, h)
            val pixels = ColorCalibrator.applyCalibrationToPixels(rawPixels, calibrationProfile)
            val medianRgb = computeFilteredMedianArgb(pixels)
            val texture = PureImageMath.analyzeWeaveTexture(pixels)

            val paletteSource = if (calibrationProfile != null) {
                Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).also {
                    calibratedBitmap = it
                }
            } else {
                innerBitmap
            }

            val palette = Palette.from(paletteSource)
                .clearFilters()
                .maximumColorCount(8)
                .generate()

            val candidateSwatches = listOfNotNull(
                palette.dominantSwatch,
                palette.vibrantSwatch,
                palette.mutedSwatch,
                palette.darkVibrantSwatch,
                palette.darkMutedSwatch,
            )

            val pickedRgb = candidateSwatches
                .sortedByDescending { it.population }
                .firstOrNull { sw ->
                    val dr = abs(Color.red(sw.rgb) - Color.red(medianRgb))
                    val dg = abs(Color.green(sw.rgb) - Color.green(medianRgb))
                    val db = abs(Color.blue(sw.rgb) - Color.blue(medianRgb))
                    (dr + dg + db) < 180
                }?.rgb ?: medianRgb

            val (labL, labA, labB) = ColorScience.argbToLab(pickedRgb)
            val mappedTag = when (texture.suggestion) {
                PureImageMath.WeaveSuggestion.SATIN_SILK -> WeaveTag.SATIN
                PureImageMath.WeaveSuggestion.LINEN_COARSE -> WeaveTag.LINEN
                PureImageMath.WeaveSuggestion.COTTON ->
                    if (texture.luminanceVariance > 150.0) WeaveTag.TWILL else WeaveTag.MATTE
            }
            val sheenScore = (1f - (texture.luminanceVariance / 300.0).toFloat().coerceIn(0f, 1f))
                .coerceIn(0f, 1f)

            ExtractedSwatchColor(
                colorHex = formatHex(pickedRgb),
                colorName = describeTextileColor(pickedRgb, arabic = arabicNames),
                innerSampleBounds = innerBounds,
                labL = labL.toDouble(),
                labA = labA.toDouble(),
                labB = labB.toDouble(),
                weaveSuggestion = texture.suggestion,
                weaveTag = mappedTag,
                sheenScore = sheenScore,
            )
        } finally {
            if (!innerBitmap.isRecycled) innerBitmap.recycle()
            if (calibratedBitmap != null && !calibratedBitmap!!.isRecycled) calibratedBitmap!!.recycle()
        }
    }
}
