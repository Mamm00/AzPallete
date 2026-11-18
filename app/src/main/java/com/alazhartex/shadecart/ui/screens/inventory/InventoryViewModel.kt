package com.alazhartex.shadecart.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.data.sync.LowStockAlertScheduler
import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AskAzPalleteResult
import com.alazhartex.shadecart.domain.model.ColorSearchResult
import com.alazhartex.shadecart.domain.model.CutLogModel
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.MillInsightsReport
import com.alazhartex.shadecart.domain.model.PriceTierModel
import com.alazhartex.shadecart.domain.model.ReservationModel
import com.alazhartex.shadecart.domain.model.RoleGate
import com.alazhartex.shadecart.domain.model.ScannedBarcodeTarget
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.BarcodeScannerGateway
import com.alazhartex.shadecart.domain.usecase.AskAzPalleteUseCase
import com.alazhartex.shadecart.domain.usecase.ComputeMillInsightsUseCase
import com.alazhartex.shadecart.domain.usecase.CreateDyeLotUseCase
import com.alazhartex.shadecart.domain.usecase.CreateWarehouseRollUseCase
import com.alazhartex.shadecart.domain.usecase.LogRollCutUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveActiveReservationsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveAllLotsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveAppSessionPreferencesUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveBuyerSessionUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveCutLogsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveFabricTypesUseCase
import com.alazhartex.shadecart.domain.usecase.ObservePriceTiersUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveSyncQueueUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveWarehouseRollsUseCase
import com.alazhartex.shadecart.domain.usecase.ReleaseReservationUseCase
import com.alazhartex.shadecart.domain.usecase.ReserveRollMetersUseCase
import com.alazhartex.shadecart.domain.usecase.SearchShadesByColorUseCase
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MillHubTab {
    REVERSE_FINDER,
    WAREHOUSE_ROLLS,
    ASK_AND_INSIGHTS,
}

data class InventoryUiState(
    val activeTab: MillHubTab = MillHubTab.REVERSE_FINDER,
    val currentRole: AppRole = AppRole.MANAGER,
    val operatorName: String = "Mill Operator",
    val carts: List<ShadeCartModel> = emptyList(),
    val fabricTypes: List<FabricTypeModel> = emptyList(),
    val rolls: List<WarehouseRollModel> = emptyList(),
    val lots: List<DyeLotModel> = emptyList(),
    val activeReservations: List<ReservationModel> = emptyList(),
    val cutLogs: List<CutLogModel> = emptyList(),
    val priceTiers: List<PriceTierModel> = emptyList(),
    // C4 Reverse Shade Finder
    val targetHexInput: String = "#102A71",
    val maxDeltaE: Float = 15.0f,
    val fabricFilterId: String? = null,
    val includeOutOfStock: Boolean = false,
    val reverseMatches: List<ColorSearchResult> = emptyList(),
    // E1 Ask AzPallete
    val askQuery: String = "",
    val askResult: AskAzPalleteResult? = null,
    val isNanoAvailable: Boolean = false,
    // E2 Mill Insights
    val insights: MillInsightsReport? = null,
    val statusMessage: String? = null,
)

private data class FinderFilterState(
    val activeTab: MillHubTab = MillHubTab.REVERSE_FINDER,
    val targetHexInput: String = "#102A71",
    val maxDeltaE: Float = 15.0f,
    val fabricFilterId: String? = null,
    val includeOutOfStock: Boolean = false,
    val askQuery: String = "",
    val askResult: AskAzPalleteResult? = null,
    val isNanoAvailable: Boolean = false,
    val statusMessage: String? = null,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    observeShadeCarts: ObserveShadeCartsUseCase,
    observeFabricTypes: ObserveFabricTypesUseCase,
    observeWarehouseRolls: ObserveWarehouseRollsUseCase,
    observeAllLots: ObserveAllLotsUseCase,
    observeActiveReservations: ObserveActiveReservationsUseCase,
    observeCutLogs: ObserveCutLogsUseCase,
    observePriceTiers: ObservePriceTiersUseCase,
    observeSyncQueue: ObserveSyncQueueUseCase,
    observeAppSession: ObserveAppSessionPreferencesUseCase,
    observeBuyerSession: ObserveBuyerSessionUseCase,
    private val searchShadesByColor: SearchShadesByColorUseCase,
    private val createWarehouseRoll: CreateWarehouseRollUseCase,
    private val createDyeLot: CreateDyeLotUseCase,
    private val reserveRollMeters: ReserveRollMetersUseCase,
    private val releaseReservation: ReleaseReservationUseCase,
    private val logRollCut: LogRollCutUseCase,
    private val computeMillInsights: ComputeMillInsightsUseCase,
    private val askAzPallete: AskAzPalleteUseCase,
    private val barcodeScannerGateway: BarcodeScannerGateway,
    private val lowStockAlertScheduler: LowStockAlertScheduler,
) : ViewModel() {

    private val localFilter = MutableStateFlow(FinderFilterState())

    init {
        viewModelScope.launch {
            val nano = askAzPallete.isNanoAvailable()
            localFilter.update { it.copy(isNanoAvailable = nano) }
        }
    }

    private val catalogFlow = combine(
        observeShadeCarts(),
        observeFabricTypes(),
        observeWarehouseRolls(null),
        observeAllLots(),
        observeActiveReservations(),
    ) { carts, fabrics, rolls, lots, reservations ->
        CatalogBundle(carts, fabrics, rolls, lots, reservations)
    }

    private val ledgerFlow = combine(
        observeCutLogs(),
        observePriceTiers(),
        observeSyncQueue(),
        observeAppSession(),
        observeBuyerSession(),
    ) { cuts, tiers, queue, session, buyer ->
        LedgerBundle(cuts, tiers, queue, session, buyer)
    }

    val uiState: StateFlow<InventoryUiState> = combine(
        catalogFlow,
        ledgerFlow,
        localFilter,
    ) { cat, led, filter ->
        val targetLab = ColorScience.hexToLab(filter.targetHexInput)
            ?: ColorScience.srgbToLab(16, 42, 113)
        val rollsById = cat.rolls.associateBy { it.id }
        val matches = searchShadesByColor.rankMatches(
            targetLab = targetLab,
            carts = cat.carts,
            rollsById = rollsById,
            maxDeltaE = filter.maxDeltaE,
            fabricTypeIdFilter = filter.fabricFilterId,
            includeOutOfStock = filter.includeOutOfStock,
        )
        val insights = computeMillInsights.compute(
            carts = cat.carts,
            rolls = cat.rolls,
            fabricNamesById = cat.fabrics.associate { it.id to it.name },
            syncQueue = led.queue,
            lots = cat.lots,
            reservations = cat.reservations,
            cuts = led.cuts,
            favoritedIds = led.buyer.favoritedSwatchIds,
        )
        InventoryUiState(
            activeTab = filter.activeTab,
            currentRole = led.session.role,
            operatorName = led.session.lastOperatorName,
            carts = cat.carts,
            fabricTypes = cat.fabrics,
            rolls = cat.rolls,
            lots = cat.lots,
            activeReservations = cat.reservations,
            cutLogs = led.cuts,
            priceTiers = led.tiers,
            targetHexInput = filter.targetHexInput,
            maxDeltaE = filter.maxDeltaE,
            fabricFilterId = filter.fabricFilterId,
            includeOutOfStock = filter.includeOutOfStock,
            reverseMatches = matches,
            askQuery = filter.askQuery,
            askResult = filter.askResult,
            isNanoAvailable = filter.isNanoAvailable,
            insights = insights,
            statusMessage = filter.statusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState(),
    )

    fun selectTab(tab: MillHubTab) {
        localFilter.update { it.copy(activeTab = tab, statusMessage = null) }
    }

    fun updateTargetHex(hex: String) {
        localFilter.update { it.copy(targetHexInput = hex) }
    }

    fun updateMaxDeltaE(value: Float) {
        localFilter.update { it.copy(maxDeltaE = value.coerceIn(1f, 30f)) }
    }

    fun updateFabricFilter(fabricId: String?) {
        localFilter.update { it.copy(fabricFilterId = fabricId) }
    }

    fun toggleIncludeOutOfStock() {
        localFilter.update { it.copy(includeOutOfStock = !it.includeOutOfStock) }
    }

    fun updateAskQuery(query: String) {
        localFilter.update { it.copy(askQuery = query) }
    }

    fun submitAskQuery(overrideQuery: String? = null) {
        val q = overrideQuery ?: localFilter.value.askQuery
        if (q.isBlank()) return
        localFilter.update { it.copy(askQuery = q) }
        viewModelScope.launch {
            val result = askAzPallete(q)
            localFilter.update { it.copy(askResult = result) }
        }
    }

    fun createRoll(
        rollCode: String,
        fabricTypeId: String,
        shadeLabel: String,
        colorName: String,
        colorHex: String,
        warehouseShed: String,
        meters: Double,
        lotCode: String,
    ) {
        if (!RoleGate.canEditCarts(uiState.value.currentRole)) {
            localFilter.update { it.copy(statusMessage = "Read-only role cannot create rolls.") }
            return
        }
        val cleanHex = if (HexColorValidator.isValid(colorHex)) {
            HexColorValidator.normalizeToSixDigitHex(colorHex)
        } else {
            "#102A71"
        }
        viewModelScope.launch {
            createWarehouseRoll(
                WarehouseRollModel(
                    id = "",
                    rollCode = rollCode,
                    fabricTypeId = fabricTypeId,
                    shadeLabel = shadeLabel,
                    colorName = colorName,
                    colorHex = cleanHex,
                    warehouseShed = warehouseShed,
                    lengthMeters = meters,
                    availableMeters = meters,
                    lotCode = lotCode,
                ),
            ).onSuccess {
                localFilter.update { it.copy(statusMessage = "Added roll ${rollCode.uppercase()}.") }
            }.onFailure { err ->
                localFilter.update { it.copy(statusMessage = err.message ?: "Failed to create roll.") }
            }
        }
    }

    fun addDyeLot(rollId: String, lotCode: String, deltaE: Double, notes: String) {
        viewModelScope.launch {
            createDyeLot(
                DyeLotModel(
                    id = "",
                    rollId = rollId,
                    lotCode = lotCode,
                    dyeDate = System.currentTimeMillis(),
                    masterDeltaE = deltaE,
                    operatorName = uiState.value.operatorName,
                    notes = notes,
                ),
            ).onSuccess {
                localFilter.update { it.copy(statusMessage = "Logged dye lot $lotCode (ΔE $deltaE).") }
            }.onFailure { err ->
                localFilter.update { it.copy(statusMessage = err.message ?: "Failed to save dye lot.") }
            }
        }
    }

    fun reserveMeters(rollId: String, meters: Double, note: String) {
        if (!RoleGate.canReserveOrCut(uiState.value.currentRole)) {
            localFilter.update { it.copy(statusMessage = "Permission denied for current role.") }
            return
        }
        viewModelScope.launch {
            reserveRollMeters(
                rollId = rollId,
                swatchId = "",
                meters = meters,
                operatorName = uiState.value.operatorName,
                note = note,
            ).onSuccess {
                localFilter.update { it.copy(statusMessage = "Reserved ${meters}m successfully.") }
                lowStockAlertScheduler.checkNow()
            }.onFailure { err ->
                localFilter.update { it.copy(statusMessage = err.message ?: "Reservation failed.") }
            }
        }
    }

    fun releaseActiveReservation(reservationId: String) {
        viewModelScope.launch {
            releaseReservation(reservationId)
                .onSuccess {
                    localFilter.update { it.copy(statusMessage = "Reservation released & meters restored.") }
                }
                .onFailure { err ->
                    localFilter.update { it.copy(statusMessage = err.message ?: "Release failed.") }
                }
        }
    }

    fun recordCut(rollId: String, metersCut: Double, pieces: Int) {
        if (!RoleGate.canReserveOrCut(uiState.value.currentRole)) {
            localFilter.update { it.copy(statusMessage = "Permission denied for current role.") }
            return
        }
        viewModelScope.launch {
            logRollCut(
                rollId = rollId,
                swatchId = "",
                metersCut = metersCut,
                pieceCount = pieces,
                operatorName = uiState.value.operatorName,
            ).onSuccess {
                localFilter.update { it.copy(statusMessage = "Cut ${metersCut}m logged.") }
                lowStockAlertScheduler.checkNow()
            }.onFailure { err ->
                localFilter.update { it.copy(statusMessage = err.message ?: "Cut log failed.") }
            }
        }
    }

    fun scanQrCode(
        onCartFound: (String) -> Unit,
        onRollFound: (String) -> Unit,
    ) {
        viewModelScope.launch {
            barcodeScannerGateway.scanSingleCode()
                .onSuccess { target ->
                    when (target) {
                        is ScannedBarcodeTarget.CartDeepLink -> onCartFound(target.cartId)
                        is ScannedBarcodeTarget.RollCode -> {
                            localFilter.update {
                                it.copy(
                                    activeTab = MillHubTab.WAREHOUSE_ROLLS,
                                    statusMessage = "Scanned Roll: ${target.rollCode}",
                                )
                            }
                            onRollFound(target.rollCode)
                        }
                        is ScannedBarcodeTarget.RawText ->
                            localFilter.update { it.copy(statusMessage = "Scanned: ${target.value}") }
                        null -> Unit
                    }
                }
                .onFailure { err ->
                    localFilter.update {
                        it.copy(
                            statusMessage = "QR scanner unavailable (${err.message ?: "no GMS module"}). Use manual roll search.",
                        ),
                    }
                }
        }
    }

    fun clearStatusMessage() {
        localFilter.update { it.copy(statusMessage = null) }
    }
}

private data class CatalogBundle(
    val carts: List<ShadeCartModel>,
    val fabrics: List<FabricTypeModel>,
    val rolls: List<WarehouseRollModel>,
    val lots: List<DyeLotModel>,
    val reservations: List<ReservationModel>,
)

private data class LedgerBundle(
    val cuts: List<CutLogModel>,
    val tiers: List<PriceTierModel>,
    val queue: List<com.alazhartex.shadecart.domain.model.SyncQueueEntryModel>,
    val session: com.alazhartex.shadecart.domain.model.AppSessionPreferences,
    val buyer: com.alazhartex.shadecart.domain.model.BuyerSessionState,
)
