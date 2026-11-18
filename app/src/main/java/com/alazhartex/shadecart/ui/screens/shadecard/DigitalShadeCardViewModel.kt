package com.alazhartex.shadecart.ui.screens.shadecard

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.usecase.CreateOrGetShadeCartUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveWarehouseRollsUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateShadeCartSwatchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable presentation state for the Digital Shade Card showcase screen, built exclusively from
 * pure Kotlin domain models (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.3 & 10.1).
 */
data class DigitalShadeCardUiState(
    val carts: List<ShadeCartModel> = emptyList(),
    val selectedCartId: String? = null,
    val activeCart: ShadeCartModel? = null,
    val availableRolls: List<WarehouseRollModel> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * `@HiltViewModel` backing [DigitalShadeCardRoute], consuming UseCases only and exposing a
 * single reactive `StateFlow` via `stateIn(WhileSubscribed)` (Pyre, Chapter 1.2, 6.3, & 10.1).
 */
@HiltViewModel
class DigitalShadeCardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeShadeCarts: ObserveShadeCartsUseCase,
    observeWarehouseRolls: ObserveWarehouseRollsUseCase,
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
    ) { carts, requestedId, rolls ->
        val active = carts.firstOrNull { it.id == requestedId } ?: carts.firstOrNull()
        val matchingRolls = if (active?.fabricTypeId != null) {
            rolls.filter { it.fabricTypeId == active.fabricTypeId }
        } else {
            rolls
        }
        DigitalShadeCardUiState(
            carts = carts,
            selectedCartId = active?.id,
            activeCart = active,
            availableRolls = matchingRolls.ifEmpty { rolls },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DigitalShadeCardUiState(),
    )

    /** Decodes a memory-bounded `RGB_565` thumbnail bitmap off the main thread. */
    suspend fun loadPreviewBitmap(imagePath: String): Bitmap? =
        bitmapProcessor.loadRgb565Preview(imagePath)

    /** Persists the active cart selection in [SavedStateHandle] across configuration changes. */
    fun selectCart(cartId: String) {
        savedStateHandle[KEY_CART_ID] = cartId
        selectedCartIdFlow.value = cartId
    }

    /** Opens the manual Palette Editor on the active cart or provisions a Silk 180 draft first. */
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

    /** Links or unlinks [target] swatch to a warehouse [WarehouseRollModel] with optimistic locking. */
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
