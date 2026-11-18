package com.alazhartex.shadecart.ui.screens.buyer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.domain.export.ShadeCartFormatters
import com.alazhartex.shadecart.domain.model.BuyerSessionState
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.usecase.ExitBuyerModeUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveBuyerSessionUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ToggleBuyerFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuyerShowcaseUiState(
    val session: BuyerSessionState = BuyerSessionState(),
    val carts: List<ShadeCartModel> = emptyList(),
    val selectedCartId: String? = null,
    val pinError: String? = null,
) {
    val activeCart: ShadeCartModel?
        get() = carts.firstOrNull { it.id == selectedCartId } ?: carts.firstOrNull()

    val favoritedSwatches: List<SwatchModel>
        get() = carts.flatMap { it.swatches }.filter { session.favoritedSwatchIds.contains(it.id) }
}

@HiltViewModel
class BuyerViewModel @Inject constructor(
    observeBuyerSession: ObserveBuyerSessionUseCase,
    observeShadeCarts: ObserveShadeCartsUseCase,
    private val toggleBuyerFavorite: ToggleBuyerFavoriteUseCase,
    private val exitBuyerMode: ExitBuyerModeUseCase,
) : ViewModel() {

    private val selectedCartId = MutableStateFlow<String?>(null)
    private val pinError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BuyerShowcaseUiState> = combine(
        observeBuyerSession(),
        observeShadeCarts(),
        selectedCartId,
        pinError,
    ) { session, carts, cartId, err ->
        BuyerShowcaseUiState(
            session = session,
            carts = carts,
            selectedCartId = cartId,
            pinError = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BuyerShowcaseUiState(),
    )

    fun selectCart(cartId: String) {
        selectedCartId.value = cartId
    }

    fun toggleFavorite(swatchId: String) {
        viewModelScope.launch { toggleBuyerFavorite(swatchId) }
    }

    fun buildInquiryText(): String {
        val state = uiState.value
        val byId = state.carts.associateBy { it.id }
        return ShadeCartFormatters.toBuyerInquiryMessage(
            companyName = state.session.companyName,
            favoritedSwatches = state.favoritedSwatches,
            cartsById = byId,
        )
    }

    fun tryExitWithPin(pin: String, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            val ok = exitBuyerMode(pin)
            if (ok) {
                pinError.value = null
                onUnlocked()
            } else {
                pinError.value = "Incorrect 4-digit PIN."
            }
        }
    }

    fun clearPinError() {
        pinError.value = null
    }
}
