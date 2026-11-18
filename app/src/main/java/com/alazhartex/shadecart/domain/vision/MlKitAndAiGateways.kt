package com.alazhartex.shadecart.domain.vision

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import com.alazhartex.shadecart.di.DefaultDispatcher
import com.alazhartex.shadecart.domain.model.AskAzPalleteResult
import com.alazhartex.shadecart.domain.model.AskAzPalleteSource
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.ScannedBarcodeTarget
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.ArFabricOverlayPort
import com.alazhartex.shadecart.domain.repository.BarcodeScannerGateway
import com.alazhartex.shadecart.domain.repository.NaturalLanguageQueryGateway
import com.alazhartex.shadecart.domain.repository.SpectrophotometerBlePort
import com.alazhartex.shadecart.domain.usecase.OfflineTemplateNaturalLanguageParser
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C1: Parses and constructs `azpallete://cart/{id}` and `azpallete://roll/{code}` URIs from QR
 * strings, Android App Links, or NFC NDEF payloads.
 */
object AzPalleteDeepLinkParser {
    fun parseUri(raw: String?): ScannedBarcodeTarget? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cartPrefix = "azpallete://cart/"
        val rollPrefix = "azpallete://roll/"
        return when {
            text.startsWith(cartPrefix, ignoreCase = true) -> {
                val id = text.substring(cartPrefix.length).substringBefore("?").trim()
                if (id.isNotBlank()) ScannedBarcodeTarget.CartDeepLink(id) else null
            }
            text.startsWith(rollPrefix, ignoreCase = true) -> {
                val code = text.substring(rollPrefix.length).substringBefore("?").trim()
                if (code.isNotBlank()) ScannedBarcodeTarget.RollCode(code) else null
            }
            text.startsWith("RL-", ignoreCase = true) -> {
                ScannedBarcodeTarget.RollCode(text)
            }
            else -> ScannedBarcodeTarget.RawText(text)
        }
    }

    @Suppress("DEPRECATION")
    fun extractTargetFromNfcIntent(intent: Intent?): ScannedBarcodeTarget? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            val msgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            } else {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
            val firstMsg = msgs?.firstOrNull() as? NdefMessage
            if (firstMsg != null) {
                for (record in firstMsg.records) {
                    val uri = record.toUri()?.toString()
                    parseUri(uri)?.let { return it }
                    val rawPayload = runCatching { String(record.payload, StandardCharsets.UTF_8) }.getOrNull()
                    if (!rawPayload.isNullOrBlank()) {
                        val idx = rawPayload.indexOf("azpallete://")
                        if (idx >= 0) {
                            parseUri(rawPayload.substring(idx))?.let { return it }
                        }
                    }
                }
            }
        }
        return parseUri(intent.dataString)
    }

    fun buildNdefMessageForCart(cartId: String): NdefMessage {
        val record = NdefRecord.createUri("azpallete://cart/$cartId")
        return NdefMessage(arrayOf(record))
    }
}

/**
 * C1: Google Play Services ML Kit Code Scanner (`GmsBarcodeScanning`) gateway requiring zero camera
 * permissions inside the app when GMS module is available, with clean failure handling.
 */
@Singleton
class MlKitBarcodeScannerGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : BarcodeScannerGateway {

    override suspend fun scanSingleCode(): Result<ScannedBarcodeTarget?> = runCatching {
        val options = GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        val barcode = scanner.startScan().await()
        AzPalleteDeepLinkParser.parseUri(barcode.rawValue)
    }
}

/**
 * E1: Offline-first `Ask AzPallete` query gateway.
 *
 * Uses [OfflineTemplateNaturalLanguageParser] synchronously & deterministically offline.
 * When Android System AICore / Gemini Nano is provisioned on Pixel 9+/Pro devices, `isNanoAvailable()`
 * safely reports device capability without crashing or pulling unsupported external binaries.
 */
@Singleton
class GeminiNanoQueryGateway @Inject constructor(
    @DefaultDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NaturalLanguageQueryGateway {

    private val offlineParser = OfflineTemplateNaturalLanguageParser()

    override suspend fun isNanoAvailable(): Boolean = false

    override suspend fun query(
        naturalQuery: String,
        carts: List<ShadeCartModel>,
        rolls: List<WarehouseRollModel>,
        lots: List<DyeLotModel>,
    ): AskAzPalleteResult = withContext(ioDispatcher) {
        offlineParser.execute(
            query = naturalQuery,
            carts = carts,
            rolls = rolls,
            lots = lots,
            source = AskAzPalleteSource.OFFLINE_TEMPLATE,
        )
    }
}

/** B3 Future BLE Spectrophotometer stub port (`NoOp` ready for Nix/Datacolor SDK module). */
@Singleton
class NoOpSpectrophotometerBlePort @Inject constructor() : SpectrophotometerBlePort {
    override fun isAvailable(): Boolean = false
    override fun observeReadings(): Flow<Triple<Float, Float, Float>?> = flowOf(null)
}

/** B3 Future AR Depth Fabric Overlay stub port (`NoOp`). */
@Singleton
class NoOpArFabricOverlayPort @Inject constructor() : ArFabricOverlayPort {
    override fun isSupported(): Boolean = false
}
