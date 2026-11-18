package com.alazhartex.shadecart.ui.screens.buyer

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.ui.components.BrandLogoBadge
import com.alazhartex.shadecart.ui.components.DrapePreviewDialog
import com.alazhartex.shadecart.ui.components.PinkedFabricCut
import com.alazhartex.shadecart.ui.export.ShadeCartAndroidExporters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerShowcaseRoute(
    viewModel: BuyerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showQuoteSheet by remember { mutableStateOf(false) }
    var drapeTarget by remember { mutableStateOf<SwatchModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandLogoBadge(logoSize = 32.dp) },
                actions = {
                    if (uiState.favoritedSwatches.isNotEmpty()) {
                        TextButton(onClick = { showQuoteSheet = true }) {
                            Text("Quote (${uiState.favoritedSwatches.size})")
                        }
                    }
                    IconButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.testTag("buyer_unlock_pin_button"),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.exit_buyer_mode))
                    }
                },
            )
        },
    ) { padding ->
        val activeCart = uiState.activeCart
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("buyer_showcase_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "buyer_banner") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${stringResource(R.string.buyer_showcase_title)} · ${uiState.session.companyName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val htmlFile = ShadeCartAndroidExporters.exportOfflineHtmlLookbook(
                                        context = context,
                                        carts = uiState.carts,
                                        calibrated = false,
                                    )
                                    ShadeCartAndroidExporters.shareFile(context, htmlFile, "text/html")
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Html, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("HTML Lookbook")
                            }
                            Button(
                                onClick = { showQuoteSheet = true },
                                enabled = uiState.favoritedSwatches.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request Quote")
                            }
                        }
                    }
                }
            }

            if (uiState.carts.size > 1) {
                item(key = "buyer_cart_selector") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.carts, key = { it.id }) { cart ->
                            FilterChip(
                                selected = cart.id == activeCart?.id,
                                onClick = { viewModel.selectCart(cart.id) },
                                label = { Text("${cart.fabricName} (${cart.fabricCode})") },
                            )
                        }
                    }
                }
            }

            if (activeCart != null) {
                item(key = "buyer_specs") {
                    Text(
                        text = "${activeCart.fabricName} · ${activeCart.weightGsm} GSM (g/m²) · ${activeCart.widthCm} cm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val rows = activeCart.swatches.chunked(2)
                items(rows, key = { pair -> pair.joinToString("-") { it.id } }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { sw ->
                            val isFav = uiState.session.favoritedSwatchIds.contains(sw.id)
                            val color = Color(HexColorValidator.parseArgbOrNull(sw.colorHex) ?: 0xFF102A71.toInt())
                            Card(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box {
                                        PinkedFabricCut(
                                            fillColor = color,
                                            contentDescription = "#${sw.swatchNumber} ${sw.colorHex}",
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        IconButton(
                                            onClick = { viewModel.toggleFavorite(sw.id) },
                                            modifier = Modifier.align(Alignment.TopEnd),
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                contentDescription = "Favorite shade",
                                                tint = if (isFav) Color(0xFFD90A0A) else Color.White,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "#${sw.swatchNumber} · ${sw.colorName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = sw.colorHex.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    TextButton(
                                        onClick = { drapeTarget = sw },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Icon(Icons.Rounded.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("2.5D Drape")
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    drapeTarget?.let { target ->
        DrapePreviewDialog(
            primarySwatch = target,
            allSwatches = uiState.activeCart?.swatches.orEmpty(),
            onDismiss = { drapeTarget = null },
        )
    }

    if (showQuoteSheet) {
        val quoteBody = viewModel.buildInquiryText()
        AlertDialog(
            onDismissRequest = { showQuoteSheet = false },
            title = { Text("Buyer Quote Inquiry") },
            text = {
                Text(text = quoteBody, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                Button(
                    onClick = {
                        ShadeCartAndroidExporters.sharePlainText(context, "Al AzharTex Shade Quote", quoteBody)
                        showQuoteSheet = false
                    },
                ) { Text("Send Inquiry") }
            },
            dismissButton = {
                TextButton(onClick = { showQuoteSheet = false }) { Text("Close") }
            },
        )
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                viewModel.clearPinError()
                showPinDialog = false
            },
            title = { Text(stringResource(R.string.exit_buyer_mode)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter 4-digit PIN to unlock internal mill tools.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = uiState.pinError != null,
                        singleLine = true,
                        modifier = Modifier.testTag("buyer_exit_pin_input"),
                    )
                    uiState.pinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.tryExitWithPin(pin) { showPinDialog = false } },
                    modifier = Modifier.testTag("buyer_exit_confirm_button"),
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPinError()
                        showPinDialog = false
                    },
                ) { Text("Cancel") }
            },
        )
    }
}
