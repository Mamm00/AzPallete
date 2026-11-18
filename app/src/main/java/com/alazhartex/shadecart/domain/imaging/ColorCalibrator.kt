package com.alazhartex.shadecart.domain.imaging

import com.alazhartex.shadecart.domain.model.CalibrationProfile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure Kotlin "ShadeLock" white-balance and 12-patch color matrix calibrator.
 *
 * Zero Android dependencies so both von Kries diagonal gains and least-squares 3×3 matrix fitting
 * can be unit tested deterministically on the JVM.
 */
object ColorCalibrator {

    private const val MIN_GAIN = 0.45f
    private const val MAX_GAIN = 2.25f

    /**
     * Computes von Kries per-channel gains normalizing a neutral gray calibration patch toward
     * [targetGray] (default mid-neutral 180 / 255).
     */
    fun computeDiagonalGains(
        meanR: Float,
        meanG: Float,
        meanB: Float,
        targetGray: Float = 180f,
    ): Triple<Float, Float, Float> {
        val safeR = meanR.coerceAtLeast(12f)
        val safeG = meanG.coerceAtLeast(12f)
        val safeB = meanB.coerceAtLeast(12f)
        return Triple(
            (targetGray / safeR).coerceIn(MIN_GAIN, MAX_GAIN),
            (targetGray / safeG).coerceIn(MIN_GAIN, MAX_GAIN),
            (targetGray / safeB).coerceIn(MIN_GAIN, MAX_GAIN),
        )
    }

    /**
     * Builds a diagonal [CalibrationProfile] from sampled gray-patch RGB pixels.
     */
    fun calibrateFromGrayPixels(
        pixels: IntArray,
        deviceId: String,
        profileName: String,
        luxLevel: Float? = null,
        targetGray: Float = 180f,
        capturedAt: Long = System.currentTimeMillis(),
    ): CalibrationProfile {
        if (pixels.isEmpty()) {
            return CalibrationProfile(
                name = profileName,
                deviceId = deviceId,
                gainR = 1f,
                gainG = 1f,
                gainB = 1f,
                ambientLux = luxLevel ?: -1f,
                capturedAt = capturedAt,
            )
        }
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        for (px in pixels) {
            sumR += (px ushr 16) and 0xFF
            sumG += (px ushr 8) and 0xFF
            sumB += px and 0xFF
        }
        val n = pixels.size.toFloat()
        val (gR, gG, gB) = computeDiagonalGains(
            meanR = (sumR / n).toFloat(),
            meanG = (sumG / n).toFloat(),
            meanB = (sumB / n).toFloat(),
            targetGray = targetGray,
        )
        return CalibrationProfile(
            name = profileName,
            deviceId = deviceId,
            gainR = gR,
            gainG = gG,
            gainB = gB,
            matrix3x3 = null,
            ambientLux = luxLevel ?: -1f,
            capturedAt = capturedAt,
        )
    }

    /**
     * Builds a diagonal [CalibrationProfile] from an A4 white paper / showroom reference sample.
     */
    fun calibrateFromWhiteSample(
        profileId: String,
        name: String,
        measuredWhiteRgb: Triple<Int, Int, Int>,
        ambientLux: Float = -1f,
        targetWhite: Float = 246f,
        capturedAt: Long = System.currentTimeMillis(),
    ): CalibrationProfile {
        val (gR, gG, gB) = computeDiagonalGains(
            meanR = measuredWhiteRgb.first.toFloat(),
            meanG = measuredWhiteRgb.second.toFloat(),
            meanB = measuredWhiteRgb.third.toFloat(),
            targetGray = targetWhite,
        )
        return CalibrationProfile(
            id = profileId,
            name = name,
            gainR = gR,
            gainG = gG,
            gainB = gB,
            ambientLux = ambientLux,
            matrix3x3 = null,
            capturedAt = capturedAt,
        )
    }

    /**
     * Fits a 3×3 color correction matrix (row-major 9 floats) via normal equations
     * `(X^T X) W = X^T Y` using Gaussian elimination with partial pivoting.
     *
     * Each entry in [measuredPatches] and [referencePatches] must be a 3-element `[R, G, B]` array.
     */
    fun solve3x3ColorMatrix(
        measuredPatches: List<FloatArray>,
        referencePatches: List<FloatArray>,
    ): FloatArray {
        require(measuredPatches.size >= 3 && measuredPatches.size == referencePatches.size) {
            "Need >= 3 matched color patches to solve 3x3 color correction matrix"
        }

        val xtx = Array(3) { DoubleArray(3) }
        val xty = Array(3) { DoubleArray(3) }

        for (p in measuredPatches.indices) {
            val m = measuredPatches[p]
            val r = referencePatches[p]
            for (i in 0 until 3) {
                for (j in 0 until 3) {
                    xtx[i][j] += m[i].toDouble() * m[j].toDouble()
                    xty[i][j] += m[i].toDouble() * r[j].toDouble()
                }
            }
        }

        // Tiny Tikhonov regularization for numerical stability on collinear patches
        for (i in 0 until 3) {
            xtx[i][i] += 1e-4
        }

        val weights = Array(3) { DoubleArray(3) }
        for (col in 0 until 3) {
            val rhs = DoubleArray(3) { row -> xty[row][col] }
            val solved = solveLinear3x3(xtx, rhs)
            for (row in 0 until 3) {
                weights[col][row] = solved[row]
            }
        }

        // Row-major output: outRow[c] = dot(weights[c], inRgb)
        return floatArrayOf(
            weights[0][0].toFloat(), weights[0][1].toFloat(), weights[0][2].toFloat(),
            weights[1][0].toFloat(), weights[1][1].toFloat(), weights[1][2].toFloat(),
            weights[2][0].toFloat(), weights[2][1].toFloat(), weights[2][2].toFloat(),
        )
    }

    /**
     * Applies [profile] gains or 3×3 matrix to an sRGB ARGB pixel, preserving alpha.
     */
    fun applyCalibrationToArgb(argb: Int, profile: CalibrationProfile?): Int {
        if (profile == null) return argb
        val a = (argb ushr 24) and 0xFF
        val r = ((argb ushr 16) and 0xFF).toFloat()
        val g = ((argb ushr 8) and 0xFF).toFloat()
        val b = (argb and 0xFF).toFloat()

        val mat = profile.matrix3x3
        val outR: Int
        val outG: Int
        val outB: Int
        if (mat != null && mat.size == 9) {
            outR = (mat[0] * r + mat[1] * g + mat[2] * b).roundToInt().coerceIn(0, 255)
            outG = (mat[3] * r + mat[4] * g + mat[5] * b).roundToInt().coerceIn(0, 255)
            outB = (mat[6] * r + mat[7] * g + mat[8] * b).roundToInt().coerceIn(0, 255)
        } else {
            outR = (r * profile.gainR).roundToInt().coerceIn(0, 255)
            outG = (g * profile.gainG).roundToInt().coerceIn(0, 255)
            outB = (b * profile.gainB).roundToInt().coerceIn(0, 255)
        }
        return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    fun applyCalibrationToPixels(pixels: IntArray, profile: CalibrationProfile?): IntArray {
        if (profile == null) return pixels
        return IntArray(pixels.size) { idx -> applyCalibrationToArgb(pixels[idx], profile) }
    }

    private fun solveLinear3x3(matrix: Array<DoubleArray>, rhsInput: DoubleArray): DoubleArray {
        val a = Array(3) { r -> matrix[r].clone() }
        val b = rhsInput.clone()

        for (pivot in 0 until 3) {
            var maxRow = pivot
            for (r in pivot + 1 until 3) {
                if (abs(a[r][pivot]) > abs(a[maxRow][pivot])) {
                    maxRow = r
                }
            }
            val tmpRow = a[pivot]
            a[pivot] = a[maxRow]
            a[maxRow] = tmpRow
            val tmpB = b[pivot]
            b[pivot] = b[maxRow]
            b[maxRow] = tmpB

            val diag = a[pivot][pivot]
            if (abs(diag) < 1e-10) continue

            for (r in pivot + 1 until 3) {
                val factor = a[r][pivot] / diag
                b[r] -= factor * b[pivot]
                for (c in pivot until 3) {
                    a[r][c] -= factor * a[pivot][c]
                }
            }
        }

        val x = DoubleArray(3)
        for (r in 2 downTo 0) {
            var sum = b[r]
            for (c in r + 1 until 3) {
                sum -= a[r][c] * x[c]
            }
            val diag = a[r][r]
            x[r] = if (abs(diag) > 1e-10) sum / diag else 1.0
        }
        return x
    }
}
