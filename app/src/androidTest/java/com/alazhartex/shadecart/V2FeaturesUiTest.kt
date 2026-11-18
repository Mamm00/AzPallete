package com.alazhartex.shadecart

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
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
class V2FeaturesUiTest {

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
    fun millHubTabs_displayReverseFinderWarehouseRollsAndAskInsights() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntilAtLeastOneExists(hasTestTag("nav_tab_mill"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("nav_tab_mill").performClick()

            composeRule.waitUntilAtLeastOneExists(hasTestTag("mill_inventory_hub"), timeoutMillis = 10_000)
            composeRule.onNodeWithText("Reverse Shade Finder", substring = true).assertIsDisplayed()

            composeRule.onNodeWithText("Rolls & Lots").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithText("RL-SLK-005", substring = true).assertIsDisplayed()
                }.isSuccess
            }

            composeRule.onNodeWithText("Ask & Stats").performClick()
            composeRule.onNodeWithText("Ask AzPallete").assertIsDisplayed()
        }
    }

    @Test
    fun buyerMode_locksInternalTabsAndRequiresPinToUnlock() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntilAtLeastOneExists(hasTestTag("theme_settings_button"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("theme_settings_button").performClick()

            composeRule.waitUntilAtLeastOneExists(hasTestTag("dynamic_color_switch"), timeoutMillis = 5_000)
            composeRule.onNodeWithText("Buyer Mode").performScrollTo().performClick()
            composeRule.onNodeWithText("Lock & Showcase").performClick()

            // Buyer Showcase screen isolates internal mill tabs
            composeRule.waitUntilAtLeastOneExists(hasTestTag("buyer_showcase_screen"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("nav_tab_mill").assertDoesNotExist()

            // Wrong PIN rejects unlock
            composeRule.onNodeWithTag("buyer_unlock_pin_button").performClick()
            composeRule.onNodeWithTag("buyer_exit_pin_input").performTextReplacement("9999")
            Espresso.closeSoftKeyboard()
            composeRule.onNodeWithTag("buyer_exit_confirm_button").performClick()
            composeRule.onNodeWithText("Incorrect 4-digit PIN.").assertIsDisplayed()

            // Valid default PIN unlocks back to full mill shell
            composeRule.onNodeWithTag("buyer_exit_pin_input").performTextReplacement("1234")
            Espresso.closeSoftKeyboard()
            composeRule.onNodeWithTag("buyer_exit_confirm_button").performClick()

            composeRule.waitUntilAtLeastOneExists(hasTestTag("nav_tab_mill"), timeoutMillis = 10_000)
            composeRule.onNodeWithTag("nav_tab_mill").assertIsDisplayed()
        }
    }
}
