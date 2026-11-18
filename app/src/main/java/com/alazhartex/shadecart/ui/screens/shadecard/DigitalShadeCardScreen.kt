package com.alazhartex.shadecart.ui.screens.shadecard

import android.content.res.Configuration
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.export.ShadeCartFormatters
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.model.ltrIsolate
import com.alazhartex.shadecart.ui.components.BrandLogoBadge
import com.alazhartex.shadecart.ui.components.DrapePreviewDialog
import com.alazhartex.shadecart.ui.components.PinkedSwatchCard
import com.alazhartex.shadecart.ui.components.Rgb565PreviewImage
import com.alazhartex.shadecart.ui.export.ShadeCartAndroidExporters
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import java.util.Locale

@Composable
fun DigitalShadeCardRoute(
    onOpenCapture: () -> Unit,
    onEditCartImage: (cartId: String) -> Unit,
    onOpenPaletteEditor: (cartId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DigitalShadeCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DigitalShadeCardScreen(
        uiState = uiState,
        onSelectCart = viewModel::selectCart,
        onOpenCapture = onOpenCapture,
        onEditCartImage = onEditCartImage,
        onOpenPaletteEditor = onOpenPaletteEditor,
        onAddSwatchManually = { viewModel.openManualPaletteEditor(onOpenPaletteEditor) },
        onLinkSwatchToRoll = viewModel::linkSwatchToRoll,
        loadPreviewBitmap = viewModel::loadPreviewBitmap,
        modifier = modifier,
    )
}

@Composable
fun DigitalShadeCardScreen(
    uiState: DigitalShadeCardUiState,
    onSelectCart: (String) -> Unit,
    onOpenCapture: () -> Unit,
    onEditCartImage: (cartId: String) -> Unit,
    onOpenPaletteEditor: (cartId: String) -> Unit,
    onAddSwatchManually: () -> Unit,
    onLinkSwatchToRoll: (SwatchModel, String?) -> Unit,
    loadPreviewBitmap: suspend (String) -> Bitmap? = { null },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var rollDialogSwatch by remember { mutableStateOf<SwatchModel?>(null) }
    var drapeSwatch by remember { mutableStateOf<SwatchModel?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

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
            item(key = "cart_selector_row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.carts, key = { it.id }) { item ->
                        FilterChip(
                            selected = item.id == activeCart?.id,
                            onClick = { onSelectCart(item.id) },
                            label = { Text("${item.fabricName} (${item.fabricCode})") },
                        )
                    }
                }
            }
        }

        if (activeCart != null) {
            item(key = "active_cart_header_${activeCart.id}") {
                ActiveCartHeaderCard(
                    cart = activeCart,
                    isCalibrated = uiState.isCalibrated,
                    activeCalibrationName = uiState.activeCalibrationName,
                    consistencyReport = uiState.consistencyReport,
                    loadPreviewBitmap = loadPreviewBitmap,
                    onEditImage = { onEditCartImage(activeCart.id) },
                    onEditPalette = { onOpenPaletteEditor(activeCart.id) },
                    onOpenExport = { showExportDialog = true },
                    onOpenQr = { showQrDialog = true },
                    onOpenDrape = {
                        activeCart.swatches.firstOrNull()?.let {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            drapeSwatch = it
                        }
                    },
                )
            }
        }

        if (activeCart == null || activeCart.swatches.isEmpty()) {
            item(key = "no_swatches_empty_state") {
                NoSwatchesEmptyState(
                    hasCart = activeCart != null,
                    onAddSwatchManually = onAddSwatchManually,
                    onCaptureNewCart = onOpenCapture,
                )
            }
        } else {
            val rows = activeCart.swatches.chunked(2)
            items(rows, key = { pair -> pair.joinToString("-") { it.id } }) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { swatchItem ->
                        Box(modifier = Modifier.weight(1f)) {
                            PinkedSwatchCard(
                                item = swatchItem,
                                onRollClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    rollDialogSwatch = it
                                },
                            )
                        }
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
            swatch = target,
            rolls = uiState.availableRolls,
            onDismiss = { rollDialogSwatch = null },
            onOpenDrape = {
                rollDialogSwatch = null
                drapeSwatch = target
            },
            onSelectRoll = { rollId ->
                onLinkSwatchToRoll(target, rollId)
                rollDialogSwatch = null
            },
        )
    }

    drapeSwatch?.let { target ->
        DrapePreviewDialog(
            primarySwatch = target,
            allSwatches = activeCart?.swatches.orEmpty(),
            onDismiss = { drapeSwatch = null },
        )
    }

    if (showExportDialog && activeCart != null) {
        ExportShareDialog(
            cart = activeCart,
            isCalibrated = uiState.isCalibrated,
            onDismiss = { showExportDialog = false },
            onExportPdf = {
                val pdf = ShadeCartAndroidExporters.exportA4Pdf(context, activeCart, uiState.isCalibrated)
                ShadeCartAndroidExporters.shareFile(context, pdf, "application/pdf")
                showExportDialog = false
            },
            onShareWhatsApp = {
                val png = ShadeCartAndroidExporters.exportPngCard(context, activeCart)
                val caption = ShadeCartFormatters.toWhatsAppShareCaption(activeCart)
                ShadeCartAndroidExporters.shareFile(context, png, "image/png", caption, preferWhatsApp = true)
                showExportDialog = false
            },
            onExportPng = {
                val png = ShadeCartAndroidExporters.exportPngCard(context, activeCart)
                ShadeCartAndroidExporters.shareFile(context, png, "image/png")
                showExportDialog = false
            },
            onExportGpl = {
                val gpl = ShadeCartAndroidExporters.exportGimpPalette(context, activeCart)
                ShadeCartAndroidExporters.shareFile(context, gpl, "text/plain")
                showExportDialog = false
            },
            onExportProcreate = {
                val swFile = ShadeCartAndroidExporters.exportProcreateSwatches(context, activeCart)
                ShadeCartAndroidExporters.shareFile(context, swFile, "application/zip")
                showExportDialog = false
            },
            onExportCsv = {
                val csv = ShadeCartAndroidExporters.exportCsv(context, activeCart)
                ShadeCartAndroidExporters.shareFile(context, csv, "text/csv")
                showExportDialog = false
            },
            onExportHtml = {
                val html = ShadeCartAndroidExporters.exportOfflineHtmlLookbook(
                    context = context,
                    carts = listOf(activeCart),
                    calibrated = uiState.isCalibrated,
                )
                ShadeCartAndroidExporters.shareFile(context, html, "text/html")
                showExportDialog = false
            },
        )
    }

    if (showQrDialog && activeCart != null) {
        val deepLink = activeCart.qrDeepLink.ifBlank { "azpallete://cart/${activeCart.id}" }
        val qrBitmap = remember(deepLink) { ShadeCartAndroidExporters.generateQrBitmap(deepLink, 360) }
        val hasNfc = remember { NfcAdapter.getDefaultAdapter(context) != null }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Digital Cart QR & NFC") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code for ${activeCart.fabricCode}",
                        modifier = Modifier.size(180.dp),
                    )
                    Text(
                        text = deepLink.ltrIsolate(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = if (hasNfc) {
                            "NFC Ready — hold AzPallete NFC tag to auto-open ${activeCart.fabricCode}."
                        } else {
                            "Scan this QR from any AzPallete device to open live stock."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Close") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveCartHeaderCard(
    cart: ShadeCartModel,
    isCalibrated: Boolean,
    activeCalibrationName: String?,
    consistencyReport: com.alazhartex.shadecart.domain.model.ShadeConsistencyReport?,
    loadPreviewBitmap: suspend (String) -> Bitmap?,
    onEditImage: () -> Unit,
    onEditPalette: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenDrape: () -> Unit,
) {
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
                        text = "Code: ${cart.fabricCode.ltrIsolate()} · v${cart.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val syncLabel = when (cart.syncStatus) {
                    SyncState.SYNCED -> "Synced"
                    SyncState.CONFLICT -> "Conflict"
                    SyncState.FAILED -> "Sync Failed"
                    SyncState.PENDING -> "Pending Sync"
                }
                AssistChip(
                    onClick = {},
                    label = { Text(syncLabel) },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
                if (isCalibrated) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = activeCalibrationName ?: "Calibrated",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (consistencyReport != null && consistencyReport.mixedLotWarnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mixed-Lot ΔE Alert: max ΔE ${String.format(Locale.US, "%.2f", consistencyReport.worstDeltaE)} across ${consistencyReport.mixedLotWarnings.size} shade(s).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            val previewPath = cart.editedImagePath ?: cart.originalImagePath
            if (!previewPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Rgb565PreviewImage(
                    imagePath = previewPath,
                    loadBitmap = loadPreviewBitmap,
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

            if (cart.swatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenExport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_shade_cart_button"),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export / PDF")
                    }
                    OutlinedButton(
                        onClick = onOpenDrape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Drape")
                    }
                    OutlinedButton(onClick = onOpenQr) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = "QR & NFC", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportShareDialog(
    cart: ShadeCartModel,
    isCalibrated: Boolean,
    onDismiss: () -> Unit,
    onExportPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onExportPng: () -> Unit,
    onExportGpl: () -> Unit,
    onExportProcreate: () -> Unit,
    onExportCsv: () -> Unit,
    onExportHtml: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export & Share (${cart.fabricCode})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isCalibrated) "Calibrated shade card ready for buyer or mill export."
                    else "Choose export format (saved privately via FileProvider):",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
                    Text("A4 Print PDF + QR Code")
                }
                Button(onClick = onShareWhatsApp, modifier = Modifier.fillMaxWidth()) {
                    Text("WhatsApp Card + Live Stock Summary")
                }
                OutlinedButton(onClick = onExportPng, modifier = Modifier.fillMaxWidth()) {
                    Text("Shareable PNG Image")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportGpl, modifier = Modifier.weight(1f)) { Text("GIMP .gpl") }
                    OutlinedButton(onClick = onExportProcreate, modifier = Modifier.weight(1f)) { Text(".swatches") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text("CSV Table") }
                    OutlinedButton(onClick = onExportHtml, modifier = Modifier.weight(1f)) { Text("HTML Lookbook") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
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
    swatch: SwatchModel,
    rolls: List<WarehouseRollModel>,
    onDismiss: () -> Unit,
    onOpenDrape: () -> Unit,
    onSelectRoll: (String?) -> Unit,
) {
    val currentRoll = swatch.linkedRoll
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
        title = { Text("Swatch #${swatch.swatchNumber} · Roll Inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDrape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Checkroom, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2.5D Fabric Drape Preview")
                }

                if (currentRoll != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Linked: ${currentRoll.rollCode} (${currentRoll.status.name})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Shed: ${currentRoll.warehouseShed} · ${currentRoll.availableMeters.toInt()}/${currentRoll.lengthMeters.toInt()} m · ${currentRoll.colorName}",
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
                                text = "${roll.warehouseShed} · ${roll.availableMeters.toInt()} m · ${roll.status.name}",
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DigitalShadeCardScreenPreview() {
    val sampleCart = ShadeCartModel(
        id = "cart-preview",
        fabricTypeId = "fab-silk-180",
        fabricName = "Silk 180",
        fabricCode = "SLK-180",
        weightGsm = 180,
        widthCm = 150,
        originalImagePath = null,
        editedImagePath = null,
        editStackJson = "{}",
        syncStatus = SyncState.SYNCED,
        version = 2L,
        swatches = listOf(
            SwatchModel(
                id = "sw-1",
                shadeCartId = "cart-preview",
                fabricTypeId = "fab-silk-180",
                swatchNumber = "5",
                colorHex = "#102A71",
                colorName = "Azhar Navy",
                positionOrder = 0,
                linkedRoll = WarehouseRollModel(
                    id = "roll-1",
                    rollCode = "RL-SLK-005",
                    fabricTypeId = "fab-silk-180",
                    shadeLabel = "5",
                    colorName = "Azhar Navy",
                    colorHex = "#102A71",
                    warehouseShed = "wh-alwa",
                    lengthMeters = 480.0,
                    status = RollStockStatus.IN_STOCK,
                ),
            ),
        ),
    )
    AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
        DigitalShadeCardScreen(
            uiState = DigitalShadeCardUiState(
                carts = listOf(sampleCart),
                selectedCartId = sampleCart.id,
                activeCart = sampleCart,
                isLoading = false,
            ),
            onSelectCart = {},
            onOpenCapture = {},
            onEditCartImage = {},
            onOpenPaletteEditor = {},
            onAddSwatchManually = {},
            onLinkSwatchToRoll = { _, _ -> },
        )
    }
}
