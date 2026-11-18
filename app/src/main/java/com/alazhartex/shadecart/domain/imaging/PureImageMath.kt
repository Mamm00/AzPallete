package com.alazhartex.shadecart.domain.imaging

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure Kotlin pixel-math kernels for:
 * - A3: Flat-field illumination flattening (separable Gaussian illumination estimate + division)
 * - A5a: 3-frame per-pixel RGB median stacking
 * - A5b: Weave / sheen luminance variance + Shannon entropy heuristic
 * - A5c: Laplacian variance focus/blur score
 */
object PureImageMath {

    enum class WeaveSuggestion(val labelEn: String, val labelAr: String) {
        SATIN_SILK("Satin / Silk", "ساتان / حرير"),
        COTTON("Cotton / Twill", "قطن / نسيج متوسط"),
        LINEN_COARSE("Linen / Coarse weave", "كتان / نسيج خشن"),
    }

    data class TextureAnalysis(
        val luminanceVariance: Double,
        val entropyBits: Double,
        val suggestion: WeaveSuggestion,
    )

    /**
     * A3: Separates uneven vignetting / lighting falloff by estimating a low-frequency illumination
     * field on a downsampled luminance grid and normalizing pixel brightness back to target white.
     */
    fun flattenIlluminationPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        kernelFraction: Float = 0.10f,
    ): IntArray {
        if (pixels.isEmpty() || width <= 4 || height <= 4) return pixels

        val smallW = 100.coerceAtMost(width)
        val smallH = (100 * height / width).coerceIn(16, height)
        val lumSmall = FloatArray(smallW * smallH)

        for (sy in 0 until smallH) {
            val srcY = (sy * height / smallH).coerceIn(0, height - 1)
            for (sx in 0 until smallW) {
                val srcX = (sx * width / smallW).coerceIn(0, width - 1)
                val px = pixels[srcY * width + srcX]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                lumSmall[sy * smallW + sx] = (0.299f * r + 0.587f * g + 0.114f * b).coerceAtLeast(16f)
            }
        }

        val radius = (max(smallW, smallH) * kernelFraction.coerceIn(0.03f, 0.25f))
            .roundToInt()
            .coerceIn(2, 18)
        val blurred = separableBoxBlur(lumSmall, smallW, smallH, radius)

        var maxIllum = 1f
        for (v in blurred) {
            if (v > maxIllum) maxIllum = v
        }
        val targetIllum = maxIllum.coerceIn(140f, 245f)

        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            val fy = (y.toFloat() / height) * (smallH - 1)
            val y0 = fy.toInt().coerceIn(0, smallH - 1)
            val y1 = min(y0 + 1, smallH - 1)
            val wy = fy - y0
            for (x in 0 until width) {
                val fx = (x.toFloat() / width) * (smallW - 1)
                val x0 = fx.toInt().coerceIn(0, smallW - 1)
                val x1 = min(x0 + 1, smallW - 1)
                val wx = fx - x0

                val i00 = blurred[y0 * smallW + x0]
                val i10 = blurred[y0 * smallW + x1]
                val i01 = blurred[y1 * smallW + x0]
                val i11 = blurred[y1 * smallW + x1]
                val illum = ((i00 * (1f - wx) + i10 * wx) * (1f - wy) +
                    (i01 * (1f - wx) + i11 * wx) * wy).coerceAtLeast(24f)

                val gain = (targetIllum / illum).coerceIn(0.65f, 1.85f)
                val px = pixels[y * width + x]
                val a = (px ushr 24) and 0xFF
                val r = (((px ushr 16) and 0xFF) * gain).roundToInt().coerceIn(0, 255)
                val g = (((px ushr 8) and 0xFF) * gain).roundToInt().coerceIn(0, 255)
                val b = ((px and 0xFF) * gain).roundToInt().coerceIn(0, 255)
                out[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /**
     * A5a: Computes per-pixel RGB median across 3 aligned frames to suppress CMOS sensor noise.
     */
    fun medianStackThreeFrames(
        frame1: IntArray,
        frame2: IntArray,
        frame3: IntArray,
    ): IntArray {
        val len = min(frame1.size, min(frame2.size, frame3.size))
        val out = IntArray(len)
        for (i in 0 until len) {
            val p1 = frame1[i]
            val p2 = frame2[i]
            val p3 = frame3[i]
            val a = (p1 ushr 24) and 0xFF
            val r = medianOf3((p1 ushr 16) and 0xFF, (p2 ushr 16) and 0xFF, (p3 ushr 16) and 0xFF)
            val g = medianOf3((p1 ushr 8) and 0xFF, (p2 ushr 8) and 0xFF, (p3 ushr 8) and 0xFF)
            val b = medianOf3(p1 and 0xFF, p2 and 0xFF, p3 and 0xFF)
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * A5b: Computes luminance variance and 256-bin Shannon entropy to suggest weave/sheen class.
     */
    fun analyzeWeaveTexture(pixels: IntArray): TextureAnalysis {
        if (pixels.isEmpty()) {
            return TextureAnalysis(0.0, 0.0, WeaveSuggestion.COTTON)
        }
        val hist = IntArray(256)
        var sum = 0.0
        var sumSq = 0.0
        for (px in pixels) {
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val luma = (0.299 * r + 0.587 * g + 0.114 * b)
            val bin = luma.roundToInt().coerceIn(0, 255)
            hist[bin]++
            sum += luma
            sumSq += luma * luma
        }
        val n = pixels.size.toDouble()
        val mean = sum / n
        val variance = ((sumSq / n) - mean * mean).coerceAtLeast(0.0)
        var entropy = 0.0
        val log2 = ln(2.0)
        for (count in hist) {
            if (count > 0) {
                val p = count / n
                entropy -= p * (ln(p) / log2)
            }
        }
        val suggestion = when {
            variance < 85.0 && entropy < 4.5 -> WeaveSuggestion.SATIN_SILK
            variance > 260.0 -> WeaveSuggestion.LINEN_COARSE
            else -> WeaveSuggestion.COTTON
        }
        return TextureAnalysis(
            luminanceVariance = variance,
            entropyBits = entropy,
            suggestion = suggestion,
        )
    }

    /**
     * A5c: Computes Laplacian variance over a luma grid to detect motion blur vs crisp focus.
     */
    fun computeLaplacianFocusScore(
        lumaGrid: IntArray,
        width: Int,
        height: Int,
    ): Double {
        if (width < 4 || height < 4 || lumaGrid.size < width * height) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val lap = (
                    lumaGrid[row - width + x] +
                        lumaGrid[row + width + x] +
                        lumaGrid[row + x - 1] +
                        lumaGrid[row + x + 1] -
                        4 * lumaGrid[row + x]
                    ).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return ((sumSq / count) - mean * mean).coerceAtLeast(0.0)
    }

    private fun medianOf3(a: Int, b: Int, c: Int): Int =
        max(min(a, b), min(max(a, b), c))

    private fun separableBoxBlur(
        input: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
    ): FloatArray {
        val temp = FloatArray(w * h)
        val out = FloatArray(w * h)

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f
                var cnt = 0
                val xStart = max(0, x - radius)
                val xEnd = min(w - 1, x + radius)
                for (kx in xStart..xEnd) {
                    sum += input[row + kx]
                    cnt++
                }
                temp[row + x] = sum / cnt
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sum = 0f
                var cnt = 0
                val yStart = max(0, y - radius)
                val yEnd = min(h - 1, y + radius)
                for (ky in yStart..yEnd) {
                    sum += temp[ky * w + x]
                    cnt++
                }
                out[y * w + x] = sum / cnt
            }
        }
        return out
    }
}
