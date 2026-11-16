package com.alazhartex.shadecart.domain.imaging

import android.graphics.Bitmap
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.vision.OcrTextBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DetectedSwatchRegion(
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String,
    val outerBounds: NormalizedRect,
    val innerSampleBounds: NormalizedRect,
    val columnIndex: Int,
)

/**
 * Detects physical fabric swatches arranged in an A4 two-column shade cart layout
 * (matching `docs/reference/shade_cart_reference.png`), excludes pinked/zigzag outer
 * margins, extracts dominant HEX colors on [defaultDispatcher], and pairs nearby
 * ML Kit OCR numeric labels (`5`, `7`, `11`, `12`, ...).
 */
class TwoColumnSwatchDetector(
    private val sampler: PinkedSwatchSampler = PinkedSwatchSampler(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        private const val ANALYSIS_WIDTH = 300
        private const val HEADER_CUTOFF_Y = 0.22f
        private const val FOOTER_CUTOFF_Y = 0.96f

        /**
         * Pairs raw detected bounding boxes (in reading order) with numeric OCR blocks.
         * Extracted cleanly so JVM unit tests can validate label assignment without Android Bitmaps.
         */
        fun pairNumericLabels(
            swatchBoxes: List<NormalizedRect>,
            numericLabels: List<OcrTextBlock>,
        ): List<String> {
            val remaining = numericLabels.toMutableList()
            val assigned = MutableList(swatchBoxes.size) { "" }

            swatchBoxes.forEachIndexed { idx, box ->
                val boxCx = (box.left + box.right) / 2f
                val boxCy = (box.top + box.bottom) / 2f

                // Prefer numeric labels aligned vertically with the swatch in the same column half
                val sameColumn = boxCx < 0.5f
                var bestIdx = -1
                var bestScore = Float.MAX_VALUE

                remaining.forEachIndexed { labelIdx, label ->
                    val lx = (label.bounds.left + label.bounds.right) / 2f
                    val ly = (label.bounds.top + label.bounds.bottom) / 2f
                    val labelSameCol = lx < 0.52f
                    if (labelSameCol != sameColumn) return@forEachIndexed

                    val dy = abs(ly - boxCy)
                    if (dy > max(box.height() * 0.95f, 0.10f)) return@forEachIndexed

                    val dx = abs(lx - boxCx)
                    val score = dy * 2.5f + dx
                    if (score < bestScore) {
                        bestScore = score
                        bestIdx = labelIdx
                    }
                }

                if (bestIdx >= 0) {
                    assigned[idx] = remaining.removeAt(bestIdx).text.trim()
                }
            }

            // Fallback sequential numbers for any unlabeled swatch
            var fallbackCounter = 1
            val used = assigned.filter { it.isNotBlank() }.toMutableSet()
            for (i in assigned.indices) {
                if (assigned[i].isBlank()) {
                    while (used.contains(fallbackCounter.toString())) {
                        fallbackCounter++
                    }
                    val num = fallbackCounter.toString()
                    assigned[i] = num
                    used.add(num)
                    fallbackCounter++
                }
            }
            return assigned
        }
    }

    suspend fun detectSwatches(
        bitmap: Bitmap,
        numericLabels: List<OcrTextBlock>,
    ): List<DetectedSwatchRegion> = withContext(defaultDispatcher) {
        if (bitmap.width < 40 || bitmap.height < 40) return@withContext emptyList()

        val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        val gridW = ANALYSIS_WIDTH
        val gridH = (gridW * aspect).roundToInt().coerceIn(80, 600)
        val scaled = Bitmap.createScaledBitmap(bitmap, gridW, gridH, true)

        val candidateBoxes = try {
            val pixels = IntArray(gridW * gridH)
            scaled.getPixels(pixels, 0, gridW, 0, 0, gridW, gridH)
            findTwoColumnSwatchBoxes(pixels, gridW, gridH)
        } finally {
            if (!scaled.isRecycled && scaled !== bitmap) {
                scaled.recycle()
            }
        }

        if (candidateBoxes.isEmpty()) return@withContext emptyList()

        val sortedBoxes = sortInReadingOrder(candidateBoxes)
        val labels = pairNumericLabels(sortedBoxes, numericLabels)

        sortedBoxes.mapIndexed { idx, bounds ->
            val extracted = sampler.extractInnerDominantColor(bitmap, bounds)
            val colIdx = if ((bounds.left + bounds.right) / 2f < 0.5f) 0 else 1
            DetectedSwatchRegion(
                swatchNumber = labels[idx],
                colorHex = extracted.colorHex,
                colorName = extracted.colorName,
                outerBounds = bounds,
                innerSampleBounds = extracted.innerSampleBounds,
                columnIndex = colIdx,
            )
        }
    }

    private fun findTwoColumnSwatchBoxes(
        pixels: IntArray,
        w: Int,
        h: Int,
    ): List<NormalizedRect> {
        val bgRgb = estimatePaperBackgroundRgb(pixels, w, h)
        val bgR = (bgRgb ushr 16) and 0xFF
        val bgG = (bgRgb ushr 8) and 0xFF
        val bgB = bgRgb and 0xFF

        val yStart = (h * HEADER_CUTOFF_Y).roundToInt().coerceIn(0, h - 1)
        val yEnd = (h * FOOTER_CUTOFF_Y).roundToInt().coerceIn(yStart + 1, h)

        val leftColBoxes = detectColumnSwatches(
            pixels = pixels,
            w = w,
            h = h,
            xMin = (w * 0.04f).roundToInt(),
            xMax = (w * 0.49f).roundToInt(),
            yMin = yStart,
            yMax = yEnd,
            bgR = bgR,
            bgG = bgG,
            bgB = bgB,
        )
        val rightColBoxes = detectColumnSwatches(
            pixels = pixels,
            w = w,
            h = h,
            xMin = (w * 0.51f).roundToInt(),
            xMax = (w * 0.96f).roundToInt(),
            yMin = yStart,
            yMax = yEnd,
            bgR = bgR,
            bgG = bgG,
            bgB = bgB,
        )

        return leftColBoxes + rightColBoxes
    }

    private fun detectColumnSwatches(
        pixels: IntArray,
        w: Int,
        h: Int,
        xMin: Int,
        xMax: Int,
        yMin: Int,
        yMax: Int,
        bgR: Int,
        bgG: Int,
        bgB: Int,
    ): List<NormalizedRect> {
        val colWidth = (xMax - xMin).coerceAtLeast(1)
        val rowForegroundCount = IntArray(h)

        for (y in yMin until yMax) {
            var fg = 0
            val rowOffset = y * w
            for (x in xMin until xMax) {
                if (isSwatchPixel(pixels[rowOffset + x], bgR, bgG, bgB)) {
                    fg++
                }
            }
            rowForegroundCount[y] = fg
        }

        // A swatch band has sustained width (>= 22% of column width)
        val minRowPixels = (colWidth * 0.22f).roundToInt().coerceAtLeast(8)
        val minBandHeight = (h * 0.045f).roundToInt().coerceAtLeast(10)

        val bands = mutableListOf<IntRange>()
        var bandStart = -1
        var gapTolerance = 0

        for (y in yMin until yMax) {
            val active = rowForegroundCount[y] >= minRowPixels
            if (active) {
                if (bandStart == -1) bandStart = y
                gapTolerance = 0
            } else if (bandStart != -1) {
                gapTolerance++
                if (gapTolerance > 3) {
                    val bandEnd = y - gapTolerance
                    if (bandEnd - bandStart + 1 >= minBandHeight) {
                        bands.add(bandStart..bandEnd)
                    }
                    bandStart = -1
                    gapTolerance = 0
                }
            }
        }
        if (bandStart != -1 && (yMax - 1 - bandStart + 1) >= minBandHeight) {
            bands.add(bandStart until yMax)
        }

        val result = mutableListOf<NormalizedRect>()
        for (band in bands) {
            val bandHeight = (band.last - band.first + 1).coerceAtLeast(1)
            // Compute horizontal extent where foreground density >= 35% of band height
            val minColPixels = (bandHeight * 0.35f).roundToInt().coerceAtLeast(3)
            var leftX = -1
            var rightX = -1
            for (x in xMin until xMax) {
                var fg = 0
                for (y in band) {
                    if (isSwatchPixel(pixels[y * w + x], bgR, bgG, bgB)) fg++
                }
                if (fg >= minColPixels) {
                    if (leftX == -1) leftX = x
                    rightX = x
                }
            }
            if (leftX != -1 && (rightX - leftX) >= (colWidth * 0.25f)) {
                val norm = NormalizedRect(
                    left = (leftX.toFloat() / w).coerceIn(0f, 1f),
                    top = (band.first.toFloat() / h).coerceIn(0f, 1f),
                    right = ((rightX + 1).toFloat() / w).coerceIn(0f, 1f),
                    bottom = ((band.last + 1).toFloat() / h).coerceIn(0f, 1f),
                )
                if (norm.width() >= 0.10f && norm.height() >= 0.045f) {
                    result.add(norm)
                }
            }
        }
        return result
    }

    private fun isSwatchPixel(argb: Int, bgR: Int, bgG: Int, bgB: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val dist = sqrt(
            ((r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)).toDouble(),
        )
        // Exclude light cardstock shadows (grayish/warm near background) and bright paper
        val isDarkShadow = dist < 42.0
        if (isDarkShadow) return false
        val luma = 0.299 * r + 0.587 * g + 0.114 * b
        val bgLuma = 0.299 * bgR + 0.587 * bgG + 0.114 * bgB
        // Ignore slight paper shading near background brightness if low chroma
        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        return !(chroma < 16 && abs(luma - bgLuma) < 52.0)
    }

    private fun estimatePaperBackgroundRgb(pixels: IntArray, w: Int, h: Int): Int {
        // Sample center gutter and side borders which typically expose physical card paper
        val samplesR = ArrayList<Int>(512)
        val samplesG = ArrayList<Int>(512)
        val samplesB = ArrayList<Int>(512)

        val gutterXStart = (w * 0.48f).roundToInt()
        val gutterXEnd = (w * 0.52f).roundToInt()
        for (y in (h * 0.25f).roundToInt() until (h * 0.90f).roundToInt() step 3) {
            for (x in gutterXStart..gutterXEnd) {
                val px = pixels[y * w + x]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                if (r > 150 && g > 150 && b > 140) {
                    samplesR.add(r)
                    samplesG.add(g)
                    samplesB.add(b)
                }
            }
        }
        if (samplesR.isEmpty()) return 0xFFF7F5EE.toInt()
        samplesR.sort()
        samplesG.sort()
        samplesB.sort()
        val mid = samplesR.size / 2
        return (0xFF shl 24) or (samplesR[mid] shl 16) or (samplesG[mid] shl 8) or samplesB[mid]
    }

    private fun sortInReadingOrder(boxes: List<NormalizedRect>): List<NormalizedRect> {
        // Group roughly by row band (within ~6% vertical tolerance), then Left column before Right column
        val sortedByTop = boxes.sortedBy { it.top }
        val rows = mutableListOf<MutableList<NormalizedRect>>()
        for (box in sortedByTop) {
            val cy = (box.top + box.bottom) / 2f
            val matchingRow = rows.lastOrNull()?.let { row ->
                val rowCy = row.map { (it.top + it.bottom) / 2f }.average().toFloat()
                if (abs(cy - rowCy) <= 0.08f) row else null
            }
            if (matchingRow != null) {
                matchingRow.add(box)
            } else {
                rows.add(mutableListOf(box))
            }
        }
        return rows.flatMap { row -> row.sortedBy { it.left } }
    }
}
