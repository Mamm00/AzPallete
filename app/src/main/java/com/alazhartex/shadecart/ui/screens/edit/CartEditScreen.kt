package com.alazhartex.shadecart.ui.screens.edit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.data.model.CropAspectPreset
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.domain.vision.SegmentationModelState
import com.alazhartex.shadecart.ui.components.Rgb565PreviewImage
import com.canhub.cropper.CropImageView
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartEditScreen(
    onSavedNavigateToCart: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var cropViewRef by remember { mutableStateOf<CropImageView?>(null) }
    var lastLoadedSource by remember { mutableStateOf<String?>(null) }
    var cropEditorActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        uiState.nonBlockingMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_non_blocking_message"),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // ML Kit Subject Segmentation download prompt / crop-only fallback banner
        when (val segState = uiState.segmentationModelState) {
            is SegmentationModelState.NotDownloaded -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mlkit_download_prompt"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.mlkit_model_missing_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.mlkit_model_missing_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::downloadSegmentationModel,
                                modifier = Modifier.testTag("download_mlkit_model_button"),
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.download_model))
                            }
                            OutlinedButton(
                                onClick = { cropEditorActive = true },
                                modifier = Modifier.testTag("crop_only_fallback_button"),
                            ) {
                                Text(stringResource(R.string.continue_crop_only))
                            }
                        }
                    }
                }
            }

            is SegmentationModelState.Downloading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Downloading Subject Segmentation model (${segState.progressPercent ?: 0}%)...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (segState.progressPercent != null) {
                            LinearProgressIndicator(
                                progress = { segState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            is SegmentationModelState.UnavailableFallback -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crop_only_fallback_banner"),
                ) {
                    Text(
                        text = "${segState.reason} · Crop-only editing active.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            else -> Unit
        }

        // Visual non-destructive editor card (CanHub CropImageView + RGB_565 preview)
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Non-Destructive Card Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { cropEditorActive = !cropEditorActive },
                        modifier = Modifier.testTag("toggle_crop_mode_button"),
                    ) {
                        Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (cropEditorActive) "Done Cropping" else "Adjust Crop")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val activeImage = uiState.originalImagePath
                    if (cropEditorActive && !activeImage.isNullOrBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                CropImageView(ctx).apply {
                                    guidelines = CropImageView.Guidelines.ON
                                    setOnSetImageUriCompleteListener { view, _, error ->
                                        if (error == null) {
                                            val whole = view.wholeImageRect
                                            val norm = uiState.editStack.cropRect
                                            if (whole != null && norm != null) {
                                                view.cropRect = android.graphics.Rect(
                                                    (whole.left + norm.left * whole.width()).toInt(),
                                                    (whole.top + norm.top * whole.height()).toInt(),
                                                    (whole.left + norm.right * whole.width()).toInt(),
                                                    (whole.top + norm.bottom * whole.height()).toInt(),
                                                )
                                            }
                                        }
                                    }
                                    cropViewRef = this
                                }
                            },
                            update = { view ->
                                val preset = uiState.editStack.aspectPreset
                                if (preset.ratioX != null && preset.ratioY != null) {
                                    view.setFixedAspectRatio(true)
                                    view.setAspectRatio(preset.ratioX, preset.ratioY)
                                } else {
                                    view.setFixedAspectRatio(false)
                                }
                                if (lastLoadedSource != activeImage) {
                                    lastLoadedSource = activeImage
                                    view.setImageUriAsync(Uri.fromFile(File(activeImage)))
                                }
                                cropViewRef = view
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("canhub_crop_view"),
                        )
                    } else {
                        Rgb565PreviewImage(
                            imagePath = uiState.workingPreviewPath ?: uiState.originalImagePath,
                            bitmapProcessor = viewModel.bitmapProcessor,
                            contentDescription = "Shade card preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.medium),
                        )
                    }

                    if (uiState.isProcessingImage) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Free & fixed-aspect ratio preset chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CropAspectPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = uiState.editStack.aspectPreset == preset,
                            onClick = {
                                cropEditorActive = true
                                viewModel.setAspectPreset(preset)
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("aspect_preset_${preset.name}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cropEditorActive) {
                        Button(
                            onClick = {
                                val view = cropViewRef
                                val rect = view?.cropRect
                                val whole = view?.wholeImageRect
                                if (rect != null && whole != null && whole.width() > 0 && whole.height() > 0) {
                                    val norm = NormalizedRect(
                                        left = ((rect.left - whole.left).toFloat() / whole.width()).coerceIn(0f, 1f),
                                        top = ((rect.top - whole.top).toFloat() / whole.height()).coerceIn(0f, 1f),
                                        right = ((rect.right - whole.left).toFloat() / whole.width()).coerceIn(0f, 1f),
                                        bottom = ((rect.bottom - whole.top).toFloat() / whole.height()).coerceIn(0f, 1f),
                                    )
                                    viewModel.applyCrop(norm)
                                }
                                cropEditorActive = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply Crop")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = viewModel::toggleBackgroundRemoval,
                            enabled = !uiState.isProcessingImage,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remove_background_button"),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.editStack.backgroundRemoved) "Restore BG" else "Remove BG")
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::rotate90Degrees,
                        enabled = !uiState.isProcessingImage,
                    ) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate 90°", modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            cropEditorActive = false
                            viewModel.resetToOriginal()
                        },
                        enabled = !uiState.isProcessingImage,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Original", modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.editStack.history.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Edit stack (${uiState.editStack.history.size} steps · non-destructive): " +
                            uiState.editStack.history.takeLast(3).joinToString(" → ") { it.description },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Metadata OCR & Swatch Detection Form
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Header Metadata (OCR)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FilledTonalButton(
                        onClick = viewModel::analyzeCurrentImage,
                        enabled = !uiState.isProcessingImage,
                        modifier = Modifier.testTag("analyze_card_button"),
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detect OCR & Swatches")
                    }
                }

                OutlinedTextField(
                    value = uiState.fabricName,
                    onValueChange = viewModel::onFabricNameChanged,
                    label = { Text("Fabric Name") },
                    placeholder = { Text("e.g. Silk 180") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_fabric_name_input"),
                )

                OutlinedTextField(
                    value = uiState.fabricCode,
                    onValueChange = viewModel::onFabricCodeChanged,
                    label = { Text("Fabric Code") },
                    placeholder = { Text("e.g. SLK-180") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_fabric_code_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.weightGsmText,
                        onValueChange = viewModel::onWeightGsmChanged,
                        label = { Text("Weight (GSM / g/m²)") },
                        placeholder = { Text("180") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ocr_weight_input"),
                    )
                    OutlinedTextField(
                        value = uiState.widthCmText,
                        onValueChange = viewModel::onWidthCmChanged,
                        label = { Text("Width (cm)") },
                        placeholder = { Text("150") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ocr_width_input"),
                    )
                }

                Text(
                    text = "Detected Swatches (${uiState.detectedSwatches.size}) — pinked borders excluded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                if (uiState.detectedSwatches.isEmpty()) {
                    Text(
                        text = "No swatches detected yet. Tap 'Detect OCR & Swatches' above or save and add swatches manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.detectedSwatches.forEach { sw ->
                            val argb = HexColorValidator.parseArgbOrNull(sw.colorHex) ?: 0xFF334155.toInt()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                )
                                OutlinedTextField(
                                    value = sw.swatchNumber,
                                    onValueChange = { viewModel.onSwatchNumberEdited(sw.id, it) },
                                    label = { Text("#") },
                                    singleLine = true,
                                    modifier = Modifier.width(88.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sw.colorName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = sw.colorHex,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.saveShadeCart(onSavedNavigateToCart) },
            enabled = !uiState.isSaving && !uiState.isProcessingImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_shade_cart_button"),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Save Shade Cart")
            }
        }
    }
}
