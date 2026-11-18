package com.alazhartex.shadecart.ui.screens.palette

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.ui.components.ColorBlindPatternDot
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import java.util.Locale

private val PresetTextileColors = listOf(
    "Navy" to "#102A71",
    "Crimson" to "#CD1820",
    "Emerald" to "#0F5132",
    "Sand" to "#D4A017",
    "Burgundy" to "#761D3A",
    "Teal" to "#187480",
    "Charcoal" to "#383E4A",
)

@Composable
fun PaletteEditorRoute(
    onSavedNavigateToCart: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaletteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PaletteEditorScreen(
        uiState = uiState,
        onStartNewSwatch = viewModel::startNewSwatch,
        onStartEditingSwatch = viewModel::startEditingSwatch,
        onSwatchNumberChanged = viewModel::onSwatchNumberChanged,
        onColorNameChanged = viewModel::onColorNameChanged,
        onPriceChanged = viewModel::onPriceChanged,
        onHexInputChanged = viewModel::onHexInputChanged,
        onRollSelected = viewModel::onRollSelected,
        onApplyColorEditor = { viewModel.applyColorEditor() },
        onMoveSwatch = viewModel::moveSwatch,
        onDeleteSwatch = viewModel::deleteSwatch,
        onSavePalette = { viewModel.savePalette(onSavedNavigateToCart) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteEditorScreen(
    uiState: PaletteEditorUiState,
    onStartNewSwatch: () -> Unit,
    onStartEditingSwatch: (SwatchInput) -> Unit,
    onSwatchNumberChanged: (String) -> Unit,
    onColorNameChanged: (String) -> Unit,
    onHexInputChanged: (String) -> Unit,
    onRollSelected: (String?) -> Unit,
    onApplyColorEditor: () -> Unit,
    onMoveSwatch: (Int, Int) -> Unit,
    onDeleteSwatch: (String) -> Unit,
    onSavePalette: () -> Unit,
    modifier: Modifier = Modifier,
    onPriceChanged: (String) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    val rowSwapThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var rollMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("palette_editor_screen"),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "palette_editor_form") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
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
                                text = if (uiState.editingSwatchId != null) {
                                    "Override Swatch Color"
                                } else {
                                    "Add / Edit Swatch Color"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (uiState.editingSwatchId != null) {
                                OutlinedButton(onClick = onStartNewSwatch) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.swatchNumberInput,
                                onValueChange = onSwatchNumberChanged,
                                label = { Text("Swatch #") },
                                singleLine = true,
                                modifier = Modifier
                                    .width(96.dp)
                                    .testTag("swatch_number_input"),
                            )
                            OutlinedTextField(
                                value = uiState.colorNameInput,
                                onValueChange = onColorNameChanged,
                                label = { Text("Color Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = uiState.priceInput,
                                onValueChange = onPriceChanged,
                                label = { Text("EGP/m") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(92.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.hexInput,
                                onValueChange = onHexInputChanged,
                                label = { Text("HEX Color (#RGB or #RRGGBB)") },
                                singleLine = true,
                                isError = !uiState.isHexValid,
                                supportingText = {
                                    if (!uiState.isHexValid && uiState.hexValidationError != null) {
                                        Text(
                                            text = uiState.hexValidationError,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.testTag("hex_error_text"),
                                        )
                                    } else {
                                        val labStr = uiState.liveLab?.let { (l, a, b) ->
                                            " · L*${String.format(Locale.US, "%.0f", l)} a*${String.format(Locale.US, "%.0f", a)} b*${String.format(Locale.US, "%.0f", b)}"
                                        }.orEmpty()
                                        Text(
                                            text = "Valid · ${HexColorValidator.normalizeToSixDigitHex(uiState.hexInput)}$labStr",
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hex_input"),
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                val previewArgb = uiState.livePreviewArgb
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(
                                            previewArgb?.let { Color(it) }
                                                ?: MaterialTheme.colorScheme.errorContainer,
                                        )
                                        .border(
                                            2.dp,
                                            if (uiState.isHexValid) {
                                                MaterialTheme.colorScheme.outline
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                            CircleShape,
                                        )
                                        .testTag("hex_live_preview"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (previewArgb == null) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Invalid HEX preview",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Preview",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }

                        uiState.duplicateWarning?.let { warn ->
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Similar shade (ΔE ${String.format(Locale.US, "%.2f", warn.deltaE)}) in ${warn.existingCartName} #${warn.existingSwatchNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PresetTextileColors.forEach { (label, hex) ->
                                val presetColor = Color(HexColorValidator.parseArgbOrNull(hex) ?: 0xFF102A71.toInt())
                                AssistChip(
                                    onClick = {
                                        onHexInputChanged(hex)
                                        if (uiState.colorNameInput.isBlank()) {
                                            onColorNameChanged(label)
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(presetColor),
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Box {
                            val linkedRoll = uiState.availableRolls.firstOrNull { it.id == uiState.selectedRollId }
                            OutlinedButton(
                                onClick = { rollMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = linkedRoll?.let {
                                        "Linked Roll: ${it.rollCode} (${it.warehouseShed} · ${it.availableMeters.toInt()}m)"
                                    } ?: "Optional Roll Link: None",
                                )
                            }
                            DropdownMenu(
                                expanded = rollMenuExpanded,
                                onDismissRequest = { rollMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (no roll linked)") },
                                    onClick = {
                                        onRollSelected(null)
                                        rollMenuExpanded = false
                                    },
                                )
                                uiState.availableRolls.forEach { roll ->
                                    DropdownMenuItem(
                                        text = { Text("${roll.rollCode} · #${roll.shadeLabel} · ${roll.colorName} (${roll.warehouseShed})") },
                                        onClick = {
                                            onRollSelected(roll.id)
                                            rollMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApplyColorEditor()
                            },
                            enabled = uiState.isHexValid && uiState.swatchNumberInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apply_hex_button"),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.editingSwatchId != null) "Apply Color Override" else "Apply & Add Swatch")
                        }
                    }
                }
            }

            uiState.statusMessage?.let { msg ->
                item(key = "palette_status_banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            item(key = "palette_swatch_section_header") {
                Text(
                    text = "Swatches (${uiState.swatches.size}) — drag handle to reorder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            itemsIndexed(uiState.swatches, key = { _, sw -> sw.id }) { index, sw ->
                val isDragging = draggedIndex == index
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragAccumulatedY * 0.25f
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .testTag("palette_row_${sw.swatchNumber}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.editingSwatchId == sw.id) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartEditingSwatch(sw) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = "Drag handle to reorder swatch #${sw.swatchNumber}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(6.dp)
                                .pointerInput(uiState.swatches.size) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragAccumulatedY = 0f
                                        },
                                        onDragEnd = {
                                            draggedIndex = -1
                                            dragAccumulatedY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            dragAccumulatedY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAccumulatedY += dragAmount.y
                                            if (draggedIndex in uiState.swatches.indices) {
                                                if (dragAccumulatedY > rowSwapThresholdPx &&
                                                    draggedIndex < uiState.swatches.lastIndex
                                                ) {
                                                    val next = draggedIndex + 1
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onMoveSwatch(draggedIndex, next)
                                                    draggedIndex = next
                                                    dragAccumulatedY = 0f
                                                } else if (dragAccumulatedY < -rowSwapThresholdPx &&
                                                    draggedIndex > 0
                                                ) {
                                                    val prev = draggedIndex - 1
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onMoveSwatch(draggedIndex, prev)
                                                    draggedIndex = prev
                                                    dragAccumulatedY = 0f
                                                }
                                            }
                                        },
                                    )
                                }
                                .testTag("drag_handle_${sw.swatchNumber}"),
                        )

                        ColorBlindPatternDot(
                            colorHex = sw.colorHex,
                            patternSeed = index,
                            modifier = Modifier.size(34.dp),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${sw.swatchNumber} · ${sw.colorName.ifBlank { "Shade" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sw.colorHex + (sw.pricePerMeter?.let { " · ${String.format(Locale.US, "%.1f", it)} ${sw.currencyCode}/m" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (index > 0) {
                            IconButton(onClick = { onMoveSwatch(index, index - 1) }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                            }
                        }
                        if (index < uiState.swatches.lastIndex) {
                            IconButton(onClick = { onMoveSwatch(index, index + 1) }) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                            }
                        }
                        IconButton(onClick = { onStartEditingSwatch(sw) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit swatch")
                        }
                        IconButton(onClick = { onDeleteSwatch(sw.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete swatch",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSavePalette,
                enabled = !uiState.isSaving && uiState.cartId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
                    .testTag("save_palette_button"),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Palette Changes")
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaletteEditorScreenPreview() {
    AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
        PaletteEditorScreen(
            uiState = PaletteEditorUiState(
                cartId = "cart-preview",
                swatchNumberInput = "6",
                hexInput = "#102A71",
                swatches = listOf(
                    SwatchInput(
                        id = "sw-5",
                        swatchNumber = "5",
                        colorHex = "#102A71",
                        colorName = "Azhar Navy",
                        positionOrder = 0,
                    ),
                ),
            ),
            onStartNewSwatch = {},
            onStartEditingSwatch = {},
            onSwatchNumberChanged = {},
            onColorNameChanged = {},
            onHexInputChanged = {},
            onRollSelected = {},
            onApplyColorEditor = {},
            onMoveSwatch = { _, _ -> },
            onDeleteSwatch = {},
            onSavePalette = {},
        )
    }
}
