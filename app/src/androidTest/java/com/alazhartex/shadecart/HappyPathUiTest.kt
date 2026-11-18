package com.alazhartex.shadecart

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.DatabaseSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class HappyPathUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var database: AlAzharTexDatabase

    @Inject
    lateinit var seeder: DatabaseSeeder

    @Before
    fun resetDatabase() {
        hiltRule.inject()
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            seeder.ensureSeeded()
        }
    }

    @Test
    fun captureSample_editOcrAndDetectSwatches_save_viewOnShadeCartTab() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntilAtLeastOneExists(hasTestTag("nav_tab_capture"), timeoutMillis = 10_000)

            // 1. Navigate to Capture tab & load the bundled A4 physical shade cart sample
            composeRule.onNodeWithTag("nav_tab_capture").performClick()
            composeRule.waitUntilAtLeastOneExists(hasTestTag("load_sample_cart_button"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("load_sample_cart_button").performClick()

            // 2. Arrive at Edit screen and wait for swatch detection + OCR analysis to finish
            composeRule.waitUntilAtLeastOneExists(hasTestTag("ocr_fabric_name_input"), timeoutMillis = 15_000)
            composeRule.waitUntilAtLeastOneExists(hasTestTag("save_shade_cart_button"), timeoutMillis = 15_000)
            composeRule.waitUntil(timeoutMillis = 20_000) {
                runCatching {
                    composeRule.onNodeWithTag("save_shade_cart_button").assertIsEnabled()
                }.isSuccess
            }

            // Ensure neither OCR/MLKit null dereference banner nor squeezed vertical Crop button appears
            assertTrue(
                composeRule.onAllNodesWithText("null object reference", substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
            assertTrue(
                composeRule.onAllNodesWithText("OCR/Swatch analysis issue", substring = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
            val boxesBounds = composeRule.onNodeWithTag("toggle_overlay_boxes_button")
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val cropBounds = composeRule.onNodeWithTag("toggle_crop_mode_button")
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Expected Boxes button wider than tall, got $boxesBounds", boxesBounds.width > boxesBounds.height)
            assertTrue("Expected Crop button wider than tall (not vertically wrapped), got $cropBounds", cropBounds.width > cropBounds.height)

            // Fill or confirm metric header values (resilient if offline emulator has no ML Kit OCR model)
            composeRule.onNodeWithTag("ocr_fabric_name_input").performScrollTo().performTextReplacement("Silk 180")
            composeRule.onNodeWithTag("ocr_fabric_code_input").performScrollTo().performTextReplacement("SLK-180")
            composeRule.onNodeWithTag("ocr_weight_input").performScrollTo().performTextReplacement("180")
            composeRule.onNodeWithTag("ocr_width_input").performScrollTo().performTextReplacement("150")
            Espresso.closeSoftKeyboard()

            // 3. Save Shade Cart -> navigates to Digital Shade Card tab
            composeRule.onNodeWithTag("save_shade_cart_button").performScrollTo().performClick()

            // 4. Verify Shade Cart tab displays saved metadata and detected swatch chips
            composeRule.waitUntilAtLeastOneExists(hasTestTag("digital_shade_card_screen"), timeoutMillis = 15_000)
            composeRule.waitUntilAtLeastOneExists(hasText("Silk 180"), timeoutMillis = 10_000)
            composeRule.onNodeWithText("Silk 180").assertIsDisplayed()
            composeRule.onNodeWithText("Weight: 180 GSM (g/m²)").assertIsDisplayed()
            composeRule.onNodeWithText("Width: 150 cm").assertIsDisplayed()

            // Assert at least one extracted swatch card is visible
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("swatch_chip_1", useUnmergedTree = false)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithTag("swatch_chip_5", useUnmergedTree = false)
                        .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
