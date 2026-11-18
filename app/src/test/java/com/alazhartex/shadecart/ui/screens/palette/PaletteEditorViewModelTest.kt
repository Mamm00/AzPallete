package com.alazhartex.shadecart.ui.screens.palette

import androidx.lifecycle.SavedStateHandle
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.usecase.CreateOrGetShadeCartUseCase
import com.alazhartex.shadecart.domain.usecase.FakeShadeCartRepository
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartByIdUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartsUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveWarehouseRollsUseCase
import com.alazhartex.shadecart.domain.usecase.UpdateShadeCartSwatchesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PaletteEditorViewModel] using [StandardTestDispatcher] and `Dispatchers.setMain`
 * to verify `StateFlow` HEX validation, swatch list mutations, and optimistic-locked persistence
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 11.3 — Testing Coroutines & ViewModels).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaletteEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeShadeCartRepository
    private lateinit var viewModel: PaletteEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeShadeCartRepository().apply {
            seedRolls(
                listOf(
                    WarehouseRollModel(
                        id = "roll-slk-5",
                        rollCode = "RL-SLK-005",
                        fabricTypeId = "fab-silk-180",
                        shadeLabel = "5",
                        colorName = "Azhar Navy",
                        colorHex = "#102A71",
                        warehouseShed = "wh-alwa",
                        lengthMeters = 480.0,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(cartId: String? = "cart-test"): PaletteEditorViewModel =
        PaletteEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("cartId" to cartId)),
            observeShadeCarts = ObserveShadeCartsUseCase(repository),
            observeShadeCartById = ObserveShadeCartByIdUseCase(repository),
            observeWarehouseRolls = ObserveWarehouseRollsUseCase(repository),
            createOrGetShadeCart = CreateOrGetShadeCartUseCase(repository),
            updateShadeCartSwatches = UpdateShadeCartSwatchesUseCase(repository),
        )

    @Test
    fun `invalid HEX blocks apply and exposes inline validation error`() = runTest(testDispatcher) {
        repository.createOrGetDraftCart(
            ShadeCartCreateInput(
                id = "cart-test",
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
            ),
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onHexInputChanged("#ZZZZZZ")
        val state = viewModel.uiState.value

        assertFalse(state.isHexValid)
        assertNotNull(state.hexValidationError)
        assertNull(state.livePreviewArgb)
        assertFalse(viewModel.applyColorEditor())
    }

    @Test
    fun `valid HEX appends swatch and savePalette persists next version`() = runTest(testDispatcher) {
        repository.createOrGetDraftCart(
            ShadeCartCreateInput(
                id = "cart-test",
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
            ),
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSwatchNumberChanged("5")
        viewModel.onHexInputChanged("#102a71")
        assertTrue(viewModel.applyColorEditor())

        assertEquals(1, viewModel.uiState.value.swatches.size)
        assertEquals("#102A71", viewModel.uiState.value.swatches.first().colorHex)

        var savedCartId: String? = null
        viewModel.savePalette { savedCartId = it }
        advanceUntilIdle()

        assertEquals("cart-test", savedCartId)
        assertEquals(2L, viewModel.uiState.value.expectedVersion)
    }
}
