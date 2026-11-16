package com.alazhartex.shadecart

import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.shadecart.ui.screens.capture.CameraPermissionStatus
import com.alazhartex.shadecart.ui.screens.capture.CartCaptureScreen
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PermissionDeniedUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun permissionDenied_displaysRationale_andPermanentDenialTriggersSettingsDeepLink() {
        var status by mutableStateOf(CameraPermissionStatus.DENIED_SHOW_RATIONALE)

        composeRule.setContent {
            AlAzharTexTheme {
                CartCaptureScreen(
                    permissionStatusOverride = status,
                    onImageReadyForEdit = {},
                    viewModel = hiltViewModel(),
                )
            }
        }

        // First denial: rationale & Allow Camera CTA visible
        composeRule.onNodeWithTag("camera_permission_rationale_card").assertIsDisplayed()
        composeRule.onNodeWithText("Camera Access Needed").assertIsDisplayed()
        composeRule.onNodeWithTag("allow_camera_button").assertIsDisplayed()

        // Permanent denial: "Open Settings" deep link visible and launches Settings intent
        composeRule.runOnIdle {
            status = CameraPermissionStatus.PERMANENTLY_DENIED
        }
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()

        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val expectedIntent = allOf(
            hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            hasData(Uri.fromParts("package", targetPackage, null)),
        )
        Intents.intending(expectedIntent)
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        composeRule.onNodeWithTag("open_settings_button").performClick()

        Intents.intended(expectedIntent)
    }
}
