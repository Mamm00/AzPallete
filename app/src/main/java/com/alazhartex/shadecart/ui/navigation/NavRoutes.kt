package com.alazhartex.shadecart.ui.navigation

import android.net.Uri

/**
 * Centralized Compose Navigation route patterns and URI-encoded argument builders.
 *
 * Keeps route parsing strictly within the UI navigation graph rather than ViewModels
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.4).
 */
object NavRoutes {
    const val DIGITAL_SHADE_CARD = "shade_cart?cartId={cartId}"
    const val CART_CAPTURE = "capture"
    const val CART_EDIT = "edit?imagePath={imagePath}&cartId={cartId}"
    const val PALETTE_EDITOR = "palette_editor?cartId={cartId}"

    /** Builds the Digital Shade Card route optionally pre-selecting [cartId]. */
    fun digitalShadeCard(cartId: String? = null): String =
        if (cartId != null) "shade_cart?cartId=${Uri.encode(cartId)}" else "shade_cart"

    /** Builds the non-destructive card editor route for [imagePath] and/or [cartId]. */
    fun cartEdit(imagePath: String? = null, cartId: String? = null): String {
        val params = buildList {
            if (!imagePath.isNullOrBlank()) add("imagePath=${Uri.encode(imagePath)}")
            if (!cartId.isNullOrBlank()) add("cartId=${Uri.encode(cartId)}")
        }
        return if (params.isEmpty()) "edit" else "edit?${params.joinToString("&")}"
    }

    /** Builds the Palette & HEX Editor route optionally scoped to [cartId]. */
    fun paletteEditor(cartId: String? = null): String =
        if (cartId != null) "palette_editor?cartId=${Uri.encode(cartId)}" else "palette_editor"
}
