package com.alazhartex.shadecart.domain.usecase

import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncBatchSummary
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import com.alazhartex.shadecart.domain.repository.ThemePreferencesContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for domain Clean Architecture UseCases verifying delegation to repository and
 * preference contracts (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1 & 11.3).
 */
class ShadeCartUseCasesTest {

    @Test
    fun `shade cart lifecycle UseCases create, update, sync, and resolve cleanly`() = runTest {
        val repo = FakeShadeCartRepository()
        val createUseCase = CreateOrGetShadeCartUseCase(repo)
        val observeCarts = ObserveShadeCartsUseCase(repo)
        val updateSwatches = UpdateShadeCartSwatchesUseCase(repo)
        val resolveConflict = ResolveSyncConflictUseCase(repo)
        val triggerSync = TriggerSyncUseCase(repo)

        val created = createUseCase(
            ShadeCartCreateInput(
                id = "cart-u1",
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                weightGsm = 180,
                widthCm = 150,
            ),
        )
        assertTrue(created is ShadeCartSaveOutcome.Saved)
        assertEquals(1, observeCarts().first().size)

        val updated = updateSwatches(
            cartId = "cart-u1",
            expectedCartVersion = 1L,
            swatches = listOf(
                SwatchInput(
                    id = "sw-1",
                    swatchNumber = "1",
                    colorHex = "#102A71",
                    colorName = "Azhar Navy",
                ),
            ),
        )
        assertTrue(updated is ShadeCartSaveOutcome.Saved)
        assertEquals("#102A71", observeCarts().first().first().swatches.first().colorHex)

        val syncSummary = triggerSync()
        assertEquals(1, syncSummary.synced)

        val resolved = resolveConflict("cart-u1", ConflictDecision.KEEP_LOCAL)
        assertTrue(resolved is ShadeCartSaveOutcome.Saved)
    }

    @Test
    fun `theme preferences UseCases persist theme mode, dynamic color, and accent palette`() = runTest {
        val prefs = FakeThemePreferences()
        val observePrefs = ObserveThemePreferencesUseCase(prefs)
        val setMode = UpdateThemeModeUseCase(prefs)
        val setDynamic = UpdateDynamicColorUseCase(prefs)
        val setAccent = UpdateAccentPaletteUseCase(prefs)

        setMode(ThemeModeSetting.DARK)
        setDynamic(false)
        setAccent(AccentPaletteSetting.AZHAR_EMERALD_SAND)

        val current = observePrefs().first()
        assertEquals(ThemeModeSetting.DARK, current.themeMode)
        assertFalse(current.useDynamicColor)
        assertEquals(AccentPaletteSetting.AZHAR_EMERALD_SAND, current.accentPalette)
    }
}

private class FakeThemePreferences : ThemePreferencesContract {
    private val state = MutableStateFlow(ThemeSettings())
    override val settings: Flow<ThemeSettings> = state

    override suspend fun setThemeMode(mode: ThemeModeSetting) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun setAccentPalette(palette: AccentPaletteSetting) {
        state.value = state.value.copy(accentPalette = palette)
    }
}

internal class FakeShadeCartRepository : ShadeCartRepositoryContract {
    private val carts = MutableStateFlow<List<ShadeCartModel>>(emptyList())
    private val rolls = MutableStateFlow<List<WarehouseRollModel>>(emptyList())
    private val fabrics = MutableStateFlow<List<FabricTypeModel>>(emptyList())
    private val queue = MutableStateFlow<List<SyncQueueEntryModel>>(emptyList())
    private val conflicts = MutableStateFlow<List<SyncConflictModel>>(emptyList())

    fun seedRolls(items: List<WarehouseRollModel>) {
        rolls.value = items
    }

    override fun observeCarts(): Flow<List<ShadeCartModel>> = carts

    override fun observeCart(cartId: String): Flow<ShadeCartModel?> =
        carts.map { list -> list.firstOrNull { it.id == cartId } }

    override fun observeFabricTypes(): Flow<List<FabricTypeModel>> = fabrics

    override fun observeRolls(fabricTypeId: String?): Flow<List<WarehouseRollModel>> =
        if (fabricTypeId == null) rolls else rolls.map { list -> list.filter { it.fabricTypeId == fabricTypeId } }

    override fun observeSyncQueue(): Flow<List<SyncQueueEntryModel>> = queue

    override fun observeConflicts(): Flow<List<SyncConflictModel>> = conflicts

    override suspend fun createOrGetDraftCart(draft: ShadeCartCreateInput): ShadeCartSaveOutcome {
        val model = ShadeCartModel(
            id = draft.id,
            fabricTypeId = draft.fabricTypeId,
            fabricName = draft.fabricName,
            fabricCode = draft.fabricCode,
            weightGsm = draft.weightGsm,
            widthCm = draft.widthCm,
            originalImagePath = draft.originalImagePath,
            editedImagePath = draft.editedImagePath,
            editStackJson = draft.editStackJson,
            headerOcrRaw = draft.headerOcrRaw,
            version = 1L,
            swatches = draft.swatches.mapIndexed { idx, sw ->
                SwatchModel(
                    id = sw.id,
                    shadeCartId = draft.id,
                    fabricTypeId = sw.fabricTypeId.ifBlank { draft.fabricTypeId ?: "" },
                    rollId = sw.rollId,
                    swatchNumber = sw.swatchNumber,
                    colorHex = sw.colorHex,
                    colorName = sw.colorName,
                    positionOrder = idx,
                )
            },
        )
        carts.value = carts.value.filterNot { it.id == model.id } + model
        return ShadeCartSaveOutcome.Saved(cartId = model.id, newVersion = 1L)
    }

    override suspend fun saveCartWithOptimisticLock(
        cartId: String,
        expectedVersion: Long,
        fabricTypeId: String?,
        fabricName: String,
        fabricCode: String,
        weightGsm: Int,
        widthCm: Int,
        originalImagePath: String?,
        editedImagePath: String?,
        editStackJson: String,
        headerOcrRaw: String,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome {
        val current = carts.value.firstOrNull { it.id == cartId }
            ?: return ShadeCartSaveOutcome.NotFound(cartId)
        val nextVersion = current.version + 1L
        val updated = current.copy(
            fabricTypeId = fabricTypeId,
            fabricName = fabricName,
            fabricCode = fabricCode,
            weightGsm = weightGsm,
            widthCm = widthCm,
            originalImagePath = originalImagePath,
            editedImagePath = editedImagePath,
            editStackJson = editStackJson,
            headerOcrRaw = headerOcrRaw,
            version = nextVersion,
            swatches = swatches.mapIndexed { idx, sw ->
                SwatchModel(
                    id = sw.id,
                    shadeCartId = cartId,
                    fabricTypeId = sw.fabricTypeId,
                    rollId = sw.rollId,
                    swatchNumber = sw.swatchNumber,
                    colorHex = sw.colorHex,
                    colorName = sw.colorName,
                    positionOrder = idx,
                )
            },
        )
        carts.value = carts.value.map { if (it.id == cartId) updated else it }
        return ShadeCartSaveOutcome.Saved(cartId = cartId, newVersion = nextVersion)
    }

    override suspend fun updateSwatchesWithOptimisticLock(
        cartId: String,
        expectedCartVersion: Long,
        swatches: List<SwatchInput>,
    ): ShadeCartSaveOutcome {
        val current = carts.value.firstOrNull { it.id == cartId }
            ?: return ShadeCartSaveOutcome.NotFound(cartId)
        return saveCartWithOptimisticLock(
            cartId = cartId,
            expectedVersion = expectedCartVersion,
            fabricTypeId = current.fabricTypeId,
            fabricName = current.fabricName,
            fabricCode = current.fabricCode,
            weightGsm = current.weightGsm,
            widthCm = current.widthCm,
            originalImagePath = current.originalImagePath,
            editedImagePath = current.editedImagePath,
            editStackJson = current.editStackJson,
            headerOcrRaw = current.headerOcrRaw,
            swatches = swatches,
        )
    }

    override suspend fun resolveConflict(cartId: String, decision: ConflictDecision): ShadeCartSaveOutcome =
        ShadeCartSaveOutcome.Saved(cartId = cartId, newVersion = 2L)

    override suspend fun syncPendingQueue(): SyncBatchSummary =
        SyncBatchSummary(processed = 1, synced = 1, conflicts = 0, failed = 0)

    override suspend fun deleteCart(cartId: String) {
        carts.value = carts.value.filterNot { it.id == cartId }
    }
}
