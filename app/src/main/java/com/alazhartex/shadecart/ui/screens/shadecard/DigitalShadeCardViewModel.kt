package com.alazhartex.shadecart.ui.screens.shadecard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartWithDetails
import com.alazhartex.shadecart.data.local.SwatchWithRoll
import com.alazhartex.shadecart.data.repo.SaveResult
import com.alazhartex.shadecart.data.repo.ShadeCartDraft
import com.alazhartex.shadecart.data.repo.ShadeCartRepository
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DigitalShadeCardUiState(
    val carts: List<ShadeCartWithDetails> = emptyList(),
    val selectedCartId: String? = null,
    val activeCart: ShadeCartWithDetails? = null,
    val availableRolls: List<Roll> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DigitalShadeCardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ShadeCartRepository,
    val bitmapProcessor: BitmapProcessor,
) : ViewModel() {

    companion object {
        private const val KEY_CART_ID = "cartId"
    }

    private val selectedCartIdFlow = MutableStateFlow(savedStateHandle.get<String>(KEY_CART_ID))

    val uiState: StateFlow<DigitalShadeCardUiState> = combine(
        repository.observeCarts(),
        selectedCartIdFlow,
        repository.observeRolls(),
    ) { carts, requestedId, rolls ->
        val active = carts.firstOrNull { it.cart.id == requestedId } ?: carts.firstOrNull()
        val matchingRolls = if (active?.cart?.fabricTypeId != null) {
            rolls.filter { it.fabricTypeId == active.cart.fabricTypeId }
        } else {
            rolls
        }
        DigitalShadeCardUiState(
            carts = carts,
            selectedCartId = active?.cart?.id,
            activeCart = active,
            availableRolls = matchingRolls.ifEmpty { rolls },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DigitalShadeCardUiState(),
    )

    fun selectCart(cartId: String) {
        savedStateHandle[KEY_CART_ID] = cartId
        selectedCartIdFlow.value = cartId
    }

    fun openManualPaletteEditor(onNavigateToPalette: (String) -> Unit) {
        val current = uiState.value.activeCart
        if (current != null) {
            onNavigateToPalette(current.cart.id)
            return
        }
        viewModelScope.launch {
            val result = repository.createOrGetDraftCart(
                ShadeCartDraft(
                    fabricTypeId = "fab-silk-180",
                    fabricName = "Silk 180",
                    fabricCode = "SLK-180",
                    weightGsm = 180,
                    widthCm = 150,
                    swatches = emptyList(),
                ),
            )
            if (result is SaveResult.Success) {
                selectCart(result.cartId)
                onNavigateToPalette(result.cartId)
            }
        }
    }

    fun linkSwatchToRoll(target: SwatchWithRoll, rollId: String?) {
        val cart = uiState.value.activeCart ?: return
        viewModelScope.launch {
            val updatedSwatches = cart.swatches.map { entry ->
                val sw = entry.swatch
                SwatchDraft(
                    id = sw.id,
                    fabricTypeId = sw.fabricTypeId,
                    rollId = if (sw.id == target.swatch.id) rollId else sw.rollId,
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
            repository.updateSwatchesWithOptimisticLock(
                cartId = cart.cart.id,
                expectedCartVersion = cart.cart.version,
                swatches = updatedSwatches,
            )
        }
    }
}
