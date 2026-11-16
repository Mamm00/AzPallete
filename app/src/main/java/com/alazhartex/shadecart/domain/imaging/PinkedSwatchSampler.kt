package com.alazhartex.shadecart.domain.imaging

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.alazhartex.shadecart.data.model.NormalizedRect
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
 * To avoid counting pinked teeth, drop shadows, or white cardstock paper behind notches,
 * sampling always insets inward by [PINKED_EDGE_INSET_FRACTION] (18% per side) and filters
 * paper-like near-white background pixels before invoking AndroidX [Palette].
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

        /**
         * Pure-JVM fallback color estimator used when Palette quantization drops uniform or very dark
         * fabrics, and verified directly in unit tests against noisy pinked border contamination.
         */
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
                // Skip paper margins (very bright low-chroma pixels) unless the entire region is white fabric
                if (excludePaperNearWhite && luma > 234.0 && chroma < 18) {
                    continue
                }
                candidateR.add(r)
                candidateG.add(g)
                candidateB.add(b)
            }

            if (candidateR.isEmpty()) {
                // Entire swatch genuinely is bright ivory/white fabric
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

        fun describeTextileColor(argb: Int): String {
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val luma = (0.299 * r + 0.587 * g + 0.114 * b)
            val chroma = maxC - minC

            if (chroma < 22) {
                return when {
                    luma < 45 -> "Jet Black"
                    luma < 105 -> "Charcoal"
                    luma < 185 -> "Stone Grey"
                    else -> "Ivory White"
                }
            }

            return when {
                r >= g && r >= b && g < 85 && b < 85 -> if (luma < 110) "Burgundy" else "Crimson"
                r > 165 && g in 95..185 && b < 90 -> if (luma > 160) "Sand Gold" else "Terracotta"
                r > 190 && g > 175 && b < 110 -> "Ochre Gold"
                g >= r && g >= b -> if (b > g * 0.72f) "Nile Teal" else "Azhar Emerald"
                b >= r && b >= g -> if (luma < 85) "Azhar Navy" else "Royal Blue"
                r > 120 && b > 100 && g < 90 -> "Plum"
                else -> "Textile Shade"
            }
        }
    }

    data class ExtractedSwatchColor(
        val colorHex: String,
        val colorName: String,
        val innerSampleBounds: NormalizedRect,
    )

    suspend fun extractInnerDominantColor(
        sourceBitmap: Bitmap,
        swatchBounds: NormalizedRect,
    ): ExtractedSwatchColor = withContext(defaultDispatcher) {
        val innerBounds = computeInnerSampleRegion(swatchBounds)
        val x = (innerBounds.left * sourceBitmap.width).roundToInt().coerceIn(0, sourceBitmap.width - 1)
        val y = (innerBounds.top * sourceBitmap.height).roundToInt().coerceIn(0, sourceBitmap.height - 1)
        val right = (innerBounds.right * sourceBitmap.width).roundToInt().coerceIn(x + 1, sourceBitmap.width)
        val bottom = (innerBounds.bottom * sourceBitmap.height).roundToInt().coerceIn(y + 1, sourceBitmap.height)
        val w = (right - x).coerceAtLeast(1)
        val h = (bottom - y).coerceAtLeast(1)

        val innerBitmap = Bitmap.createBitmap(sourceBitmap, x, y, w, h)
        try {
            val pixels = IntArray(w * h)
            innerBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val medianRgb = computeFilteredMedianArgb(pixels)

            val palette = Palette.from(innerBitmap)
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

            // Prefer the most populated Palette swatch that is reasonably consistent with inner fabric
            val pickedRgb = candidateSwatches
                .sortedByDescending { it.population }
                .firstOrNull { sw ->
                    val dr = abs(Color.red(sw.rgb) - Color.red(medianRgb))
                    val dg = abs(Color.green(sw.rgb) - Color.green(medianRgb))
                    val db = abs(Color.blue(sw.rgb) - Color.blue(medianRgb))
                    (dr + dg + db) < 180
                }?.rgb ?: medianRgb

            ExtractedSwatchColor(
                colorHex = formatHex(pickedRgb),
                colorName = describeTextileColor(pickedRgb),
                innerSampleBounds = innerBounds,
            )
        } finally {
            if (!innerBitmap.isRecycled) {
                innerBitmap.recycle()
            }
        }
    }
}
