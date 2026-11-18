package com.alazhartex.shadecart.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.repository.SyncDebugController
import com.alazhartex.shadecart.domain.usecase.ObserveSyncConflictsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveSyncQueueUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveThemePreferencesUseCase
import com.alazhartex.shadecart.domain.usecase.ResolveSyncConflictUseCase
import com.alazhartex.shadecart.domain.usecase.TriggerSyncUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateAccentPaletteUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateDynamicColorUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateThemeModeUseCase
import com.alazhartex.shadecart.ui.components.BrandLogoBadge
import com.alazhartex.shadecart.ui.components.ConflictBanner
import com.alazhartex.shadecart.ui.components.ThemeAndSyncSettingsSheet
import com.alazhartex.shadecart.ui.navigation.NavRoutes
import com.alazhartex.shadecart.ui.screens.capture.CameraPermissionStatus
import com.alazhartex.shadecart.ui.screens.capture.CartCaptureRoute
import com.alazhartex.shadecart.ui.screens.edit.CartEditRoute
import com.alazhartex.shadecart.ui.screens.palette.PaletteEditorRoute
import com.alazhartex.shadecart.ui.screens.shadecard.DigitalShadeCardRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable shell UI state combining DataStore theme preferences, unresolved conflicts,
 * and offline sync queue diagnostics (Pyre, Chapter 6.3 & Chapter 10.1).
 */
data class AppShellState(
    val themePreferences: ThemeSettings = ThemeSettings(),
    val conflicts: List<SyncConflictModel> = emptyList(),
    val syncQueue: List<SyncQueueEntryModel> = emptyList(),
)

/**
 * Application-shell `@HiltViewModel` delegating exclusively to domain UseCases and
 * [SyncDebugController], exposing a single reactive [StateFlow] via `stateIn(WhileSubscribed)`
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.3, 7.1, & 10.1).
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    observeThemePreferences: ObserveThemePreferencesUseCase,
    observeSyncConflicts: ObserveSyncConflictsUseCase,
    observeSyncQueue: ObserveSyncQueueUseCase,
    private val updateThemeMode: UpdateThemeModeUseCase,
    private val updateDynamicColor: UpdateDynamicColorUseCase,
    private val updateAccentPalette: UpdateAccentPaletteUseCase,
    private val resolveSyncConflict: ResolveSyncConflictUseCase,
    private val triggerSync: TriggerSyncUseCase,
    private val syncDebugController: SyncDebugController,
) : ViewModel() {

    val shellState: StateFlow<AppShellState> = combine(
        observeThemePreferences(),
        observeSyncConflicts(),
        observeSyncQueue(),
    ) { prefs, conflicts, queue ->
        AppShellState(
            themePreferences = prefs,
            conflicts = conflicts,
            syncQueue = queue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppShellState(),
    )

    fun setThemeMode(mode: ThemeModeSetting) {
        viewModelScope.launch { updateThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { updateDynamicColor(enabled) }
    }

    fun setAccentPalette(palette: AccentPaletteSetting) {
        viewModelScope.launch { updateAccentPalette(palette) }
    }

    fun resolveConflict(cartId: String, decision: ConflictDecision) {
        viewModelScope.launch {
            resolveSyncConflict(cartId, decision)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            triggerSync()
        }
    }

    fun armSimulatedConflict() {
        syncDebugController.armSimulatedConflict()
    }

    fun isConflictArmed(): Boolean = syncDebugController.isConflictArmed()
}

/**
 * Top-level Material 3 Scaffold and Compose Navigation container (`rememberNavController` +
 * `NavHost`). Navigation controller stays strictly inside UI composables, never inside ViewModels
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 5.3 & Chapter 6.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlAzharTexShadeCartApp(
    shellViewModel: AppShellViewModel,
    initialRoute: String = NavRoutes.DIGITAL_SHADE_CARD,
    cameraPermissionStatusOverride: CameraPermissionStatus? = null,
    onOpenSettingsOverride: (() -> Unit)? = null,
) {
    val shellState by shellViewModel.shellState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSettingsSheet by remember { mutableStateOf(false) }

    val activeConflict = shellState.conflicts.firstOrNull()
    LaunchedEffect(activeConflict?.cartId, activeConflict?.detectedAt) {
        if (activeConflict != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Conflict on ${activeConflict.fabricName}: keep local edits or accept remote?",
                actionLabel = "Keep Local",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                shellViewModel.resolveConflict(activeConflict.cartId, ConflictDecision.KEEP_LOCAL)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { BrandLogoBadge(logoSize = 34.dp) },
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("theme_settings_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (shellState.conflicts.isNotEmpty()) {
                                    Badge { Text("${shellState.conflicts.size}") }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ColorLens,
                                contentDescription = "Theme & Sync Settings",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute.startsWith("shade_cart"),
                    onClick = {
                        navController.navigate(NavRoutes.digitalShadeCard()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_shade_cart)) },
                    modifier = Modifier.testTag("nav_tab_shade_cart"),
                )
                NavigationBarItem(
                    selected = currentRoute.startsWith("capture"),
                    onClick = {
                        navController.navigate(NavRoutes.CART_CAPTURE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_capture)) },
                    modifier = Modifier.testTag("nav_tab_capture"),
                )
                NavigationBarItem(
                    selected = currentRoute.startsWith("palette_editor"),
                    onClick = {
                        navController.navigate(NavRoutes.paletteEditor()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_palette)) },
                    modifier = Modifier.testTag("nav_tab_palette"),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (activeConflict != null) {
                ConflictBanner(
                    conflict = activeConflict,
                    onResolve = shellViewModel::resolveConflict,
                )
            }

            NavHost(
                navController = navController,
                startDestination = initialRoute,
                modifier = Modifier.weight(1f),
            ) {
                composable(
                    route = NavRoutes.DIGITAL_SHADE_CARD,
                    arguments = listOf(
                        navArgument("cartId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    DigitalShadeCardRoute(
                        onOpenCapture = {
                            navController.navigate(NavRoutes.CART_CAPTURE) { launchSingleTop = true }
                        },
                        onEditCartImage = { cartId ->
                            navController.navigate(NavRoutes.cartEdit(cartId = cartId))
                        },
                        onOpenPaletteEditor = { cartId ->
                            navController.navigate(NavRoutes.paletteEditor(cartId = cartId))
                        },
                    )
                }

                composable(route = NavRoutes.CART_CAPTURE) {
                    CartCaptureRoute(
                        permissionStatusOverride = cameraPermissionStatusOverride,
                        onOpenSettingsOverride = onOpenSettingsOverride,
                        onImageReadyForEdit = { imagePath ->
                            navController.navigate(NavRoutes.cartEdit(imagePath = imagePath))
                        },
                    )
                }

                composable(
                    route = NavRoutes.CART_EDIT,
                    arguments = listOf(
                        navArgument("imagePath") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("cartId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    CartEditRoute(
                        onSavedNavigateToCart = { savedCartId ->
                            navController.navigate(NavRoutes.digitalShadeCard(savedCartId)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(
                    route = NavRoutes.PALETTE_EDITOR,
                    arguments = listOf(
                        navArgument("cartId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    PaletteEditorRoute(
                        onSavedNavigateToCart = { savedCartId ->
                            navController.navigate(NavRoutes.digitalShadeCard(savedCartId)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        ThemeAndSyncSettingsSheet(
            preferences = shellState.themePreferences,
            syncQueue = shellState.syncQueue,
            isConflictArmed = shellViewModel.isConflictArmed(),
            onDismiss = { showSettingsSheet = false },
            onSelectThemeMode = shellViewModel::setThemeMode,
            onToggleDynamicColor = shellViewModel::setDynamicColor,
            onSelectAccentPalette = shellViewModel::setAccentPalette,
            onSyncNow = {
                shellViewModel.syncNow()
                scope.launch {
                    snackbarHostState.showSnackbar("Sync triggered via WorkManager / Retrofit SyncAdapter.")
                }
            },
            onArmSimulatedConflict = {
                shellViewModel.armSimulatedConflict()
                scope.launch {
                    snackbarHostState.showSnackbar("Armed: next sync will simulate a remote version conflict.")
                }
            },
        )
    }
}
