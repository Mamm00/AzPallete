package com.alazhartex.shadecart

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.ui.screens.shadecard.DigitalShadeCardScreen
import com.alazhartex.shadecart.ui.screens.shadecard.DigitalShadeCardUiState
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure stateless Jetpack Compose UI test exercising [DigitalShadeCardScreen] directly with
 * `createComposeRule()` and `Modifier.testTag()` assertions without Hilt or Room dependencies
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 1.2 & Chapter 11.4).
 */
@RunWith(AndroidJUnit4::class)
class StatelessShadeCardScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptySwatchesState_rendersEmptyCardAndTriggersManualAddCallback() {
        var clicked = false
        composeRule.setContent {
            AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
                DigitalShadeCardScreen(
                    uiState = DigitalShadeCardUiState(isLoading = false, activeCart = null),
                    onSelectCart = {},
                    onOpenCapture = {},
                    onEditCartImage = {},
                    onOpenPaletteEditor = {},
                    onAddSwatchManually = { clicked = true },
                    onLinkSwatchToRoll = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("no_swatches_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("add_swatch_manually_cta").performClick()
        assertTrue(clicked)
    }

    @Test
    fun populatedCart_displaysFabricSpecsAndSwatchChips() {
        val sampleCart = ShadeCartModel(
            id = "cart-ui-test",
            fabricTypeId = "fab-silk-180",
            fabricName = "Silk 180",
            fabricCode = "SLK-180",
            weightGsm = 180,
            widthCm = 150,
            originalImagePath = null,
            editedImagePath = null,
            editStackJson = "{}",
            syncStatus = SyncState.SYNCED,
            version = 3L,
            swatches = listOf(
                SwatchModel(
                    id = "sw-5",
                    shadeCartId = "cart-ui-test",
                    fabricTypeId = "fab-silk-180",
                    swatchNumber = "5",
                    colorHex = "#102A71",
                    colorName = "Azhar Navy",
                    positionOrder = 0,
                ),
            ),
        )

        composeRule.setContent {
            AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
                DigitalShadeCardScreen(
                    uiState = DigitalShadeCardUiState(
                        carts = listOf(sampleCart),
                        selectedCartId = sampleCart.id,
                        activeCart = sampleCart,
                        isLoading = false,
                    ),
                    onSelectCart = {},
                    onOpenCapture = {},
                    onEditCartImage = {},
                    onOpenPaletteEditor = {},
                    onAddSwatchManually = {},
                    onLinkSwatchToRoll = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Silk 180").assertIsDisplayed()
        composeRule.onNodeWithText("Weight: 180 GSM (g/m²)").assertIsDisplayed()
        composeRule.onNodeWithText("Width: 150 cm").assertIsDisplayed()
        composeRule.onNodeWithTag("swatch_chip_5").assertIsDisplayed()
    }
}
