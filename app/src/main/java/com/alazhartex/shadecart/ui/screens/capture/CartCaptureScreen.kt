package com.alazhartex.shadecart.ui.screens.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import kotlinx.coroutines.guava.await
import java.io.File
import java.util.concurrent.Executors

/**
 * Runtime camera permission states governing viewfinder vs rationale / settings deep-link UI.
 */
enum class CameraPermissionStatus {
    GRANTED,
    DENIED_SHOW_RATIONALE,
    PERMANENTLY_DENIED,
}

/**
 * Stateful route composable collecting [CartCaptureViewModel.uiState], binding Android runtime
 * permission + Photo Picker activity-result launchers, and delegating rendering to stateless
 * [CartCaptureScreen] (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 1.2 & 6.3).
 */
@Composable
fun CartCaptureRoute(
    onImageReadyForEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    permissionStatusOverride: CameraPermissionStatus? = null,
    onOpenSettingsOverride: (() -> Unit)? = null,
    viewModel: CartCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var systemGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        systemGranted = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.onGalleryPhotoPicked(uri, onImageReadyForEdit)
        }
    }

    val effectivePermissionStatus = permissionStatusOverride ?: when {
        systemGranted -> CameraPermissionStatus.GRANTED
        uiState.hasRequestedCameraPermission && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
            CameraPermissionStatus.PERMANENTLY_DENIED
        else -> CameraPermissionStatus.DENIED_SHOW_RATIONALE
    }

    val openAppSettings = onOpenSettingsOverride ?: {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    CartCaptureScreen(
        uiState = uiState,
        permissionStatus = effectivePermissionStatus,
        onRequestCameraPermission = {
            viewModel.markCameraPermissionRequested()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenAppSettings = openAppSettings,
        onPrepareTempCaptureFile = viewModel::prepareTempCaptureFile,
        onPhotoCaptured = { file -> viewModel.onPhotoCaptured(file, onImageReadyForEdit) },
        onCaptureError = viewModel::onCaptureError,
        onPickGalleryPhoto = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onLoadSampleCart = { viewModel.onLoadSampleCart(onImageReadyForEdit) },
        onResumePendingCapture = { viewModel.resumePendingCapture(onImageReadyForEdit) },
        onDiscardRestorableCapture = viewModel::discardRestorableCapture,
        modifier = modifier,
    )
}

/**
 * Pure stateless UI screen presenting process-death capture recovery, CameraX Compose viewfinder,
 * permission rationale/settings fallback cards, gallery Photo Picker trigger, and bundled sample A4
 * shade cart loader (Pyre, Chapter 1.2, 4.4, & 11.4).
 */
@Composable
fun CartCaptureScreen(
    uiState: CartCaptureUiState,
    permissionStatus: CameraPermissionStatus,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPrepareTempCaptureFile: () -> File,
    onPhotoCaptured: (File) -> Unit,
    onCaptureError: (String) -> Unit,
    onPickGalleryPhoto: () -> Unit,
    onLoadSampleCart: () -> Unit,
    onResumePendingCapture: () -> Unit,
    onDiscardRestorableCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.restorableCapturePath?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_capture_card"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "In-Progress Capture Restored",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Resume editing your saved A4 card capture.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = onDiscardRestorableCapture) {
                            Text("Discard")
                        }
                        Button(onClick = onResumePendingCapture) {
                            Text("Resume")
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (permissionStatus) {
                CameraPermissionStatus.GRANTED -> {
                    CameraCapturePreviewBox(
                        isBusy = uiState.isBusy,
                        onCaptureClick = { imageCapture, executor ->
                            val tempFile = onPrepareTempCaptureFile()
                            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                            imageCapture.takePicture(
                                options,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onPhotoCaptured(tempFile)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        onCaptureError(
                                            exception.message ?: "Camera capture error",
                                        )
                                    }
                                },
                            )
                        },
                        onCameraError = onCaptureError,
                    )
                }

                CameraPermissionStatus.DENIED_SHOW_RATIONALE -> {
                    PermissionDeniedCard(
                        title = stringResource(R.string.camera_rationale_title),
                        body = stringResource(R.string.camera_rationale_body),
                        isPermanent = false,
                        onRequestPermission = onRequestCameraPermission,
                        onOpenSettings = onOpenAppSettings,
                    )
                }

                CameraPermissionStatus.PERMANENTLY_DENIED -> {
                    PermissionDeniedCard(
                        title = stringResource(R.string.camera_rationale_title),
                        body = stringResource(R.string.camera_permanently_denied_body),
                        isPermanent = true,
                        onRequestPermission = onRequestCameraPermission,
                        onOpenSettings = onOpenAppSettings,
                    )
                }
            }

            if (uiState.isBusy) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Downsampling to <=2048px...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = onPickGalleryPhoto,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("upload_photo_button"),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.upload_from_gallery))
            }

            OutlinedButton(
                onClick = onLoadSampleCart,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("load_sample_cart_button"),
            ) {
                Icon(Icons.Rounded.AutoAwesomeMotion, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.load_sample_cart))
            }
        }
    }
}

@Composable
private fun CameraCapturePreviewBox(
    isBusy: Boolean,
    onCaptureClick: (ImageCapture, java.util.concurrent.ExecutorService) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(lifecycleOwner) {
        runCatching {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { req -> surfaceRequest = req }
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure { err ->
            onCameraError("Camera preview unavailable (${err.message ?: "emulator/no hardware"}). Use Upload Photo or Load Sample A4 Cart.")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxSize()
            .testTag("camera_preview_box"),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val activeSurface = surfaceRequest
            if (activeSurface != null) {
                val coordinateTransformer = remember { MutableCoordinateTransformer() }
                CameraXViewfinder(
                    surfaceRequest = activeSurface,
                    coordinateTransformer = coordinateTransformer,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Initializing CameraX viewfinder...\nAlign A4 physical shade cart inside frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                color = Color.Transparent,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp),
            ) {}

            FloatingActionButton(
                onClick = {
                    if (!isBusy) {
                        onCaptureClick(imageCapture, cameraExecutor)
                    }
                },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .size(68.dp)
                    .testTag("capture_shutter_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = "Capture A4 Shade Cart",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedCard(
    title: String,
    body: String,
    isPermanent: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("camera_permission_rationale_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isPermanent) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_settings_button"),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.open_settings))
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("allow_camera_button"),
                ) {
                    Text(stringResource(R.string.grant_camera_permission))
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@ComposePreview(showBackground = true)
@ComposePreview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CartCaptureScreenRationalePreview() {
    AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
        CartCaptureScreen(
            uiState = CartCaptureUiState(),
            permissionStatus = CameraPermissionStatus.DENIED_SHOW_RATIONALE,
            onRequestCameraPermission = {},
            onOpenAppSettings = {},
            onPrepareTempCaptureFile = { File("/tmp/preview.jpg") },
            onPhotoCaptured = {},
            onCaptureError = {},
            onPickGalleryPhoto = {},
            onLoadSampleCart = {},
            onResumePendingCapture = {},
            onDiscardRestorableCapture = {},
        )
    }
}
