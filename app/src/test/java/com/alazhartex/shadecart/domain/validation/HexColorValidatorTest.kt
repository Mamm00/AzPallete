package com.alazhartex.shadecart.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexColorValidatorTest {

    @Test
    fun `valid 6-digit uppercase and lowercase HEX pass and normalize to uppercase`() {
        val upper = HexColorValidator.validate("#102A71")
        assertTrue(upper is HexColorValidator.ValidationResult.Valid)
        upper as HexColorValidator.ValidationResult.Valid
        assertEquals("#102A71", upper.normalizedHex)
        assertEquals(0xFF102A71.toInt(), upper.argb)

        val lower = HexColorValidator.validate("#cd1820")
        assertTrue(lower is HexColorValidator.ValidationResult.Valid)
        lower as HexColorValidator.ValidationResult.Valid
        assertEquals("#CD1820", lower.normalizedHex)
        assertEquals(0xFFCD1820.toInt(), lower.argb)

        val mixed = HexColorValidator.validate("#a1B2c3")
        assertTrue(mixed is HexColorValidator.ValidationResult.Valid)
        assertEquals("#A1B2C3", (mixed as HexColorValidator.ValidationResult.Valid).normalizedHex)
    }

    @Test
    fun `valid 3-digit shorthand HEX pass and expand to 6-digit uppercase`() {
        val shortLower = HexColorValidator.validate("#f0a")
        assertTrue(shortLower is HexColorValidator.ValidationResult.Valid)
        shortLower as HexColorValidator.ValidationResult.Valid
        assertEquals("#FF00AA", shortLower.normalizedHex)
        assertEquals(0xFFFF00AA.toInt(), shortLower.argb)

        val shortUpper = HexColorValidator.validate("#1A9")
        assertTrue(shortUpper is HexColorValidator.ValidationResult.Valid)
        assertEquals("#11AA99", (shortUpper as HexColorValidator.ValidationResult.Valid).normalizedHex)
    }

    @Test
    fun `missing leading hash is rejected`() {
        assertFalse(HexColorValidator.isValid("102A71"))
        assertFalse(HexColorValidator.isValid("FFF"))
        val res = HexColorValidator.validate("102A71")
        assertTrue(res is HexColorValidator.ValidationResult.Invalid)
        assertTrue((res as HexColorValidator.ValidationResult.Invalid).message.contains("'#'"))
        assertNull(HexColorValidator.parseArgbOrNull("102A71"))
    }

    @Test
    fun `invalid hex characters are rejected`() {
        listOf("#GGGGGG", "#12345Z", "#12 345", "#-12345", "#12G", "#XYZ").forEach { candidate ->
            assertFalse("Expected invalid for $candidate", HexColorValidator.isValid(candidate))
            assertTrue(HexColorValidator.validate(candidate) is HexColorValidator.ValidationResult.Invalid)
        }
    }

    @Test
    fun `wrong lengths and empty inputs are rejected`() {
        listOf("", "#", "#1", "#12", "#1234", "#12345", "#1234567", "#12345678", "   ").forEach { candidate ->
            assertFalse("Expected invalid length for '$candidate'", HexColorValidator.isValid(candidate))
            assertTrue(HexColorValidator.validate(candidate) is HexColorValidator.ValidationResult.Invalid)
        }
        assertNotNull(HexColorValidator.parseArgbOrNull("#000"))
    }
}
