package com.alazhartex.shadecart.ui.screens.inventory

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.model.AskAzPalleteSource
import com.alazhartex.shadecart.domain.model.MillInsightsReport
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.RoleGate
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.ui.components.ColorBlindPatternDot
import com.alazhartex.shadecart.ui.export.ShadeCartAndroidExporters
import java.util.Locale

private val FinderPresets = listOf(
    "Azhar Navy" to "#102A71",
    "Crimson" to "#CD1820",
    "Emerald" to "#0F5132",
    "Cairo Sand" to "#D4A017",
    "Burgundy" to "#761D3A",
    "Nile Teal" to "#187480",
)

@Composable
fun InventoryAndMillRoute(
    onOpenCart: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.submitAskQuery(spoken)
            }
        }
    }

    InventoryAndMillScreen(
        uiState = uiState,
        onSelectTab = viewModel::selectTab,
        onOpenCart = onOpenCart,
        onTargetHexChanged = viewModel::updateTargetHex,
        onMaxDeltaEChanged = viewModel::updateMaxDeltaE,
        onFabricFilterChanged = viewModel::updateFabricFilter,
        onToggleOutOfStock = viewModel::toggleIncludeOutOfStock,
        onScanQr = { viewModel.scanQrCode(onCartFound = onOpenCart, onRollFound = {}) },
        onPrintRollLabelsPdf = {
            val file = ShadeCartAndroidExporters.exportRollQrLabelSheetPdf(context, uiState.rolls)
            ShadeCartAndroidExporters.shareFile(context, file, "application/pdf")
        },
        onCreateRoll = viewModel::createRoll,
        onAddDyeLot = viewModel::addDyeLot,
        onReserveMeters = viewModel::reserveMeters,
        onReleaseReservation = viewModel::releaseActiveReservation,
        onRecordCut = viewModel::recordCut,
        onAskQueryChanged = viewModel::updateAskQuery,
        onSubmitAsk = { viewModel.submitAskQuery() },
        onLaunchVoiceAsk = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask AzPallete about shades, stock, or lots")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                voiceLauncher.launch(intent)
            } else {
                viewModel.submitAskQuery("Navy more than 200 meters")
            }
        },
        onDismissStatus = viewModel::clearStatusMessage,
        modifier = modifier,
    )
}

@Composable
fun InventoryAndMillScreen(
    uiState: InventoryUiState,
    onSelectTab: (MillHubTab) -> Unit,
    onOpenCart: (String) -> Unit,
    onTargetHexChanged: (String) -> Unit,
    onMaxDeltaEChanged: (Float) -> Unit,
    onFabricFilterChanged: (String?) -> Unit,
    onToggleOutOfStock: () -> Unit,
    onScanQr: () -> Unit,
    onPrintRollLabelsPdf: () -> Unit,
    onCreateRoll: (String, String, String, String, String, String, Double, String) -> Unit,
    onAddDyeLot: (String, String, Double, String) -> Unit,
    onReserveMeters: (String, Double, String) -> Unit,
    onReleaseReservation: (String) -> Unit,
    onRecordCut: (String, Double, Int) -> Unit,
    onAskQueryChanged: (String) -> Unit,
    onSubmitAsk: () -> Unit,
    onLaunchVoiceAsk: () -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("mill_inventory_hub"),
    ) {
        SecondaryTabRow(selectedTabIndex = uiState.activeTab.ordinal) {
            Tab(
                selected = uiState.activeTab == MillHubTab.REVERSE_FINDER,
                onClick = { onSelectTab(MillHubTab.REVERSE_FINDER) },
                text = { Text(stringResource(R.string.reverse_finder)) },
                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )
            Tab(
                selected = uiState.activeTab == MillHubTab.WAREHOUSE_ROLLS,
                onClick = { onSelectTab(MillHubTab.WAREHOUSE_ROLLS) },
                text = { Text(stringResource(R.string.rolls_lots_cuts)) },
                icon = { Icon(Icons.Rounded.Inventory2, contentDescription = null) },
            )
            Tab(
                selected = uiState.activeTab == MillHubTab.ASK_AND_INSIGHTS,
                onClick = { onSelectTab(MillHubTab.ASK_AND_INSIGHTS) },
                text = { Text(stringResource(R.string.ask_insights)) },
                icon = { Icon(Icons.Rounded.Analytics, contentDescription = null) },
            )
        }

        uiState.statusMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismissStatus() },
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        when (uiState.activeTab) {
            MillHubTab.REVERSE_FINDER -> ReverseFinderContent(
                uiState = uiState,
                onTargetHexChanged = onTargetHexChanged,
                onMaxDeltaEChanged = onMaxDeltaEChanged,
                onFabricFilterChanged = onFabricFilterChanged,
                onToggleOutOfStock = onToggleOutOfStock,
                onOpenCart = onOpenCart,
            )

            MillHubTab.WAREHOUSE_ROLLS -> WarehouseRollsContent(
                uiState = uiState,
                onScanQr = onScanQr,
                onPrintRollLabelsPdf = onPrintRollLabelsPdf,
                onCreateRoll = onCreateRoll,
                onAddDyeLot = onAddDyeLot,
                onReserveMeters = onReserveMeters,
                onReleaseReservation = onReleaseReservation,
                onRecordCut = onRecordCut,
            )

            MillHubTab.ASK_AND_INSIGHTS -> AskAndInsightsContent(
                uiState = uiState,
                onAskQueryChanged = onAskQueryChanged,
                onSubmitAsk = onSubmitAsk,
                onLaunchVoiceAsk = onLaunchVoiceAsk,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReverseFinderContent(
    uiState: InventoryUiState,
    onTargetHexChanged: (String) -> Unit,
    onMaxDeltaEChanged: (Float) -> Unit,
    onFabricFilterChanged: (String?) -> Unit,
    onToggleOutOfStock: () -> Unit,
    onOpenCart: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "finder_controls") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reverse_shade_finder_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val argb = HexColorValidator.parseArgbOrNull(uiState.targetHexInput) ?: 0xFF102A71.toInt()
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        OutlinedTextField(
                            value = uiState.targetHexInput,
                            onValueChange = onTargetHexChanged,
                            label = { Text("Target HEX (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FinderPresets.forEach { (label, hex) ->
                            AssistChip(
                                onClick = { onTargetHexChanged(hex) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(HexColorValidator.parseArgbOrNull(hex) ?: 0xFF102A71.toInt())),
                                    )
                                },
                                label = { Text(label) },
                            )
                        }
                    }

                    Text(
                        text = "Max CIEDE2000 Tolerance: ΔE ≤ ${String.format(Locale.US, "%.1f", uiState.maxDeltaE)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = uiState.maxDeltaE,
                        onValueChange = onMaxDeltaEChanged,
                        valueRange = 1f..30f,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = uiState.fabricFilterId == null,
                            onClick = { onFabricFilterChanged(null) },
                            label = { Text("All Fabrics") },
                        )
                        uiState.fabricTypes.forEach { fab ->
                            FilterChip(
                                selected = uiState.fabricFilterId == fab.id,
                                onClick = { onFabricFilterChanged(fab.id) },
                                label = { Text(fab.name) },
                            )
                        }
                        FilterChip(
                            selected = uiState.includeOutOfStock,
                            onClick = onToggleOutOfStock,
                            label = { Text("Include Out of Stock") },
                        )
                    }
                }
            }
        }

        if (uiState.reverseMatches.isEmpty()) {
            item(key = "no_matches") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No saved shades within ΔE ≤ ${String.format(Locale.US, "%.1f", uiState.maxDeltaE)}. Capture a shade cart or raise tolerance.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(uiState.reverseMatches, key = { it.swatch.id }) { match ->
                val badgeText = when {
                    match.deltaE < 2.0f -> "Exact · ΔE ${String.format(Locale.US, "%.1f", match.deltaE)}"
                    match.deltaE < 5.0f -> "Close · ΔE ${String.format(Locale.US, "%.1f", match.deltaE)}"
                    else -> "Match · ΔE ${String.format(Locale.US, "%.1f", match.deltaE)}"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCart(match.swatch.shadeCartId) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ColorBlindPatternDot(
                            colorHex = match.swatch.colorHex,
                            patternSeed = match.swatch.positionOrder,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${match.cartName} · #${match.swatch.swatchNumber}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${match.swatch.colorName} (${match.swatch.colorHex.uppercase(Locale.US)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            match.roll?.let { r ->
                                Text(
                                    text = "${r.rollCode} · ${r.warehouseShed} · ${r.availableMeters.toInt()}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            color = if (match.deltaE < 2.0f) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehouseRollsContent(
    uiState: InventoryUiState,
    onScanQr: () -> Unit,
    onPrintRollLabelsPdf: () -> Unit,
    onCreateRoll: (String, String, String, String, String, String, Double, String) -> Unit,
    onAddDyeLot: (String, String, Double, String) -> Unit,
    onReserveMeters: (String, Double, String) -> Unit,
    onReleaseReservation: (String) -> Unit,
    onRecordCut: (String, Double, Int) -> Unit,
) {
    var showCreateRollDialog by remember { mutableStateOf(false) }
    var reserveTargetRoll by remember { mutableStateOf<WarehouseRollModel?>(null) }
    var cutTargetRoll by remember { mutableStateOf<WarehouseRollModel?>(null) }
    var lotTargetRoll by remember { mutableStateOf<WarehouseRollModel?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "roll_actions_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR")
                }
                OutlinedButton(onClick = onPrintRollLabelsPdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR Labels PDF")
                }
                if (RoleGate.canEditCarts(uiState.currentRole)) {
                    IconButton(onClick = { showCreateRollDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "New Roll")
                    }
                }
            }
        }

        if (uiState.activeReservations.isNotEmpty()) {
            item(key = "active_reservations_card") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Active Meter Reservations (${uiState.activeReservations.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        uiState.activeReservations.forEach { res ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${res.metersReserved.toInt()}m by ${res.reservedBy} (${res.note.ifBlank { res.rollId }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onReleaseReservation(res.id) }) {
                                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Release")
                                }
                            }
                        }
                    }
                }
            }
        }

        items(uiState.rolls, key = { it.id }) { roll ->
            val rollLots = uiState.lots.filter { it.rollId == roll.id }
            val argb = HexColorValidator.parseArgbOrNull(roll.colorHex) ?: 0xFF102A71.toInt()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${roll.rollCode} · Shade #${roll.shadeLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${roll.colorName} · ${roll.warehouseShed} · Lot: ${roll.lotCode.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val statusColor = when (roll.status) {
                            RollStockStatus.LOW_STOCK -> MaterialTheme.colorScheme.errorContainer
                            RollStockStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.errorContainer
                            RollStockStatus.RESERVED -> MaterialTheme.colorScheme.tertiaryContainer
                            RollStockStatus.IN_STOCK -> MaterialTheme.colorScheme.primaryContainer
                        }
                        Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                            Text(
                                text = "${roll.availableMeters.toInt()} / ${roll.lengthMeters.toInt()}m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (rollLots.isNotEmpty()) {
                        Text(
                            text = "Dye Lots: " + rollLots.joinToString(", ") {
                                "${it.lotCode} (ΔE ${String.format(Locale.US, "%.2f", it.masterDeltaE)})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (RoleGate.canReserveOrCut(uiState.currentRole)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { reserveTargetRoll = roll },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Reserve")
                            }
                            Button(
                                onClick = { cutTargetRoll = roll },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Cut")
                            }
                            TextButton(onClick = { lotTargetRoll = roll }) {
                                Text("+ Lot")
                            }
                        }
                    }
                }
            }
        }

        if (uiState.priceTiers.isNotEmpty()) {
            item(key = "price_tiers_card") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Volume Pricing Tiers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        uiState.priceTiers.forEach { tier ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${tier.tierName} (≥ ${tier.minMeters.toInt()}m)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", tier.pricePerMeter)} ${tier.currencyCode}/m",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.cutLogs.isNotEmpty()) {
            item(key = "cut_logs_card") {
                Card {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recent Cut Ledger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        uiState.cutLogs.take(5).forEach { cut ->
                            Text(
                                text = "${cut.metersCut}m (${cut.pieceCount} pcs) · ${cut.operatorName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateRollDialog) {
        CreateRollDialog(
            fabrics = uiState.fabricTypes,
            onDismiss = { showCreateRollDialog = false },
            onSave = { code, fabId, shade, cname, chex, shed, meters, lot ->
                onCreateRoll(code, fabId, shade, cname, chex, shed, meters, lot)
                showCreateRollDialog = false
            },
        )
    }

    reserveTargetRoll?.let { roll ->
        ReserveOrCutDialog(
            title = "Reserve Meters · ${roll.rollCode}",
            actionLabel = "Reserve",
            maxAvailable = roll.availableMeters,
            showPieceCount = false,
            onDismiss = { reserveTargetRoll = null },
            onConfirm = { meters, _, note ->
                onReserveMeters(roll.id, meters, note)
                reserveTargetRoll = null
            },
        )
    }

    cutTargetRoll?.let { roll ->
        ReserveOrCutDialog(
            title = "Log Cut · ${roll.rollCode}",
            actionLabel = "Log Cut",
            maxAvailable = roll.availableMeters,
            showPieceCount = true,
            onDismiss = { cutTargetRoll = null },
            onConfirm = { meters, pcs, _ ->
                onRecordCut(roll.id, meters, pcs)
                cutTargetRoll = null
            },
        )
    }

    lotTargetRoll?.let { roll ->
        AddDyeLotDialog(
            rollCode = roll.rollCode,
            onDismiss = { lotTargetRoll = null },
            onSave = { lotCode, deltaE, notes ->
                onAddDyeLot(roll.id, lotCode, deltaE, notes)
                lotTargetRoll = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskAndInsightsContent(
    uiState: InventoryUiState,
    onAskQueryChanged: (String) -> Unit,
    onSubmitAsk: () -> Unit,
    onLaunchVoiceAsk: () -> Unit,
) {
    val quickPrompts = listOf(
        "Navy more than 200m",
        "Low stock",
        "In stock Silk 180",
        "كحلي أكثر من 200 متر",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "ask_azpallete_card") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ask_azpallete),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = if (uiState.isNanoAvailable) "Gemini Nano" else "Offline Parser",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = uiState.askQuery,
                            onValueChange = onAskQueryChanged,
                            label = { Text("Ask in English or العربية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onLaunchVoiceAsk) {
                            Icon(Icons.Rounded.Mic, contentDescription = "Voice Query")
                        }
                        Button(onClick = onSubmitAsk) { Text("Ask") }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickPrompts.forEach { sample ->
                            AssistChip(
                                onClick = {
                                    onAskQueryChanged(sample)
                                    onSubmitAsk()
                                },
                                label = { Text(sample) },
                            )
                        }
                    }

                    uiState.askResult?.let { res ->
                        HorizontalDivider()
                        Text(
                            text = "${res.summary} (${if (res.source == AskAzPalleteSource.GEMINI_NANO) "AI" else "Local"})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        res.items.take(6).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(HexColorValidator.parseArgbOrNull(item.colorHex) ?: 0xFF102A71.toInt())),
                                )
                                Text(
                                    text = "${item.fabricName} #${item.swatchNumber} · ${item.colorName} · ${item.rollCode ?: "No Roll"} (${item.availableMeters?.toInt() ?: 0}m)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        uiState.insights?.let { report ->
            item(key = "mill_insights_dashboard") {
                MillInsightsDashboardCard(report = report)
            }
        }
    }
}

@Composable
private fun MillInsightsDashboardCard(report: MillInsightsReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.mill_insights_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Inventory Value: ${String.format(Locale.US, "%,.0f", report.totalInventoryValue)} EGP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Active Carts: ${report.totalActiveCarts} · Low Stock Rolls: ${report.lowStockCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pending Sync: ${report.pendingSyncCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Canvas Sync Health Gauge
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    val primary = MaterialTheme.colorScheme.primary
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = track,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = primary,
                            startAngle = -90f,
                            sweepAngle = 360f * report.syncHealthScore,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Text(
                        text = "${(report.syncHealthScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (report.stockValuePerShed.isNotEmpty()) {
                Text(
                    text = "Stock Value by Warehouse Shed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val maxVal = report.stockValuePerShed.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val barColor = MaterialTheme.colorScheme.primary
                val entries = report.stockValuePerShed.entries.toList()
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((entries.size * 28).coerceAtLeast(48).dp),
                ) {
                    val rowH = size.height / entries.size.coerceAtLeast(1)
                    entries.forEachIndexed { idx, (_, value) ->
                        val frac = (value / maxVal).toFloat().coerceIn(0.05f, 1f)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(0f, idx * rowH + 6f),
                            size = Size(size.width * frac, rowH - 12f),
                        )
                    }
                }
                entries.forEach { (shed, value) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(shed, style = MaterialTheme.typography.labelSmall)
                        Text("${String.format(Locale.US, "%,.0f", value)} EGP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (report.lotVarianceSummary.isNotEmpty()) {
                HorizontalDivider()
                Text("Dye Lot ΔE Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                report.lotVarianceSummary.forEach { item ->
                    Text(
                        text = "${item.colorFamily}: worst ΔE ${String.format(Locale.US, "%.2f", item.worstDeltaE)} (${item.lotCount} lots)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRollDialog(
    fabrics: List<com.alazhartex.shadecart.domain.model.FabricTypeModel>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Double, String) -> Unit,
) {
    var rollCode by remember { mutableStateOf("RL-SLK-099") }
    var shadeLabel by remember { mutableStateOf("10") }
    var colorName by remember { mutableStateOf("Azhar Navy") }
    var colorHex by remember { mutableStateOf("#102A71") }
    var shed by remember { mutableStateOf("wh-alwa") }
    var metersText by remember { mutableStateOf("350") }
    var lotCode by remember { mutableStateOf("LOT-2026-10A") }
    var selectedFabId by remember { mutableStateOf(fabrics.firstOrNull()?.id ?: "fab-silk-180") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Warehouse Roll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rollCode, onValueChange = { rollCode = it }, label = { Text("Roll Code") }, singleLine = true)
                OutlinedTextField(value = shadeLabel, onValueChange = { shadeLabel = it }, label = { Text("Shade #") }, singleLine = true)
                OutlinedTextField(value = colorName, onValueChange = { colorName = it }, label = { Text("Color Name") }, singleLine = true)
                OutlinedTextField(value = colorHex, onValueChange = { colorHex = it }, label = { Text("HEX") }, singleLine = true)
                OutlinedTextField(value = shed, onValueChange = { shed = it }, label = { Text("Warehouse Shed") }, singleLine = true)
                OutlinedTextField(
                    value = metersText,
                    onValueChange = { metersText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Length (meters)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = lotCode, onValueChange = { lotCode = it }, label = { Text("Dye Lot Code") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = metersText.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
                    onSave(rollCode, selectedFabId, shadeLabel, colorName, colorHex, shed, m, lotCode)
                },
            ) { Text("Create Roll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReserveOrCutDialog(
    title: String,
    actionLabel: String,
    maxAvailable: Double,
    showPieceCount: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, Int, String) -> Unit,
) {
    var metersText by remember { mutableStateOf("50") }
    var piecesText by remember { mutableStateOf("2") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available: ${String.format(Locale.US, "%.1f", maxAvailable)} m", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = metersText,
                    onValueChange = { metersText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Meters") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (showPieceCount) {
                    OutlinedTextField(
                        value = piecesText,
                        onValueChange = { piecesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Piece Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Buyer / Order Note") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = metersText.toDoubleOrNull() ?: 0.0
                    val pcs = piecesText.toIntOrNull() ?: 1
                    if (m > 0.0) onConfirm(m, pcs, note)
                },
            ) { Text(actionLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddDyeLotDialog(
    rollCode: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit,
) {
    var lotCode by remember { mutableStateOf("LOT-2026-NEW") }
    var deltaEText by remember { mutableStateOf("1.2") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Dye Lot · $rollCode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = lotCode, onValueChange = { lotCode = it }, label = { Text("Lot Code") }, singleLine = true)
                OutlinedTextField(
                    value = deltaEText,
                    onValueChange = { deltaEText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Master ΔE Variance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(lotCode, deltaEText.toDoubleOrNull() ?: 0.0, notes) }) { Text("Save Lot") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
