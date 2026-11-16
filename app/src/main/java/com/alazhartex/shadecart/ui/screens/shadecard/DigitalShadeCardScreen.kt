package com.alazhartex.shadecart.ui.screens.shadecard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchWithRoll
import com.alazhartex.shadecart.data.local.SyncStatus
import com.alazhartex.shadecart.ui.components.BrandLogoBadge
import com.alazhartex.shadecart.ui.components.PinkedSwatchCard
import com.alazhartex.shadecart.ui.components.Rgb565PreviewImage

@Composable
fun DigitalShadeCardScreen(
    onOpenCapture: () -> Unit,
    onEditCartImage: (cartId: String) -> Unit,
    onOpenPaletteEditor: (cartId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DigitalShadeCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var rollDialogSwatch by remember { mutableStateOf<SwatchWithRoll?>(null) }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val activeCart = uiState.activeCart

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("digital_shade_card_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.carts.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.carts, key = { it.cart.id }) { item ->
                        FilterChip(
                            selected = item.cart.id == activeCart?.cart?.id,
                            onClick = { viewModel.selectCart(item.cart.id) },
                            label = { Text("${item.cart.fabricName} (${item.cart.fabricCode})") },
                        )
                    }
                }
            }
        }

        if (activeCart != null) {
            item {
                ActiveCartHeaderCard(
                    details = activeCart,
                    bitmapProcessor = viewModel.bitmapProcessor,
                    onEditImage = { onEditCartImage(activeCart.cart.id) },
                    onEditPalette = { onOpenPaletteEditor(activeCart.cart.id) },
                )
            }
        }

        if (activeCart == null || activeCart.swatches.isEmpty()) {
            item {
                NoSwatchesEmptyState(
                    hasCart = activeCart != null,
                    onAddSwatchManually = { viewModel.openManualPaletteEditor(onOpenPaletteEditor) },
                    onCaptureNewCart = onOpenCapture,
                )
            }
        } else {
            val rows = activeCart.swatches.chunked(2)
            items(rows, key = { pair -> pair.joinToString("-") { it.swatch.id } }) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { swatchItem ->
                        PinkedSwatchCard(
                            item = swatchItem,
                            onRollClick = { rollDialogSwatch = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    rollDialogSwatch?.let { target ->
        RollInventoryLinkDialog(
            swatchWithRoll = target,
            rolls = uiState.availableRolls,
            onDismiss = { rollDialogSwatch = null },
            onSelectRoll = { rollId ->
                viewModel.linkSwatchToRoll(target, rollId)
                rollDialogSwatch = null
            },
        )
    }
}

@Composable
private fun ActiveCartHeaderCard(
    details: ShadeCartWithDetails,
    bitmapProcessor: com.alazhartex.shadecart.domain.imaging.BitmapProcessor,
    onEditImage: () -> Unit,
    onEditPalette: () -> Unit,
) {
    val cart = details.cart
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cart.fabricName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Code: ${cart.fabricCode} · v${cart.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val syncLabel = when (cart.syncStatus) {
                    SyncStatus.SYNCED -> "Synced"
                    SyncStatus.CONFLICT -> "Conflict"
                    else -> "Pending Sync"
                }
                AssistChip(
                    onClick = {},
                    label = { Text(syncLabel) },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "Weight: ${cart.weightGsm} GSM (g/m²)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "Width: ${cart.widthCm} cm",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            val previewPath = cart.editedImagePath ?: cart.originalImagePath
            if (!previewPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Rgb565PreviewImage(
                    imagePath = previewPath,
                    bitmapProcessor = bitmapProcessor,
                    contentDescription = "Captured shade cart thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!previewPath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onEditImage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Crop / OCR")
                    }
                }
                Button(
                    onClick = onEditPalette,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_palette_editor_button"),
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Palette Editor")
                }
            }
        }
    }
}

@Composable
private fun NoSwatchesEmptyState(
    hasCart: Boolean,
    onAddSwatchManually: () -> Unit,
    onCaptureNewCart: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_swatches_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandLogoBadge(logoSize = 48.dp, showSubtitle = false)
            Text(
                text = stringResource(R.string.no_swatches_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.no_swatches_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddSwatchManually,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_swatch_manually_cta"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_swatch_manually))
            }
            if (!hasCart) {
                OutlinedButton(
                    onClick = onCaptureNewCart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Physical Shade Cart")
                }
            }
        }
    }
}

@Composable
private fun RollInventoryLinkDialog(
    swatchWithRoll: SwatchWithRoll,
    rolls: List<Roll>,
    onDismiss: () -> Unit,
    onSelectRoll: (String?) -> Unit,
) {
    val currentRoll = swatchWithRoll.roll
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
        title = { Text("Swatch #${swatchWithRoll.swatch.swatchNumber} · Roll Inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentRoll != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Linked: ${currentRoll.rollCode} (${currentRoll.status})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Shed: ${currentRoll.warehouseShed} · ${currentRoll.lengthMeters} m · ${currentRoll.colorName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }

                Text(
                    text = "Select Roll Record",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRoll(null) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentRoll == null,
                        onClick = { onSelectRoll(null) },
                    )
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unlinked (no roll record)", style = MaterialTheme.typography.bodyMedium)
                }

                rolls.forEach { roll ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRoll(roll.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentRoll?.id == roll.id,
                            onClick = { onSelectRoll(roll.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${roll.rollCode} · Shade #${roll.shadeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${roll.warehouseShed} · ${roll.lengthMeters} m · ${roll.status}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
