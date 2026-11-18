package com.alazhartex.shadecart.domain.vision

import com.alazhartex.shadecart.data.model.NormalizedRect

data class OcrTextBlock(
    val text: String,
    val bounds: NormalizedRect,
)

data class ParsedHeaderMetadata(
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int?,
    val widthCm: Int?,
    val rawHeaderText: String,
)

data class ShadeCartOcrResult(
    val header: ParsedHeaderMetadata,
    val numericSwatchLabels: List<OcrTextBlock>,
    val rawFullText: String,
)

/**
 * Pure Kotlin OCR parser for Al AzharTex physical A4 shade carts.
 *
 * Extracts:
 * - Header block (top ~30% of cart): Fabric Name, Code, Weight (GSM / g/m²), Width (cm)
 * - Body numeric swatch labels (`5`, `7`, `11`, `12`, ...) with normalized bounding boxes.
 */
class ShadeCartOcrParser {

    companion object {
        private val FABRIC_NAME_REGEX = Regex(
            """(?i)(?:fabric\s*name|item\s*name|fabric|quality)\s*[:\-]\s*([^\r\n]+?)(?=\s+(?:(?:fabric|item)\s*code|code|ref|weight|width)\s*[:\-]|$)""",
        )
        private val FABRIC_CODE_REGEX = Regex(
            """(?i)(?:fabric\s*code|item\s*code|code|ref|sku|art(?:icle)?(?:\s*no)?)\s*[:.\-]\s*([A-Z0-9][A-Z0-9\-_/]{1,20})""",
        )
        private val WEIGHT_GSM_REGEX = Regex(
            """(?i)(?:weight\s*[:\-]\s*)?(\d{2,4})\s*(?:gsm|g/m2|g/m²|gr/m2|gm2)\b""",
        )
        private val WEIGHT_LABEL_ONLY_REGEX = Regex(
            """(?i)weight\s*[:\-]\s*(\d{2,4})\b""",
        )
        private val WIDTH_CM_REGEX = Regex(
            """(?i)(?:width\s*[:\-]\s*)?(\d{2,3})\s*cm\b""",
        )
        private val WIDTH_LABEL_ONLY_REGEX = Regex(
            """(?i)width\s*[:\-]\s*(\d{2,3})\b""",
        )
        private val NUMERIC_SWATCH_REGEX = Regex("""^\d{1,4}$""")
    }

    fun parse(blocks: List<OcrTextBlock>): ShadeCartOcrResult {
        val headerBlocks = blocks
            .filter { it.bounds.top < 0.30f }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))

        val headerText = headerBlocks.joinToString("\n") { it.text.trim() }
        val allText = blocks.joinToString("\n") { it.text.trim() }
        val parseSource = headerText.ifBlank { allText }

        val fabricName = extractFabricName(parseSource, headerBlocks)
        val fabricCode = FABRIC_CODE_REGEX.find(parseSource)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val weightGsm = WEIGHT_GSM_REGEX.find(parseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: WEIGHT_LABEL_ONLY_REGEX.find(parseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val widthCm = WIDTH_CM_REGEX.find(parseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: WIDTH_LABEL_ONLY_REGEX.find(parseSource)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val numericLabels = blocks
            .filter { it.bounds.top >= 0.21f }
            .flatMap { block -> splitNumericCandidates(block) }

        return ShadeCartOcrResult(
            header = ParsedHeaderMetadata(
                fabricName = fabricName,
                fabricCode = fabricCode,
                weightGsm = weightGsm,
                widthCm = widthCm,
                rawHeaderText = headerText,
            ),
            numericSwatchLabels = numericLabels,
            rawFullText = allText,
        )
    }

    private fun extractFabricName(text: String, headerBlocks: List<OcrTextBlock>): String {
        FABRIC_NAME_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { match ->
            if (match.isNotBlank()) return match
        }
        // Fallback: first descriptive line in header that isn't branding or purely numeric/metric
        val ignoreTokens = listOf("AL AZHAR", "FINE FABRICS", "DIGITAL SHADE", "COLLECTION", "WEIGHT", "WIDTH", "CODE")
        return headerBlocks
            .map { it.text.trim() }
            .firstOrNull { line ->
                line.length >= 3 &&
                    ignoreTokens.none { token -> line.uppercase().contains(token) } &&
                    !line.matches(Regex("""^[\d\s.\-/]+$"""))
            }.orEmpty()
    }

    private fun splitNumericCandidates(block: OcrTextBlock): List<OcrTextBlock> {
        val cleaned = block.text.trim()
        if (NUMERIC_SWATCH_REGEX.matches(cleaned)) {
            return listOf(block.copy(text = cleaned))
        }
        // Sometimes OCR merges adjacent short tokens like "5" or "#11"
        val stripped = cleaned.removePrefix("#").trim()
        if (NUMERIC_SWATCH_REGEX.matches(stripped)) {
            return listOf(block.copy(text = stripped))
        }
        return emptyList()
    }
}
