package com.alazhartex.shadecart.data.nearby

import android.content.Context
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NearbyShareBundle(
    val carts: List<NearbySharedCart>,
    val sentAtMillis: Long,
)

@Serializable
data class NearbySharedCart(
    val id: String,
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int,
    val widthCm: Int,
    val swatches: List<NearbySharedSwatch>,
)

@Serializable
data class NearbySharedSwatch(
    val id: String,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String,
)

enum class NearbySyncStatus {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    ERROR,
}

data class NearbyPeerEndpoint(
    val endpointId: String,
    val name: String,
)

data class NearbyShareUiState(
    val status: NearbySyncStatus = NearbySyncStatus.IDLE,
    val discoveredPeers: List<NearbyPeerEndpoint> = emptyList(),
    val connectedEndpointName: String? = null,
    val statusMessage: String? = null,
    val receivedCartsCount: Int = 0,
)

/**
 * C5b: Offline Nearby Connections (`P2P_POINT_TO_POINT`) coordinator enabling two mill devices to
 * sync shade carts over Wi-Fi Direct / Bluetooth without internet or a central server.
 */
@Singleton
class NearbyShareCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ShadeCartRepositoryContract,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { Nearby.getConnectionsClient(context) }

    private val _state = MutableStateFlow(NearbyShareUiState())
    val state: StateFlow<NearbyShareUiState> = _state.asStateFlow()

    private var activeEndpointId: String? = null
    private var pendingOutgoingCarts: List<ShadeCartModel> = emptyList()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val raw = payload.asBytes()?.toString(StandardCharsets.UTF_8) ?: return
                scope.launch {
                    importReceivedBundle(raw)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.COMPLETED,
                    statusMessage = "Nearby transfer complete.",
                )
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ERROR,
                    statusMessage = "Nearby payload transfer failed.",
                )
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
            _state.value = _state.value.copy(
                connectedEndpointName = info.endpointName,
                statusMessage = "Connecting to ${info.endpointName}...",
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                activeEndpointId = endpointId
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.CONNECTED,
                    statusMessage = "Connected to peer.",
                )
                if (pendingOutgoingCarts.isNotEmpty()) {
                    sendCartsToEndpoint(endpointId, pendingOutgoingCarts)
                }
            } else {
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ERROR,
                    statusMessage = "Nearby connection rejected (${result.status.statusCode}).",
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (activeEndpointId == endpointId) activeEndpointId = null
            _state.value = _state.value.copy(
                status = NearbySyncStatus.IDLE,
                connectedEndpointName = null,
            )
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val updated = (_state.value.discoveredPeers.filterNot { it.endpointId == endpointId } +
                NearbyPeerEndpoint(endpointId, info.endpointName))
            _state.value = _state.value.copy(
                discoveredPeers = updated,
                statusMessage = "Found nearby device: ${info.endpointName}",
            )
        }

        override fun onEndpointLost(endpointId: String) {
            _state.value = _state.value.copy(
                discoveredPeers = _state.value.discoveredPeers.filterNot { it.endpointId == endpointId },
            )
        }
    }

    fun startAdvertisingAsReceiver(deviceDisplayName: String) {
        stopAll()
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(deviceDisplayName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ADVERTISING,
                    statusMessage = "Waiting for nearby sender...",
                )
            }
            .addOnFailureListener { exc ->
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ERROR,
                    statusMessage = exc.message ?: "Unable to start Nearby advertising.",
                )
            }
    }

    fun startDiscoveryToSend(deviceDisplayName: String, cartsToShare: List<ShadeCartModel>) {
        stopAll()
        pendingOutgoingCarts = cartsToShare
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.DISCOVERING,
                    discoveredPeers = emptyList(),
                    statusMessage = "Scanning for nearby AzPallete devices...",
                )
            }
            .addOnFailureListener { exc ->
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ERROR,
                    statusMessage = exc.message ?: "Unable to start Nearby discovery.",
                )
            }
    }

    fun connectAndSend(endpointId: String, senderName: String) {
        client.requestConnection(senderName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { exc ->
                _state.value = _state.value.copy(
                    status = NearbySyncStatus.ERROR,
                    statusMessage = exc.message ?: "Connection request failed.",
                )
            }
    }

    private fun sendCartsToEndpoint(endpointId: String, carts: List<ShadeCartModel>) {
        val bundle = NearbyShareBundle(
            carts = carts.map { c ->
                NearbySharedCart(
                    id = c.id,
                    fabricName = c.fabricName,
                    fabricCode = c.fabricCode,
                    weightGsm = c.weightGsm,
                    widthCm = c.widthCm,
                    swatches = c.swatches.map { sw ->
                        NearbySharedSwatch(sw.id, sw.swatchNumber, sw.colorHex, sw.colorName)
                    },
                )
            },
            sentAtMillis = System.currentTimeMillis(),
        )
        val payloadBytes = json.encodeToString(bundle).toByteArray(StandardCharsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
        _state.value = _state.value.copy(
            status = NearbySyncStatus.TRANSFERRING,
            statusMessage = "Sending ${carts.size} shade carts nearby...",
        )
    }

    private suspend fun importReceivedBundle(rawJson: String) {
        val bundle = runCatching { json.decodeFromString<NearbyShareBundle>(rawJson) }.getOrNull()
            ?: return
        val existingIds = repository.observeCarts().first().map { it.id }.toSet()
        var imported = 0
        for (shared in bundle.carts) {
            if (existingIds.contains(shared.id)) continue
            repository.createOrGetDraftCart(
                ShadeCartCreateInput(
                    id = shared.id,
                    fabricName = shared.fabricName,
                    fabricCode = shared.fabricCode,
                    weightGsm = shared.weightGsm,
                    widthCm = shared.widthCm,
                    swatches = shared.swatches.mapIndexed { idx, sw ->
                        SwatchInput(
                            id = sw.id,
                            swatchNumber = sw.swatchNumber,
                            colorHex = sw.colorHex,
                            colorName = sw.colorName,
                            positionOrder = idx,
                        )
                    },
                ),
            )
            imported++
        }
        _state.value = _state.value.copy(
            status = NearbySyncStatus.COMPLETED,
            receivedCartsCount = _state.value.receivedCartsCount + imported,
            statusMessage = "Imported $imported shade cart(s) from nearby peer.",
        )
    }

    fun stopAll() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        activeEndpointId = null
        _state.value = _state.value.copy(
            status = NearbySyncStatus.IDLE,
            discoveredPeers = emptyList(),
        )
    }

    companion object {
        const val SERVICE_ID = "com.alazhartex.shadecart.nearby"
    }
}
