package com.alazhartex.shadecart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.ui.screens.AlAzharTexShadeCartApp
import com.alazhartex.shadecart.ui.screens.AppShellViewModel
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val shellViewModel: AppShellViewModel = hiltViewModel()
            val shellState by shellViewModel.shellState.collectAsStateWithLifecycle()

            AlAzharTexTheme(preferences = shellState.themePreferences) {
                AlAzharTexShadeCartApp(shellViewModel = shellViewModel)
            }
        }
    }
}
