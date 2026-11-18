package com.alazhartex.shadecart.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
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
import com.alazhartex.shadecart.data.backup.EncryptedBackupManager
import com.alazhartex.shadecart.data.nearby.NearbyShareCoordinator
import com.alazhartex.shadecart.data.nearby.NearbyShareUiState
import com.alazhartex.shadecart.data.nearby.NearbySyncStatus
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.AppLanguageSetting
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AppSessionPreferences
import com.alazhartex.shadecart.domain.model.BuyerSessionState
import com.alazhartex.shadecart.domain.model.CalibrationPreferencesState
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.RoleGate
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.repository.SyncDebugController
import com.alazhartex.shadecart.domain.usecase.ActivateBuyerModeUseCase
import com.alazhartex.shadecart.domain.usecase.DeleteCalibrationProfileUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveAppSessionPreferencesUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveBuyerSessionUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveCalibrationStateUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveSyncConflictsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveSyncQueueUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveThemePreferencesUseCase
import com.alazhartex.shadecart.domain.usecase.RecordBackupMetadataUseCase
import com.alazhartex.shadecart.domain.usecase.ResolveSyncConflictUseCase
import com.alazhartex.shadecart.domain.usecase.SaveCalibrationProfileUseCase
import com.alazhartex.shadecart.domain.usecase.SelectCalibrationProfileUseCase
import com.alazhartex.shadecart.domain.usecase.TriggerSyncUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateAccentPaletteUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateAppLanguageUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateAppRoleUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateDynamicColorUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateGloveModeUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateThemeModeUseCase
import com.alazhartex.shadecart.ui.components.BrandLogoBadge
import com.alazhartex.shadecart.ui.components.ConflictBanner
import com.alazhartex.shadecart.ui.components.ShadeLockCalibrationDialog
import com.alazhartex.shadecart.ui.components.ThemeAndSyncSettingsSheet
import com.alazhartex.shadecart.ui.components.rememberAmbientLux
import com.alazhartex.shadecart.ui.navigation.NavRoutes
import com.alazhartex.shadecart.ui.screens.buyer.BuyerShowcaseRoute
import com.alazhartex.shadecart.ui.screens.capture.CameraPermissionStatus
import com.alazhartex.shadecart.ui.screens.capture.CartCaptureRoute
import com.alazhartex.shadecart.ui.screens.edit.CartEditRoute
import com.alazhartex.shadecart.ui.screens.inventory.InventoryAndMillRoute
import com.alazhartex.shadecart.ui.screens.palette.PaletteEditorRoute
import com.alazhartex.shadecart.ui.screens.shadecard.DigitalShadeCardRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppShellState(
    val themePreferences: ThemeSettings = ThemeSettings(),
    val conflicts: List<SyncConflictModel> = emptyList(),
    val syncQueue: List<SyncQueueEntryModel> = emptyList(),
    val calibrationState: CalibrationPreferencesState = CalibrationPreferencesState(),
    val buyerSession: BuyerSessionState = BuyerSessionState(),
    val sessionPreferences: AppSessionPreferences = AppSessionPreferences(),
    val carts: List<ShadeCartModel> = emptyList(),
    val nearbyState: NearbyShareUiState = NearbyShareUiState(),
)

private data class ShellCoreBundle(
    val theme: ThemeSettings,
    val conflicts: List<SyncConflictModel>,
    val queue: List<SyncQueueEntryModel>,
    val calibration: CalibrationPreferencesState,
)

private data class ShellSessionBundle(
    val buyer: BuyerSessionState,
    val session: AppSessionPreferences,
    val carts: List<ShadeCartModel>,
    val nearby: NearbyShareUiState,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    observeThemePreferences: ObserveThemePreferencesUseCase,
    observeSyncConflicts: ObserveSyncConflictsUseCase,
    observeSyncQueue: ObserveSyncQueueUseCase,
    observeCalibrationState: ObserveCalibrationStateUseCase,
    observeBuyerSession: ObserveBuyerSessionUseCase,
    observeAppSessionPreferences: ObserveAppSessionPreferencesUseCase,
    observeShadeCarts: ObserveShadeCartsUseCase,
    private val updateThemeMode: UpdateThemeModeUseCase,
    private val updateDynamicColor: UpdateDynamicColorUseCase,
    private val updateAccentPalette: UpdateAccentPaletteUseCase,
    private val resolveSyncConflict: ResolveSyncConflictUseCase,
    private val triggerSync: TriggerSyncUseCase,
    private val syncDebugController: SyncDebugController,
    private val saveCalibrationProfile: SaveCalibrationProfileUseCase,
    private val selectCalibrationProfile: SelectCalibrationProfileUseCase,
    private val deleteCalibrationProfile: DeleteCalibrationProfileUseCase,
    private val activateBuyerModeUseCase: ActivateBuyerModeUseCase,
    private val updateAppRole: UpdateAppRoleUseCase,
    private val updateAppLanguage: UpdateAppLanguageUseCase,
    private val updateGloveMode: UpdateGloveModeUseCase,
    private val recordBackupMetadata: RecordBackupMetadataUseCase,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val nearbyShareCoordinator: NearbyShareCoordinator,
) : ViewModel() {

    private val coreFlow = combine(
        observeThemePreferences(),
        observeSyncConflicts(),
        observeSyncQueue(),
        observeCalibrationState(),
    ) { theme, conflicts, queue, cal ->
        ShellCoreBundle(theme, conflicts, queue, cal)
    }

    private val sessionFlow = combine(
        observeBuyerSession(),
        observeAppSessionPreferences(),
        observeShadeCarts(),
        nearbyShareCoordinator.state,
    ) { buyer, session, carts, nearby ->
        ShellSessionBundle(buyer, session, carts, nearby)
    }

    val shellState: StateFlow<AppShellState> = combine(coreFlow, sessionFlow) { core, sess ->
        AppShellState(
            themePreferences = core.theme,
            conflicts = core.conflicts,
            syncQueue = core.queue,
            calibrationState = core.calibration,
            buyerSession = sess.buyer,
            sessionPreferences = sess.session,
            carts = sess.carts,
            nearbyState = sess.nearby,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppShellState(),
    )

    val backupBannerMessage = MutableStateFlow<String?>(null)

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
        viewModelScope.launch { resolveSyncConflict(cartId, decision) }
    }

    fun syncNow() {
        viewModelScope.launch { triggerSync() }
    }

    fun armSimulatedConflict() {
        syncDebugController.armSimulatedConflict()
    }

    fun isConflictArmed(): Boolean = syncDebugController.isConflictArmed()

    fun saveCalibration(profile: CalibrationProfile) {
        viewModelScope.launch { saveCalibrationProfile(profile, setAsActive = true) }
    }

    fun selectCalibration(profileId: String) {
        viewModelScope.launch { selectCalibrationProfile(profileId) }
    }

    fun deleteCalibration(profileId: String) {
        viewModelScope.launch { deleteCalibrationProfile(profileId) }
    }

    fun activateBuyerMode(pin: String, company: String) {
        viewModelScope.launch { activateBuyerModeUseCase(pin, company) }
    }

    fun setRole(role: AppRole) {
        viewModelScope.launch { updateAppRole(role) }
    }

    fun setLanguage(lang: AppLanguageSetting) {
        viewModelScope.launch {
            updateAppLanguage(lang)
            val tags = if (lang.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(lang.tag)
            AppCompatDelegate.setApplicationLocales(tags)
        }
    }

    fun setGloveMode(enabled: Boolean) {
        viewModelScope.launch { updateGloveMode(enabled) }
    }

    fun exportEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            encryptedBackupManager.exportEncryptedZipToUri(uri, passphrase)
                .onSuccess { summary ->
                    recordBackupMetadata(summary.timestampMillis, summary.bytesWritten)
                    backupBannerMessage.value =
                        "Encrypted backup saved (${summary.cartCount} carts, ${summary.bytesWritten / 1024} KB)."
                }
                .onFailure { err ->
                    backupBannerMessage.value = err.message ?: "Backup failed."
                }
        }
    }

    fun restoreEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            encryptedBackupManager.restoreFromEncryptedZipUri(uri, passphrase)
                .onSuccess { summary ->
                    backupBannerMessage.value =
                        "Restored ${summary.cartCount} carts and ${summary.rollCount} warehouse rolls."
                }
                .onFailure { err ->
                    backupBannerMessage.value = err.message ?: "Restore failed — check passphrase."
                }
        }
    }

    fun startNearbyReceive() {
        nearbyShareCoordinator.startAdvertisingAsReceiver(
            "AzPallete (${shellState.value.sessionPreferences.lastOperatorName})",
        )
    }

    fun startNearbySend() {
        nearbyShareCoordinator.startDiscoveryToSend(
            deviceDisplayName = "AzPallete (${shellState.value.sessionPreferences.lastOperatorName})",
            cartsToShare = shellState.value.carts,
        )
    }

    fun connectNearbyPeer(endpointId: String) {
        nearbyShareCoordinator.connectAndSend(
            endpointId = endpointId,
            senderName = "AzPallete (${shellState.value.sessionPreferences.lastOperatorName})",
        )
    }

    fun stopNearby() {
        nearbyShareCoordinator.stopAll()
    }

    fun clearBackupMessage() {
        backupBannerMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlAzharTexShadeCartApp(
    shellViewModel: AppShellViewModel,
    initialRoute: String = NavRoutes.DIGITAL_SHADE_CARD,
    cameraPermissionStatusOverride: CameraPermissionStatus? = null,
    onOpenSettingsOverride: (() -> Unit)? = null,
    externalDeepLinkCartId: String? = null,
    onConsumedDeepLink: () -> Unit = {},
) {
    val shellState by shellViewModel.shellState.collectAsStateWithLifecycle()
    val backupMessage by shellViewModel.backupBannerMessage.collectAsStateWithLifecycle()

    // B2: Buyer Showcase Mode completely isolates buyer UI from internal mill tools
    if (shellState.buyerSession.isBuyerMode) {
        BuyerShowcaseRoute()
        return
    }

    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val useNavigationRail = configuration.screenWidthDp >= 600
    val ambientLux = rememberAmbientLux()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showNearbyDialog by remember { mutableStateOf(false) }
    var pendingBackupPassphrase by remember { mutableStateOf<String?>(null) }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val pass = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri != null && !pass.isNullOrBlank()) {
            shellViewModel.exportEncryptedBackup(uri, pass)
        }
    }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val pass = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri != null && !pass.isNullOrBlank()) {
            shellViewModel.restoreEncryptedBackup(uri, pass)
        }
    }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        showNearbyDialog = true
    }

    LaunchedEffect(externalDeepLinkCartId) {
        if (!externalDeepLinkCartId.isNullOrBlank()) {
            navController.navigate(NavRoutes.digitalShadeCard(externalDeepLinkCartId)) {
                launchSingleTop = true
            }
            onConsumedDeepLink()
        }
    }

    LaunchedEffect(backupMessage) {
        val msg = backupMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            shellViewModel.clearBackupMessage()
        }
    }

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

    val canEdit = RoleGate.canEditCarts(shellState.sessionPreferences.role)
    val itemHeightModifier = if (shellState.sessionPreferences.gloveMode) Modifier.height(60.dp) else Modifier

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
            if (!useNavigationRail) {
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
                        modifier = Modifier.then(itemHeightModifier).testTag("nav_tab_shade_cart"),
                    )
                    if (canEdit) {
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
                            modifier = Modifier.then(itemHeightModifier).testTag("nav_tab_capture"),
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
                            modifier = Modifier.then(itemHeightModifier).testTag("nav_tab_palette"),
                        )
                    }
                    NavigationBarItem(
                        selected = currentRoute.startsWith("mill_hub"),
                        onClick = {
                            navController.navigate(NavRoutes.MILL_HUB) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Inventory, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_mill_hub)) },
                        modifier = Modifier.then(itemHeightModifier).testTag("nav_tab_mill"),
                    )
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (useNavigationRail) {
                NavigationRail {
                    NavigationRailItem(
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
                    if (canEdit) {
                        NavigationRailItem(
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
                        NavigationRailItem(
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
                    NavigationRailItem(
                        selected = currentRoute.startsWith("mill_hub"),
                        onClick = {
                            navController.navigate(NavRoutes.MILL_HUB) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.Inventory, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_mill_hub)) },
                        modifier = Modifier.testTag("nav_tab_mill"),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
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

                    composable(route = NavRoutes.MILL_HUB) {
                        InventoryAndMillRoute(
                            onOpenCart = { cartId ->
                                navController.navigate(NavRoutes.digitalShadeCard(cartId)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
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
            sessionPreferences = shellState.sessionPreferences,
            activeCalibrationName = shellState.calibrationState.activeProfile?.name,
            onSelectLanguage = shellViewModel::setLanguage,
            onSelectRole = shellViewModel::setRole,
            onToggleGloveMode = shellViewModel::setGloveMode,
            onOpenCalibration = {
                showSettingsSheet = false
                showCalibrationDialog = true
            },
            onActivateBuyerMode = { pin, company ->
                shellViewModel.activateBuyerMode(pin, company)
            },
            onExportBackup = { pass ->
                pendingBackupPassphrase = pass
                showSettingsSheet = false
                safExportLauncher.launch("AzPallete_Backup.azpallete.zip")
            },
            onRestoreBackup = { pass ->
                pendingBackupPassphrase = pass
                showSettingsSheet = false
                safImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            onOpenNearbyShare = {
                showSettingsSheet = false
                val needed = buildNearbyPermissions().filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (needed.isEmpty()) {
                    showNearbyDialog = true
                } else {
                    nearbyPermissionLauncher.launch(needed.toTypedArray())
                }
            },
        )
    }

    if (showCalibrationDialog) {
        ShadeLockCalibrationDialog(
            profiles = shellState.calibrationState.profiles,
            activeProfileId = shellState.calibrationState.activeProfileId,
            currentAmbientLux = ambientLux,
            onSelectProfile = shellViewModel::selectCalibration,
            onDeleteProfile = shellViewModel::deleteCalibration,
            onSaveNewProfile = shellViewModel::saveCalibration,
            onDismiss = { showCalibrationDialog = false },
        )
    }

    if (showNearbyDialog) {
        NearbySyncDialog(
            state = shellState.nearbyState,
            cartCount = shellState.carts.size,
            onStartReceive = shellViewModel::startNearbyReceive,
            onStartSend = shellViewModel::startNearbySend,
            onConnectPeer = shellViewModel::connectNearbyPeer,
            onDismiss = {
                shellViewModel.stopNearby()
                showNearbyDialog = false
            },
        )
    }
}

@Composable
private fun NearbySyncDialog(
    state: NearbyShareUiState,
    cartCount: Int,
    onStartReceive: () -> Unit,
    onStartSend: () -> Unit,
    onConnectPeer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nearby Mill Sync (P2P)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = state.statusMessage ?: "Share $cartCount local shade cart(s) peer-to-peer without internet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartSend,
                        enabled = state.status == NearbySyncStatus.IDLE || state.status == NearbySyncStatus.ERROR || state.status == NearbySyncStatus.COMPLETED,
                        modifier = Modifier.weight(1f),
                    ) { Text("Send") }
                    OutlinedButton(
                        onClick = onStartReceive,
                        enabled = state.status == NearbySyncStatus.IDLE || state.status == NearbySyncStatus.ERROR || state.status == NearbySyncStatus.COMPLETED,
                        modifier = Modifier.weight(1f),
                    ) { Text("Receive") }
                }
                state.discoveredPeers.forEach { peer ->
                    OutlinedButton(
                        onClick = { onConnectPeer(peer.endpointId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Send to ${peer.name}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun buildNearbyPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
