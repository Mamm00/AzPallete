package com.alazhartex.shadecart.domain.vision

import com.alazhartex.shadecart.data.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeCartOcrParserTest {

    @Test
    fun `parses metric header lines including GSM and cm dimensions`() {
        val headerBlocks = listOf(
            OcrTextBlock(
                text = "AL AZHAR TEX",
                bounds = NormalizedRect(0.08f, 0.04f, 0.50f, 0.08f),
            ),
            OcrTextBlock(
                text = "Silk 180",
                bounds = NormalizedRect(0.08f, 0.10f, 0.40f, 0.13f),
            ),
            OcrTextBlock(
                text = "Code: SLK-180",
                bounds = NormalizedRect(0.08f, 0.14f, 0.45f, 0.17f),
            ),
            OcrTextBlock(
                text = "Weight: 180 GSM   Width: 150 cm",
                bounds = NormalizedRect(0.08f, 0.175f, 0.85f, 0.21f),
            ),
            OcrTextBlock(
                text = "5",
                bounds = NormalizedRect(0.11f, 0.36f, 0.15f, 0.39f),
            ),
            OcrTextBlock(
                text = "11",
                bounds = NormalizedRect(0.57f, 0.36f, 0.62f, 0.39f),
            ),
            OcrTextBlock(
                text = "24",
                bounds = NormalizedRect(0.57f, 0.88f, 0.62f, 0.91f),
            ),
        )

        val result = ShadeCartOcrParser().parse(headerBlocks)
        assertEquals("Silk 180", result.header.fabricName)
        assertEquals("SLK-180", result.header.fabricCode)
        assertEquals(180, result.header.weightGsm)
        assertEquals(150, result.header.widthCm)
        assertEquals(listOf("5", "11", "24"), result.numericSwatchLabels.map { it.text })
    }

    @Test
    fun `parses g per m2 metric notation and hash-prefixed swatch labels`() {
        val blocks = listOf(
            OcrTextBlock(
                text = "Cotton Poplin 130",
                bounds = NormalizedRect(0.06f, 0.08f, 0.55f, 0.12f),
            ),
            OcrTextBlock(
                text = "Art. CTN-130",
                bounds = NormalizedRect(0.06f, 0.13f, 0.45f, 0.16f),
            ),
            OcrTextBlock(
                text = "130 g/m2   145cm",
                bounds = NormalizedRect(0.06f, 0.17f, 0.55f, 0.20f),
            ),
            OcrTextBlock(
                text = "#07",
                bounds = NormalizedRect(0.10f, 0.42f, 0.16f, 0.45f),
            ),
        )

        val result = ShadeCartOcrParser().parse(blocks)
        assertEquals("Cotton Poplin 130", result.header.fabricName)
        assertEquals("CTN-130", result.header.fabricCode)
        assertEquals(130, result.header.weightGsm)
        assertEquals(145, result.header.widthCm)
        assertTrue(result.numericSwatchLabels.any { it.text == "07" })
    }

    @Test
    fun `parses physical card ITEM NAME ITEM CODE and unitless WIDTH labels`() {
        val blocks = listOf(
            OcrTextBlock(
                text = "ITEM NAME : Lycra Biscotte",
                bounds = NormalizedRect(0.05f, 0.06f, 0.45f, 0.10f),
            ),
            OcrTextBlock(
                text = "ITEM CODE : LYC-160",
                bounds = NormalizedRect(0.05f, 0.11f, 0.45f, 0.14f),
            ),
            OcrTextBlock(
                text = "WEIGHT :   WIDTH : 160",
                bounds = NormalizedRect(0.05f, 0.15f, 0.75f, 0.18f),
            ),
        )

        val result = ShadeCartOcrParser().parse(blocks)
        assertEquals("Lycra Biscotte", result.header.fabricName)
        assertEquals("LYC-160", result.header.fabricCode)
        assertEquals(160, result.header.widthCm)
    }
}
