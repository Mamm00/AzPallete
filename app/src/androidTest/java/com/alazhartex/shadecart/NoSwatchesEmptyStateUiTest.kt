package com.alazhartex.shadecart

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.DatabaseSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NoSwatchesEmptyStateUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
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
    fun noSwatches_showsEmptyStateAndManualAddCta_validatesHexAndSavesSwatch() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Empty shade cart tab shows empty state & "Add swatch manually" CTA
            composeRule.waitUntilAtLeastOneExists(hasTestTag("no_swatches_empty_state"), timeoutMillis = 10_000)
            composeRule.onNodeWithText("No Swatches Yet").assertIsDisplayed()
            composeRule.onNodeWithTag("add_swatch_manually_cta").assertIsDisplayed()

            // 2. Tap manual add CTA -> navigates to PaletteEditorScreen
            composeRule.onNodeWithTag("add_swatch_manually_cta").performClick()
            composeRule.waitUntilAtLeastOneExists(hasTestTag("palette_editor_screen"), timeoutMillis = 10_000)

            // 3. Invalid HEX input disables Apply button & shows inline error
            composeRule.onNodeWithTag("hex_input").performTextReplacement("#GGGGGG")
            composeRule.onNodeWithTag("hex_error_text").assertIsDisplayed()
            composeRule.onNodeWithTag("apply_hex_button").assertIsNotEnabled()

            // 4. Valid HEX re-enables Apply button; apply & save back to Shade Cart
            composeRule.onNodeWithTag("hex_input").performTextReplacement("#102A71")
            composeRule.onNodeWithTag("apply_hex_button").assertIsEnabled()
            composeRule.onNodeWithTag("apply_hex_button").performClick()

            composeRule.onNodeWithTag("save_palette_button").performClick()
            composeRule.waitUntilAtLeastOneExists(hasTestTag("swatch_chip_1"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("swatch_chip_1").assertIsDisplayed()
        }
    }
}
