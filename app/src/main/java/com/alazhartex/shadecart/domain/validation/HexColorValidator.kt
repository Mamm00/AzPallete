package com.alazhartex.shadecart.domain.validation

/**
 * Validates and normalizes HEX color codes entered manually or extracted from swatches.
 *
 * Enforces the exact specification regex: `^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$`
 */
object HexColorValidator {

    val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$")

    sealed interface ValidationResult {
        data class Valid(
            val normalizedHex: String,
            val argb: Int,
        ) : ValidationResult

        data class Invalid(
            val message: String,
        ) : ValidationResult
    }

    fun isValid(input: String): Boolean = HEX_COLOR_REGEX.matches(input)

    fun validate(input: String): ValidationResult {
        if (input.isEmpty()) {
            return ValidationResult.Invalid("Enter a HEX color such as #102A71 or #F0A")
        }
        if (!input.startsWith('#')) {
            return ValidationResult.Invalid("HEX code must start with '#'")
        }
        if (!HEX_COLOR_REGEX.matches(input)) {
            return ValidationResult.Invalid("Use #RGB or #RRGGBB with hex digits 0-9, A-F")
        }
        val normalized = normalizeToSixDigitHex(input)
        val rgb = normalized.substring(1).toInt(16)
        val argb = (0xFF shl 24) or rgb
        return ValidationResult.Valid(normalizedHex = normalized, argb = argb)
    }

    /**
     * Expands `#RGB` to `#RRGGBB` and uppercases hex digits. Caller must validate first or handle fallback.
     */
    fun normalizeToSixDigitHex(validHex: String): String {
        require(isValid(validHex)) { "Invalid HEX color: $validHex" }
        val body = validHex.substring(1).uppercase()
        return if (body.length == 3) {
            buildString(7) {
                append('#')
                body.forEach { ch ->
                    append(ch)
                    append(ch)
                }
            }
        } else {
            "#$body"
        }
    }

    fun parseArgbOrNull(input: String): Int? = when (val res = validate(input)) {
        is ValidationResult.Valid -> res.argb
        is ValidationResult.Invalid -> null
    }
}
