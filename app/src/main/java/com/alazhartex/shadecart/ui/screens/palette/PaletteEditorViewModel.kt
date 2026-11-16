package com.alazhartex.shadecart.ui.screens.palette

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.repo.SaveResult
import com.alazhartex.shadecart.data.repo.ShadeCartDraft
import com.alazhartex.shadecart.data.repo.ShadeCartRepository
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.alazhartex.shadecart.domain.imaging.PinkedSwatchSampler
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PaletteEditorUiState(
    val cartId: String? = null,
    val cartTitle: String = "Manual Palette Editor",
    val expectedVersion: Long = 1L,
    val fabricTypeId: String = "fab-silk-180",
    val swatches: List<SwatchDraft> = emptyList(),
    val availableRolls: List<Roll> = emptyList(),
    // Active swatch color editor state (used for both Add and Override)
    val editingSwatchId: String? = null,
    val swatchNumberInput: String = "",
    val colorNameInput: String = "",
    val hexInput: String = "#102A71",
    val hexValidationError: String? = null,
    val livePreviewArgb: Int? = 0xFF102A71.toInt(),
    val selectedRollId: String? = null,
    val isHexValid: Boolean = true,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
)

@HiltViewModel
class PaletteEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ShadeCartRepository,
) : ViewModel() {

    companion object {
        private const val KEY_CART_ID = "cartId"
        private const val KEY_HEX_INPUT = "palette_hex_input"
        private const val KEY_SWATCH_NUM = "palette_swatch_num"
    }

    private val _uiState = MutableStateFlow(PaletteEditorUiState())
    val uiState: StateFlow<PaletteEditorUiState> = _uiState.asStateFlow()

    init {
        loadInitialCart()
    }

    private fun loadInitialCart() {
        viewModelScope.launch {
            val rolls = repository.observeRolls().first()
            val requestedId = savedStateHandle.get<String>(KEY_CART_ID)
            val allCarts = repository.observeCarts().first()
            var targetCart = allCarts.firstOrNull { it.cart.id == requestedId } ?: allCarts.firstOrNull()

            if (targetCart == null) {
                val created = repository.createOrGetDraftCart(
                    ShadeCartDraft(
                        fabricTypeId = "fab-silk-180",
                        fabricName = "Silk 180",
                        fabricCode = "SLK-180",
                        weightGsm = 180,
                        widthCm = 150,
                        swatches = emptyList(),
                    ),
                )
                if (created is SaveResult.Success) {
                    savedStateHandle[KEY_CART_ID] = created.cartId
                    targetCart = repository.observeCart(created.cartId).first()
                }
            }

            if (targetCart != null) {
                val swatchDrafts = targetCart.swatches.map { entry ->
                    val sw = entry.swatch
                    SwatchDraft(
                        id = sw.id,
                        fabricTypeId = sw.fabricTypeId,
                        rollId = sw.rollId,
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

                val savedHex = savedStateHandle.get<String>(KEY_HEX_INPUT) ?: "#102A71"
                val savedNum = savedStateHandle.get<String>(KEY_SWATCH_NUM)
                    ?: nextSwatchNumber(swatchDrafts)

                _uiState.update {
                    it.copy(
                        cartId = targetCart.cart.id,
                        cartTitle = "${targetCart.cart.fabricName} (${targetCart.cart.fabricCode})",
                        expectedVersion = targetCart.cart.version,
                        fabricTypeId = targetCart.cart.fabricTypeId ?: "fab-silk-180",
                        swatches = swatchDrafts,
                        availableRolls = rolls,
                        swatchNumberInput = savedNum,
                    )
                }
                onHexInputChanged(savedHex)
            }
        }
    }

    fun startEditingSwatch(swatch: SwatchDraft) {
        _uiState.update {
            it.copy(
                editingSwatchId = swatch.id,
                swatchNumberInput = swatch.swatchNumber,
                colorNameInput = swatch.colorName,
                selectedRollId = swatch.rollId,
                statusMessage = null,
            )
        }
        onHexInputChanged(swatch.colorHex)
    }

    fun startNewSwatch() {
        val nextNum = nextSwatchNumber(_uiState.value.swatches)
        _uiState.update {
            it.copy(
                editingSwatchId = null,
                swatchNumberInput = nextNum,
                colorNameInput = "",
                selectedRollId = null,
                statusMessage = null,
            )
        }
        onHexInputChanged("#102A71")
    }

    fun onSwatchNumberChanged(value: String) {
        savedStateHandle[KEY_SWATCH_NUM] = value
        _uiState.update { it.copy(swatchNumberInput = value) }
    }

    fun onColorNameChanged(value: String) {
        _uiState.update { it.copy(colorNameInput = value) }
    }

    fun onRollSelected(rollId: String?) {
        _uiState.update { it.copy(selectedRollId = rollId) }
    }

    fun onHexInputChanged(value: String) {
        savedStateHandle[KEY_HEX_INPUT] = value
        when (val validation = HexColorValidator.validate(value)) {
            is HexColorValidator.ValidationResult.Valid -> {
                _uiState.update {
                    it.copy(
                        hexInput = value,
                        isHexValid = true,
                        hexValidationError = null,
                        livePreviewArgb = validation.argb,
                    )
                }
            }

            is HexColorValidator.ValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        hexInput = value,
                        isHexValid = false,
                        hexValidationError = validation.message,
                        livePreviewArgb = null,
                    )
                }
            }
        }
    }

    fun applyColorEditor(): Boolean {
        val state = _uiState.value
        val validation = HexColorValidator.validate(state.hexInput)
        if (validation !is HexColorValidator.ValidationResult.Valid) {
            _uiState.update {
                it.copy(
                    isHexValid = false,
                    hexValidationError = (validation as HexColorValidator.ValidationResult.Invalid).message,
                )
            }
            return false
        }
        val cleanNum = state.swatchNumberInput.trim()
        if (cleanNum.isEmpty()) return false

        val normalizedHex = validation.normalizedHex
        val resolvedName = state.colorNameInput.trim().ifBlank {
            PinkedSwatchSampler.describeTextileColor(validation.argb)
        }

        val currentList = state.swatches.toMutableList()
        val editId = state.editingSwatchId
        if (editId != null) {
            val idx = currentList.indexOfFirst { it.id == editId }
            if (idx >= 0) {
                currentList[idx] = currentList[idx].copy(
                    swatchNumber = cleanNum,
                    colorHex = normalizedHex,
                    colorName = resolvedName,
                    rollId = state.selectedRollId,
                    isOverridden = true,
                )
            }
        } else {
            currentList.add(
                SwatchDraft(
                    id = UUID.randomUUID().toString(),
                    fabricTypeId = state.fabricTypeId,
                    rollId = state.selectedRollId,
                    swatchNumber = cleanNum,
                    colorHex = normalizedHex,
                    colorName = resolvedName,
                    positionOrder = currentList.size,
                    isOverridden = true,
                ),
            )
        }

        val reindexed = currentList.mapIndexed { idx, sw -> sw.copy(positionOrder = idx) }
        val nextNum = nextSwatchNumber(reindexed)
        _uiState.update {
            it.copy(
                swatches = reindexed,
                editingSwatchId = null,
                swatchNumberInput = nextNum,
                colorNameInput = "",
                selectedRollId = null,
                statusMessage = "Applied #$cleanNum ($normalizedHex). Tap Save Palette to persist.",
            )
        }
        return true
    }

    fun deleteSwatch(swatchId: String) {
        val updated = _uiState.value.swatches
            .filterNot { it.id == swatchId }
            .mapIndexed { idx, sw -> sw.copy(positionOrder = idx) }
        _uiState.update {
            it.copy(
                swatches = updated,
                editingSwatchId = if (it.editingSwatchId == swatchId) null else it.editingSwatchId,
            )
        }
    }

    fun moveSwatch(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.swatches.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        val reindexed = list.mapIndexed { idx, sw -> sw.copy(positionOrder = idx) }
        _uiState.update { it.copy(swatches = reindexed) }
    }

    fun savePalette(onSaved: (String) -> Unit) {
        val state = _uiState.value
        val cartId = state.cartId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (
                val res = repository.updateSwatchesWithOptimisticLock(
                    cartId = cartId,
                    expectedCartVersion = state.expectedVersion,
                    swatches = state.swatches,
                )
            ) {
                is SaveResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            expectedVersion = res.newVersion,
                            statusMessage = "Saved v${res.newVersion} and queued for sync.",
                        )
                    }
                    onSaved(cartId)
                }

                is SaveResult.Conflict -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = "Version conflict detected — resolve using the conflict banner.",
                        )
                    }
                    onSaved(cartId)
                }

                is SaveResult.NotFound -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = "Cart not found in database.",
                        )
                    }
                }
            }
        }
    }

    private fun nextSwatchNumber(swatches: List<SwatchDraft>): String {
        val maxExisting = swatches.mapNotNull { it.swatchNumber.toIntOrNull() }.maxOrNull() ?: 0
        return (maxExisting + 1).toString()
    }
}
