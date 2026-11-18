package com.alazhartex.shadecart.ui.navigation

import android.net.Uri

/**
 * Centralized Compose Navigation route patterns and URI-encoded argument builders.
 */
object NavRoutes {
    const val DIGITAL_SHADE_CARD = "shade_cart?cartId={cartId}"
    const val CART_CAPTURE = "capture"
    const val CART_EDIT = "edit?imagePath={imagePath}&cartId={cartId}"
    const val PALETTE_EDITOR = "palette_editor?cartId={cartId}"
    const val MILL_HUB = "mill_hub"

    fun digitalShadeCard(cartId: String? = null): String =
        if (cartId != null) "shade_cart?cartId=${Uri.encode(cartId)}" else "shade_cart"

    fun cartEdit(imagePath: String? = null, cartId: String? = null): String {
        val params = buildList {
            if (!imagePath.isNullOrBlank()) add("imagePath=${Uri.encode(imagePath)}")
            if (!cartId.isNullOrBlank()) add("cartId=${Uri.encode(cartId)}")
        }
        return if (params.isEmpty()) "edit" else "edit?${params.joinToString("&")}"
    }

    fun paletteEditor(cartId: String? = null): String =
        if (cartId != null) "palette_editor?cartId=${Uri.encode(cartId)}" else "palette_editor"
}
