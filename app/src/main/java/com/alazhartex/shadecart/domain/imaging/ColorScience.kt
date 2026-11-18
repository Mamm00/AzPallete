package com.alazhartex.shadecart.domain.imaging

import com.alazhartex.shadecart.domain.validation.HexColorValidator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin perceptual color-science engine implementing sRGB -> CIE XYZ (D65) -> CIE L*a*b*
 * conversions and full CIEDE2000 (Sharma, Wu, & Dalal, *Color Research & Application*, 2005).
 *
 * Zero Android framework imports so unit tests run directly on the JVM.
 */
object ColorScience {

    private const val D65_XN = 95.047
    private const val D65_YN = 100.000
    private const val D65_ZN = 108.883
    private const val CIE_EPSILON = 216.0 / 24389.0
    private const val CIE_KAPPA = 24389.0 / 27.0
    private const val POW_25_7 = 6_103_515_625.0

    const val D65_SPECTROPHOTOMETER_DISCLAIMER =
        "CIEDE2000 values compare sRGB captures under D65 simulation. Phone cameras are not " +
            "spectrophotometers; confirm critical dye sign-offs with a BLE spectrophotometer (Nix Pro 2 class)."

    enum class DeltaEBand(
        val labelEn: String,
        val labelAr: String,
        val severityRank: Int,
    ) {
        IMPERCEPTIBLE("Imperceptible", "غير ملحوظ", 0),
        ONLY_UNDER_SCRUTINY("Only under scrutiny", "ملحوظ بالفحص الدقيق", 1),
        PERCEPTIBLE("Perceptible", "اختلاف ملحوظ", 2),
        VISIBLE_MISMATCH("Visible mismatch", "تباين واضح", 3),
        DIFFERENT_COLORS("Different colors", "ألوان مختلفة", 4),
    }

    fun classifyDeltaE(deltaE: Float): DeltaEBand = when {
        deltaE < 1.0f -> DeltaEBand.IMPERCEPTIBLE
        deltaE < 2.0f -> DeltaEBand.ONLY_UNDER_SCRUTINY
        deltaE < 3.5f -> DeltaEBand.PERCEPTIBLE
        deltaE <= 5.0f -> DeltaEBand.VISIBLE_MISMATCH
        else -> DeltaEBand.DIFFERENT_COLORS
    }

    fun srgbToLinear(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f).toDouble()
        val linear = if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
        return linear.toFloat()
    }

    fun linearToXyzD65(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val rd = r.toDouble()
        val gd = g.toDouble()
        val bd = b.toDouble()
        val x = (0.4124564 * rd + 0.3575761 * gd + 0.1804375 * bd) * 100.0
        val y = (0.2126729 * rd + 0.7151522 * gd + 0.0721750 * bd) * 100.0
        val z = (0.0193339 * rd + 0.1191920 * gd + 0.9503041 * bd) * 100.0
        return Triple(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun xyzToLab(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val fx = labF(x.toDouble() / D65_XN)
        val fy = labF(y.toDouble() / D65_YN)
        val fz = labF(z.toDouble() / D65_ZN)

        val l = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val b = 200.0 * (fy - fz)
        return Triple(l.toFloat(), a.toFloat(), b.toFloat())
    }

    fun srgbToLab(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val lr = srgbToLinear(r.coerceIn(0, 255) / 255f)
        val lg = srgbToLinear(g.coerceIn(0, 255) / 255f)
        val lb = srgbToLinear(b.coerceIn(0, 255) / 255f)
        val (x, y, z) = linearToXyzD65(lr, lg, lb)
        return xyzToLab(x, y, z)
    }

    fun argbToLab(argb: Int): Triple<Float, Float, Float> {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return srgbToLab(r, g, b)
    }

    fun hexToLab(hex: String): Triple<Float, Float, Float>? {
        val argb = HexColorValidator.parseArgbOrNull(hex) ?: return null
        return argbToLab(argb)
    }

    /**
     * Exact CIEDE2000 perceptual color difference (kL = kC = kH = 1.0 by default).
     */
    fun ciede2000(
        lab1: Triple<Float, Float, Float>,
        lab2: Triple<Float, Float, Float>,
        kL: Double = 1.0,
        kC: Double = 1.0,
        kH: Double = 1.0,
    ): Float {
        val l1 = lab1.first.toDouble()
        val a1 = lab1.second.toDouble()
        val b1 = lab1.third.toDouble()
        val l2 = lab2.first.toDouble()
        val a2 = lab2.second.toDouble()
        val b2 = lab2.third.toDouble()

        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cBar = 0.5 * (c1 + c2)
        val cBar7 = cBar.pow(7)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + POW_25_7)))

        val a1Prime = (1.0 + g) * a1
        val a2Prime = (1.0 + g) * a2
        val c1Prime = hypot(a1Prime, b1)
        val c2Prime = hypot(a2Prime, b2)

        val h1Prime = hueAngleDegrees(a1Prime, b1)
        val h2Prime = hueAngleDegrees(a2Prime, b2)

        val dLPrime = l2 - l1
        val dCPrime = c2Prime - c1Prime

        val cProd = c1Prime * c2Prime
        val dhPrime = when {
            cProd == 0.0 -> 0.0
            abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            (h2Prime - h1Prime) > 180.0 -> (h2Prime - h1Prime) - 360.0
            else -> (h2Prime - h1Prime) + 360.0
        }
        val dHPrime = 2.0 * sqrt(cProd) * sin(Math.toRadians(dhPrime * 0.5))

        val lBarPrime = 0.5 * (l1 + l2)
        val cBarPrime = 0.5 * (c1Prime + c2Prime)

        val hBarPrime = when {
            cProd == 0.0 -> h1Prime + h2Prime
            abs(h1Prime - h2Prime) <= 180.0 -> 0.5 * (h1Prime + h2Prime)
            (h1Prime + h2Prime) < 360.0 -> 0.5 * (h1Prime + h2Prime + 360.0)
            else -> 0.5 * (h1Prime + h2Prime - 360.0)
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
            0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        val dTheta = 30.0 * exp(-((hBarPrime - 275.0) / 25.0).pow(2))
        val cBarPrime7 = cBarPrime.pow(7)
        val rC = 2.0 * sqrt(cBarPrime7 / (cBarPrime7 + POW_25_7))

        val lBarMinus50Sq = (lBarPrime - 50.0).pow(2)
        val sL = 1.0 + (0.015 * lBarMinus50Sq) / sqrt(20.0 + lBarMinus50Sq)
        val sC = 1.0 + 0.045 * cBarPrime
        val sH = 1.0 + 0.015 * cBarPrime * t
        val rT = -sin(Math.toRadians(2.0 * dTheta)) * rC

        val termL = dLPrime / (kL * sL)
        val termC = dCPrime / (kC * sC)
        val termH = dHPrime / (kH * sH)

        val dE2 = termL * termL + termC * termC + termH * termH + rT * termC * termH
        return sqrt(dE2.coerceAtLeast(0.0)).toFloat()
    }

    private fun labF(t: Double): Double =
        if (t > CIE_EPSILON) cbrt(t) else (CIE_KAPPA * t + 16.0) / 116.0

    private fun hueAngleDegrees(aPrime: Double, b: Double): Double {
        if (aPrime == 0.0 && b == 0.0) return 0.0
        val deg = Math.toDegrees(atan2(b, aPrime))
        return if (deg >= 0.0) deg else deg + 360.0
    }
}
