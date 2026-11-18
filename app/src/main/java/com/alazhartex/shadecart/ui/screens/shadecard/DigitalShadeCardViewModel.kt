package com.alazhartex.shadecart.ui.screens.shadecard

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.ShadeConsistencyReport
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.usecase.CreateOrGetShadeCartUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveAllLotsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveCalibrationStateUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveWarehouseRollsUseCase
import com.alazhartex.shadecart.domain.usecase.ShadeConsistencyUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateShadeCartSwatchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigitalShadeCardUiState(
    val carts: List<ShadeCartModel> = emptyList(),
    val selectedCartId: String? = null,
    val activeCart: ShadeCartModel? = null,
    val availableRolls: List<WarehouseRollModel> = emptyList(),
    val consistencyReport: ShadeConsistencyReport? = null,
    val isCalibrated: Boolean = false,
    val activeCalibrationName: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DigitalShadeCardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeShadeCarts: ObserveShadeCartsUseCase,
    observeWarehouseRolls: ObserveWarehouseRollsUseCase,
    observeAllLots: ObserveAllLotsUseCase,
    observeCalibrationState: ObserveCalibrationStateUseCase,
    private val shadeConsistencyUseCase: ShadeConsistencyUseCase,
    private val createOrGetShadeCart: CreateOrGetShadeCartUseCase,
    private val updateShadeCartSwatches: UpdateShadeCartSwatchesUseCase,
    private val bitmapProcessor: BitmapProcessor,
) : ViewModel() {

    companion object {
        private const val KEY_CART_ID = "cartId"
    }

    private val selectedCartIdFlow = MutableStateFlow(savedStateHandle.get<String>(KEY_CART_ID))

    val uiState: StateFlow<DigitalShadeCardUiState> = combine(
        observeShadeCarts(),
        selectedCartIdFlow,
        observeWarehouseRolls(null),
        observeAllLots(),
        observeCalibrationState(),
    ) { carts, requestedId, rolls, lots, calState ->
        val active = carts.firstOrNull { it.id == requestedId } ?: carts.firstOrNull()
        val matchingRolls = if (active?.fabricTypeId != null) {
            rolls.filter { it.fabricTypeId == active.fabricTypeId }
        } else {
            rolls
        }
        val report = active?.let {
            shadeConsistencyUseCase.evaluate(cart = it, rolls = rolls, lots = lots)
        }
        val activeCal = active?.calibrationProfileId?.let { id ->
            calState.profiles.firstOrNull { it.id == id }
        } ?: calState.activeProfile
        DigitalShadeCardUiState(
            carts = carts,
            selectedCartId = active?.id,
            activeCart = active,
            availableRolls = matchingRolls.ifEmpty { rolls },
            consistencyReport = report,
            isCalibrated = activeCal != null,
            activeCalibrationName = activeCal?.name,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DigitalShadeCardUiState(),
    )

    suspend fun loadPreviewBitmap(imagePath: String): Bitmap? =
        bitmapProcessor.loadRgb565Preview(imagePath)

    fun selectCart(cartId: String) {
        savedStateHandle[KEY_CART_ID] = cartId
        selectedCartIdFlow.value = cartId
    }

    fun openManualPaletteEditor(onNavigateToPalette: (String) -> Unit) {
        val current = uiState.value.activeCart
        if (current != null) {
            onNavigateToPalette(current.id)
            return
        }
        viewModelScope.launch {
            val result = createOrGetShadeCart(
                ShadeCartCreateInput(
                    fabricTypeId = "fab-silk-180",
                    fabricName = "Silk 180",
                    fabricCode = "SLK-180",
                    weightGsm = 180,
                    widthCm = 150,
                    swatches = emptyList(),
                ),
            )
            if (result is ShadeCartSaveOutcome.Saved) {
                selectCart(result.cartId)
                onNavigateToPalette(result.cartId)
            }
        }
    }

    fun linkSwatchToRoll(target: SwatchModel, rollId: String?) {
        val cart = uiState.value.activeCart ?: return
        viewModelScope.launch {
            val updatedSwatches = cart.swatches.map { sw ->
                SwatchInput(
                    id = sw.id,
                    fabricTypeId = sw.fabricTypeId,
                    rollId = if (sw.id == target.id) rollId else sw.rollId,
                    swatchNumber = sw.swatchNumber,
                    colorHex = sw.colorHex,
                    colorName = sw.colorName,
                    positionOrder = sw.positionOrder,
                    bboxLeft = sw.bboxLeft,
                    bboxTop = sw.bboxTop,
                    bboxRight = sw.bboxRight,
                    bboxBottom = sw.bboxBottom,
                    isOverridden = sw.isOverridden,
                    labL = sw.labL,
                    labA = sw.labA,
                    labB = sw.labB,
                    weaveTag = sw.weaveTag,
                    sheenScore = sw.sheenScore,
                    pricePerMeter = sw.pricePerMeter,
                    currencyCode = sw.currencyCode,
                    version = sw.version,
                )
            }
            updateShadeCartSwatches(
                cartId = cart.id,
                expectedCartVersion = cart.version,
                swatches = updatedSwatches,
            )
        }
    }
}
