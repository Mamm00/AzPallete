package com.alazhartex.shadecart.domain.export

import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.ltrIsolate
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

@Serializable
private data class ProcreateColorEntry(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val alpha: Float = 1.0f,
    val colorSpace: Int = 0,
)

@Serializable
private data class ProcreateSwatchesFile(
    val name: String,
    val swatches: List<ProcreateColorEntry?>,
)

/**
 * Pure Kotlin export formatters for Al AzharTex Digital Shade Carts:
 * - `.gpl` GIMP Palette (B1)
 * - `.swatches` Procreate zipped JSON palette (B1)
 * - `.csv` spreadsheet export (B1)
 * - Structured WhatsApp & Buyer Inquiry messages (B1 / B2)
 * - Shared zigzag/pinked perimeter geometry coordinates for UI, PDF & HTML rendering.
 */
object ShadeCartFormatters {

    private val json = Json { encodeDefaults = true }

    fun buildPinkedPerimeterCoords(
        width: Float,
        height: Float,
        amp: Float = height * 0.06f,
        tooth: Float = width / 13f,
    ): List<Pair<Float, Float>> {
        val bodyLeft = amp
        val bodyRight = width - amp
        val bodyTop = amp
        val bodyBottom = height - amp
        val points = ArrayList<Pair<Float, Float>>(64)
        points.add(bodyLeft to bodyTop)

        var x = bodyLeft
        while (x < bodyRight - 0.5f) {
            val tipX = min(x + tooth / 2f, bodyRight)
            val endX = min(x + tooth, bodyRight)
            points.add(tipX to 0f)
            points.add(endX to bodyTop)
            x = endX
        }

        var y = bodyTop
        while (y < bodyBottom - 0.5f) {
            val tipY = min(y + tooth / 2f, bodyBottom)
            val endY = min(y + tooth, bodyBottom)
            points.add(width to tipY)
            points.add(bodyRight to endY)
            y = endY
        }

        x = bodyRight
        while (x > bodyLeft + 0.5f) {
            val tipX = max(x - tooth / 2f, bodyLeft)
            val endX = max(x - tooth, bodyLeft)
            points.add(tipX to height)
            points.add(endX to bodyBottom)
            x = endX
        }

        y = bodyBottom
        while (y > bodyTop + 0.5f) {
            val tipY = max(y - tooth / 2f, bodyTop)
            val endY = max(y - tooth, bodyTop)
            points.add(0f to tipY)
            points.add(bodyLeft to endY)
            y = endY
        }

        return points
    }

    fun toGimpPaletteText(cart: ShadeCartModel): String = buildString {
        appendLine("GIMP Palette")
        appendLine("Name: Al AzharTex - ${cart.fabricName} (${cart.fabricCode})")
        appendLine("Columns: 2")
        appendLine("# ${cart.weightGsm} GSM | ${cart.widthCm} cm")
        cart.swatches.sortedBy { it.positionOrder }.forEach { sw ->
            val argb = HexColorValidator.parseArgbOrNull(sw.colorHex) ?: 0xFF334155.toInt()
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val name = "#${sw.swatchNumber} ${sw.colorName.ifBlank { sw.colorHex }}"
            appendLine(String.format(Locale.US, "%3d %3d %3d\t%s", r, g, b, name))
        }
    }

    fun toProcreateSwatchesZipBytes(cart: ShadeCartModel): ByteArray {
        val entries = cart.swatches.sortedBy { it.positionOrder }.map { sw ->
            val argb = HexColorValidator.parseArgbOrNull(sw.colorHex) ?: 0xFF334155.toInt()
            val (h, s, v) = rgbToHsv01(argb)
            ProcreateColorEntry(hue = h, saturation = s, brightness = v)
        }
        val payload = listOf(
            ProcreateSwatchesFile(
                name = "${cart.fabricName} (${cart.fabricCode})",
                swatches = entries,
            ),
        )
        val jsonBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("Swatches.json").apply {
                // Fixed deterministic timestamp for repeatable exports & golden tests
                time = 1_700_000_000_000L
            }
            zos.putNextEntry(entry)
            zos.write(jsonBytes)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    fun toCsvText(cart: ShadeCartModel): String = buildString {
        appendLine("shade_number,color_name,hex,lab_l,lab_a,lab_b,roll_code,warehouse_shed,available_meters,price_per_meter")
        cart.swatches.sortedBy { it.positionOrder }.forEach { sw ->
            val roll = sw.linkedRoll
            val avail = roll?.availableMeters?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val price = sw.pricePerMeter?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            appendLine(
                listOf(
                    csvEscape(sw.swatchNumber),
                    csvEscape(sw.colorName),
                    csvEscape(sw.colorHex.uppercase(Locale.US)),
                    String.format(Locale.US, "%.2f", sw.labL),
                    String.format(Locale.US, "%.2f", sw.labA),
                    String.format(Locale.US, "%.2f", sw.labB),
                    csvEscape(roll?.rollCode ?: ""),
                    csvEscape(roll?.warehouseShed ?: ""),
                    avail,
                    price,
                ).joinToString(","),
            )
        }
    }

    fun toWhatsAppShareCaption(
        cart: ShadeCartModel,
        qrVerificationUrl: String = "azpallete://cart/${cart.id}",
    ): String {
        val linkedRolls = cart.swatches.mapNotNull { it.linkedRoll }.distinctBy { it.id }
        val totalMeters = linkedRolls.sumOf { it.availableMeters }
        val shadesSummary = cart.swatches
            .sortedBy { it.positionOrder }
            .take(10)
            .joinToString(", ") { "#${it.swatchNumber} ${it.colorHex.uppercase(Locale.US)}".ltrIsolate() }

        return buildString {
            appendLine("${cart.fabricName} (${cart.fabricCode.ltrIsolate()}) · ${cart.weightGsm} GSM · ${cart.widthCm} cm")
            if (shadesSummary.isNotBlank()) {
                appendLine("Shades: $shadesSummary")
            }
            appendLine(
                "In-stock: ${linkedRolls.size} rolls · ${String.format(Locale.US, "%.0f", totalMeters)} m",
            )
            append("Verify live stock: ${qrVerificationUrl.ltrIsolate()}")
        }
    }

    fun toBuyerInquiryMessage(
        companyName: String,
        favoritedSwatches: List<SwatchModel>,
        cartsById: Map<String, ShadeCartModel>,
    ): String {
        val grouped = favoritedSwatches.groupBy { it.shadeCartId }
        return buildString {
            appendLine("Inquiry from $companyName:")
            grouped.forEach { (cartId, swatches) ->
                val cart = cartsById[cartId]
                val header = if (cart != null) {
                    "${cart.fabricName} (${cart.fabricCode}) · ${cart.weightGsm} g/m² · ${cart.widthCm} cm"
                } else {
                    "Shade Cart $cartId"
                }
                appendLine(header)
                swatches.sortedBy { it.positionOrder }.forEach { sw ->
                    appendLine("  - Shade #${sw.swatchNumber}: ${sw.colorHex.uppercase(Locale.US)} (${sw.colorName})")
                }
            }
            append("Please confirm availability and pricing.")
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun rgbToHsv01(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val hue = when {
            delta < 1e-6f -> 0f
            maxC == r -> (((g - b) / delta) % 6f) / 6f
            maxC == g -> (((b - r) / delta) + 2f) / 6f
            else -> (((r - g) / delta) + 4f) / 6f
        }.let { if (it < 0f) it + 1f else it }

        val sat = if (maxC <= 1e-6f) 0f else delta / maxC
        return Triple(hue.coerceIn(0f, 1f), sat.coerceIn(0f, 1f), maxC.coerceIn(0f, 1f))
    }
}
