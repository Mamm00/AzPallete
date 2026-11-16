package com.alazhartex.shadecart.domain.imaging

import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.vision.OcrTextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinkedSwatchSamplerTest {

    @Test
    fun `inner sample region insets 18 percent per side to skip pinked teeth`() {
        val outer = NormalizedRect(left = 0.10f, top = 0.20f, right = 0.50f, bottom = 0.40f)
        val inner = PinkedSwatchSampler.computeInnerSampleRegion(outer)

        assertEquals(0.172f, inner.left, 0.001f)
        assertEquals(0.236f, inner.top, 0.001f)
        assertEquals(0.428f, inner.right, 0.001f)
        assertEquals(0.364f, inner.bottom, 0.001f)
        assertTrue(inner.width() < outer.width())
        assertTrue(inner.height() < outer.height())
    }

    @Test
    fun `filtered median rejects bright paper pixels mixed into pinked swatch border`() {
        val navy = 0xFF102A71.toInt()
        val whitePaper = 0xFFFAFAFA.toInt()
        // Even with 40% white cardstock glare, paper pixels are filtered out
        val pixels = IntArray(100) { idx -> if (idx < 60) navy else whitePaper }

        val median = PinkedSwatchSampler.computeFilteredMedianArgb(pixels)
        assertEquals("#102A71", PinkedSwatchSampler.formatHex(median))
    }

    @Test
    fun `two-column label pairing assigns nearest same-column OCR numbers and sequential fallbacks`() {
        val boxes = listOf(
            NormalizedRect(0.16f, 0.28f, 0.45f, 0.40f),
            NormalizedRect(0.62f, 0.28f, 0.91f, 0.40f),
            NormalizedRect(0.16f, 0.44f, 0.45f, 0.56f),
        )
        val labels = listOf(
            OcrTextBlock("5", NormalizedRect(0.08f, 0.33f, 0.13f, 0.36f)),
            OcrTextBlock("11", NormalizedRect(0.55f, 0.33f, 0.60f, 0.36f)),
        )

        val assigned = TwoColumnSwatchDetector.pairNumericLabels(boxes, labels)
        assertEquals(listOf("5", "11", "1"), assigned)
    }
}
