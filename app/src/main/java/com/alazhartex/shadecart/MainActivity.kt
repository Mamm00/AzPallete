package com.alazhartex.shadecart

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.domain.model.ScannedBarcodeTarget
import com.alazhartex.shadecart.domain.vision.AzPalleteDeepLinkParser
import com.alazhartex.shadecart.ui.navigation.NavRoutes
import com.alazhartex.shadecart.ui.screens.AlAzharTexShadeCartApp
import com.alazhartex.shadecart.ui.screens.AppShellViewModel
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity Compose entry point extending [AppCompatActivity] so Material 3 Compose UI
 * integrates cleanly with AppCompat per-app locale switching (`AppCompatDelegate`), deep links,
 * and NFC `azpallete://cart/{id}` intents.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var pendingDeepLinkCartId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val shellViewModel: AppShellViewModel = hiltViewModel()
            val shellState by shellViewModel.shellState.collectAsStateWithLifecycle()

            AlAzharTexTheme(preferences = shellState.themePreferences) {
                val startRoute = pendingDeepLinkCartId?.let { NavRoutes.digitalShadeCard(it) }
                    ?: NavRoutes.DIGITAL_SHADE_CARD
                AlAzharTexShadeCartApp(
                    shellViewModel = shellViewModel,
                    initialRoute = startRoute,
                    externalDeepLinkCartId = pendingDeepLinkCartId,
                    onConsumedDeepLink = { pendingDeepLinkCartId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val target = AzPalleteDeepLinkParser.extractTargetFromNfcIntent(intent)
        if (target is ScannedBarcodeTarget.CartDeepLink) {
            pendingDeepLinkCartId = target.cartId
        }
    }
}
