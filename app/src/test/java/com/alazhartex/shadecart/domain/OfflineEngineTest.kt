package com.alazhartex.shadecart.domain

import com.alazhartex.shadecart.data.backup.BackupCartDto
import com.alazhartex.shadecart.data.backup.BackupDatabasePayload
import com.alazhartex.shadecart.data.backup.BackupRollDto
import com.alazhartex.shadecart.data.backup.BackupSwatchDto
import com.alazhartex.shadecart.data.backup.PureBackupCrypto
import com.alazhartex.shadecart.domain.export.LookbookHtmlExporter
import com.alazhartex.shadecart.domain.export.ShadeCartFormatters
import com.alazhartex.shadecart.domain.imaging.ColorCalibrator
import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.imaging.PureImageMath
import com.alazhartex.shadecart.domain.model.DyeLotModel
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ScannedBarcodeTarget
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.usecase.ComputeMillInsightsUseCase
import com.alazhartex.shadecart.domain.usecase.FakeShadeCartRepository
import com.alazhartex.shadecart.domain.usecase.OfflineTemplateNaturalLanguageParser
import com.alazhartex.shadecart.domain.usecase.SearchShadesByColorUseCase
import com.alazhartex.shadecart.domain.usecase.ShadeConsistencyUseCase
import com.alazhartex.shadecart.domain.vision.AzPalleteDeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Pure JVM tests exercising AzPallete v2/v3 offline mathematical, color-science, cryptography,
 * export, deep-link, NL parser, and analytics kernels without Android framework dependencies.
 */
class OfflineEngineTest {

    @Test
    fun `CIEDE2000 returns zero for identical colors and differentiates distinct textile hues`() {
        val navyLab = ColorScience.hexToLab("#102A71")
        val nearNavyLab = ColorScience.hexToLab("#122D76")
        val crimsonLab = ColorScience.hexToLab("#CD1820")

        assertNotNull(navyLab)
        assertNotNull(nearNavyLab)
        assertNotNull(crimsonLab)

        val selfDelta = ColorScience.ciede2000(navyLab!!, navyLab)
        val nearDelta = ColorScience.ciede2000(navyLab, nearNavyLab!!)
        val farDelta = ColorScience.ciede2000(navyLab, crimsonLab!!)

        assertEquals(0.0f, selfDelta, 0.001f)
        assertTrue("Expected near shade ΔE < 2.0, got $nearDelta", nearDelta < 2.0f)
        assertTrue("Expected opposite shade ΔE > 40.0, got $farDelta", farDelta > 40.0f)
    }

    @Test
    fun `ShadeLock white calibration normalizes warm cast toward neutral gray and fits 3x3 matrix`() {
        val warmProfile = ColorCalibrator.calibrateFromWhiteSample(
            profileId = "cal-warm",
            name = "Warm Showroom",
            measuredWhiteRgb = Triple(246, 220, 180),
            ambientLux = 420f,
        )
        assertTrue(warmProfile.gainB > warmProfile.gainR)

        val warmGray = (0xFF shl 24) or (180 shl 16) or (161 shl 8) or 132
        val corrected = ColorCalibrator.applyCalibrationToArgb(warmGray, warmProfile)
        val outR = (corrected ushr 16) and 0xFF
        val outB = corrected and 0xFF
        assertTrue("Expected blue channel boosted closer to red ($outR vs $outB)", kotlin.math.abs(outR - outB) <= 5)

        val measured = listOf(
            floatArrayOf(200f, 10f, 10f),
            floatArrayOf(10f, 200f, 10f),
            floatArrayOf(10f, 10f, 200f),
            floatArrayOf(180f, 180f, 180f),
        )
        val reference = listOf(
            floatArrayOf(220f, 12f, 12f),
            floatArrayOf(12f, 220f, 12f),
            floatArrayOf(12f, 12f, 220f),
            floatArrayOf(198f, 198f, 198f),
        )
        val matrix = ColorCalibrator.solve3x3ColorMatrix(measured, reference)
        assertEquals(9, matrix.size)
        assertTrue(matrix[0] > 1.0f)
    }

    @Test
    fun `PureImageMath median stack, flat-field lighting, texture, and focus score behave deterministically`() {
        val pClean = (0xFF shl 24) or (16 shl 16) or (42 shl 8) or 113
        val pGlare = (0xFF shl 24) or (250 shl 16) or (250 shl 8) or 250
        val stacked = PureImageMath.medianStackThreeFrames(
            intArrayOf(pClean, pClean),
            intArrayOf(pGlare, pClean),
            intArrayOf(pClean, pClean),
        )
        assertEquals(pClean, stacked[0])

        val flatInput = IntArray(64 * 64) { idx ->
            val x = idx % 64
            val shade = if (x < 32) 110 else 210
            (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        val flatOut = PureImageMath.flattenIlluminationPixels(flatInput, 64, 64)
        assertEquals(flatInput.size, flatOut.size)

        val satinPixels = IntArray(400) { (0xFF shl 24) or (40 shl 16) or (60 shl 8) or 120 }
        val satinAnalysis = PureImageMath.analyzeWeaveTexture(satinPixels)
        assertEquals(PureImageMath.WeaveSuggestion.SATIN_SILK, satinAnalysis.suggestion)

        val sharpGrid = IntArray(16 * 16) { idx -> if ((idx + idx / 16) % 2 == 0) 240 else 20 }
        val blurryGrid = IntArray(16 * 16) { 128 }
        assertTrue(
            PureImageMath.computeLaplacianFocusScore(sharpGrid, 16, 16) >
                PureImageMath.computeLaplacianFocusScore(blurryGrid, 16, 16) + 100.0,
        )
    }

    @Test
    fun `multi-format exporters produce valid GPL, Procreate ZIP, CSV, bidi-safe WhatsApp, and offline HTML`() {
        val roll = WarehouseRollModel(
            id = "roll-1",
            rollCode = "RL-SLK-005",
            fabricTypeId = "fab-silk-180",
            shadeLabel = "5",
            colorName = "Azhar Navy",
            colorHex = "#102A71",
            warehouseShed = "wh-alwa",
            lengthMeters = 480.0,
            availableMeters = 320.0,
            lotCode = "LOT-2026-05A",
        )
        val cart = ShadeCartModel(
            id = "cart-demo-1",
            fabricTypeId = "fab-silk-180",
            fabricName = "Silk 180",
            fabricCode = "SLK-180",
            weightGsm = 180,
            widthCm = 150,
            originalImagePath = null,
            editedImagePath = null,
            editStackJson = "{}",
            swatches = listOf(
                SwatchModel(
                    id = "sw-5",
                    shadeCartId = "cart-demo-1",
                    fabricTypeId = "fab-silk-180",
                    rollId = "roll-1",
                    linkedRoll = roll,
                    swatchNumber = "5",
                    colorHex = "#102A71",
                    colorName = "Azhar <Navy>",
                    positionOrder = 0,
                    labL = 19.8,
                    labA = 12.1,
                    labB = -41.5,
                    pricePerMeter = 125.0,
                ),
            ),
        )

        val gpl = ShadeCartFormatters.toGimpPaletteText(cart)
        assertTrue(gpl.startsWith("GIMP Palette"))
        assertTrue(gpl.contains("16  42 113") || gpl.contains(" 16  42 113"))

        val swatchesBytes = ShadeCartFormatters.toProcreateSwatchesZipBytes(cart)
        val zipNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(swatchesBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipNames.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue(zipNames.contains("Swatches.json"))

        val csv = ShadeCartFormatters.toCsvText(cart)
        assertTrue(csv.contains("shade_number,color_name,hex,lab_l,lab_a,lab_b"))
        assertTrue(csv.contains("RL-SLK-005"))

        val whatsapp = ShadeCartFormatters.toWhatsAppShareCaption(cart)
        assertTrue(whatsapp.contains("\u2066#5 #102A71\u2069"))
        assertTrue(whatsapp.contains("320 m"))

        val html = LookbookHtmlExporter.generateHtml(listOf(cart), calibrated = true)
        assertTrue(html.contains("Azhar &lt;Navy&gt;"))
        assertTrue(html.contains("data:image/svg+xml;base64,"))
    }

    @Test
    fun `deep link parser handles cart URIs, roll URIs, RL barcodes, and fallback raw text`() {
        assertEquals(
            ScannedBarcodeTarget.CartDeepLink("cart-abc-123"),
            AzPalleteDeepLinkParser.parseUri("azpallete://cart/cart-abc-123?src=qr"),
        )
        assertEquals(
            ScannedBarcodeTarget.RollCode("RL-SLK-005"),
            AzPalleteDeepLinkParser.parseUri("azpallete://roll/RL-SLK-005"),
        )
        assertEquals(
            ScannedBarcodeTarget.RollCode("RL-WRS-012"),
            AzPalleteDeepLinkParser.parseUri("RL-WRS-012"),
        )
        assertEquals(
            ScannedBarcodeTarget.RawText("CUSTOM-42"),
            AzPalleteDeepLinkParser.parseUri("CUSTOM-42"),
        )
    }

    @Test
    fun `PureBackupCrypto AES-GCM encrypts, verifies SHA-256 checksum, and rejects bad passphrases`() {
        val payload = BackupDatabasePayload(
            fabrics = emptyList(),
            rolls = listOf(
                BackupRollDto(
                    id = "r1",
                    rollCode = "RL-SLK-005",
                    fabricTypeId = "fab-silk-180",
                    shadeLabel = "5",
                    colorName = "Azhar Navy",
                    colorHex = "#102A71",
                    warehouseShed = "wh-alwa",
                    lengthMeters = 480.0,
                    availableMeters = 410.0,
                    lotCode = "LOT-2026-05A",
                    lowStockThresholdMeters = 50.0,
                    qrPayload = "azpallete://roll/RL-SLK-005",
                    status = "IN_STOCK",
                    version = 1L,
                    updatedAt = 1_700_000_000_000L,
                ),
            ),
            carts = listOf(
                BackupCartDto(
                    id = "c1",
                    fabricTypeId = "fab-silk-180",
                    fabricName = "Silk 180",
                    fabricCode = "SLK-180",
                    weightGsm = 180,
                    widthCm = 150,
                    originalImagePath = null,
                    editedImagePath = null,
                    editStackJson = "{}",
                    headerOcrRaw = "",
                    calibrationProfileId = null,
                    ambientLux = -1f,
                    qrDeepLink = "azpallete://cart/c1",
                    isArchived = false,
                    syncStatus = "SYNCED",
                    version = 2L,
                    lastSyncedVersion = 2L,
                    updatedAt = 1_700_000_000_000L,
                    swatches = listOf(
                        BackupSwatchDto(
                            id = "s1",
                            shadeCartId = "c1",
                            fabricTypeId = "fab-silk-180",
                            rollId = "r1",
                            swatchNumber = "5",
                            colorHex = "#102A71",
                            colorName = "Azhar Navy",
                            positionOrder = 0,
                            bboxLeft = 0f,
                            bboxTop = 0f,
                            bboxRight = 0f,
                            bboxBottom = 0f,
                            isOverridden = false,
                            labL = 20.0,
                            labA = 12.0,
                            labB = -41.0,
                            weaveTag = "SATIN",
                            sheenScore = 0.85f,
                            pricePerMeter = 120.0,
                            currencyCode = "EGP",
                            version = 1L,
                            updatedAt = 1_700_000_000_000L,
                        ),
                    ),
                ),
            ),
            lots = emptyList(),
            reservations = emptyList(),
            cutLogs = emptyList(),
            priceTiers = emptyList(),
        )

        val zipBytes = PureBackupCrypto.sealToZipBytes(payload, "azhar2026", 1_700_000_000_000L)
        val (manifest, restored) = PureBackupCrypto.openFromZipBytes(zipBytes, "azhar2026")
        assertEquals(1, manifest.cartCount)
        assertEquals(1, restored.rolls.size)
        assertEquals("RL-SLK-005", restored.rolls.first().rollCode)
        assertEquals("#102A71", restored.carts.first().swatches.first().colorHex)

        val wrongPassFailed = runCatching {
            PureBackupCrypto.openFromZipBytes(zipBytes, "wrong-pass")
        }.isFailure
        assertTrue(wrongPassFailed)
    }

    @Test
    fun `OfflineTemplateNaturalLanguageParser, ReverseShadeFinder, ShadeConsistency, and MillInsights rank accurately`() {
        val repo = FakeShadeCartRepository()
        val rollNavy = WarehouseRollModel(
            id = "r-navy",
            rollCode = "RL-SLK-005",
            fabricTypeId = "fab-silk-180",
            shadeLabel = "5",
            colorName = "Azhar Navy",
            colorHex = "#102A71",
            warehouseShed = "wh-alwa",
            lengthMeters = 480.0,
            availableMeters = 350.0,
            lotCode = "LOT-2026-05A",
            status = RollStockStatus.IN_STOCK,
        )
        val rollLowCrimson = WarehouseRollModel(
            id = "r-crimson",
            rollCode = "RL-SLK-009",
            fabricTypeId = "fab-silk-180",
            shadeLabel = "9",
            colorName = "Crimson",
            colorHex = "#CD1820",
            warehouseShed = "wh-shorbagy",
            lengthMeters = 200.0,
            availableMeters = 35.0,
            lotCode = "LOT-2026-09B",
            status = RollStockStatus.LOW_STOCK,
        )
        val cart = ShadeCartModel(
            id = "c1",
            fabricTypeId = "fab-silk-180",
            fabricName = "Silk 180",
            fabricCode = "SLK-180",
            weightGsm = 180,
            widthCm = 150,
            originalImagePath = null,
            editedImagePath = null,
            editStackJson = "{}",
            swatches = listOf(
                SwatchModel(
                    id = "s-navy",
                    shadeCartId = "c1",
                    fabricTypeId = "fab-silk-180",
                    rollId = "r-navy",
                    linkedRoll = rollNavy,
                    swatchNumber = "5",
                    colorHex = "#102A71",
                    colorName = "Azhar Navy",
                    positionOrder = 0,
                    pricePerMeter = 120.0,
                ),
                SwatchModel(
                    id = "s-crimson",
                    shadeCartId = "c1",
                    fabricTypeId = "fab-silk-180",
                    rollId = "r-crimson",
                    linkedRoll = rollLowCrimson,
                    swatchNumber = "9",
                    colorHex = "#CD1820",
                    colorName = "Crimson",
                    positionOrder = 1,
                    pricePerMeter = 110.0,
                ),
            ),
        )
        val lots = listOf(
            DyeLotModel(
                id = "lot-1",
                rollId = "r-navy",
                lotCode = "LOT-2026-05A",
                dyeDate = 1_700_000_000_000L,
                masterDeltaE = 0.8,
                operatorName = "Hassan",
            ),
            DyeLotModel(
                id = "lot-2",
                rollId = "r-crimson",
                lotCode = "LOT-2026-09B",
                dyeDate = 1_700_000_000_000L,
                masterDeltaE = 3.1,
                operatorName = "Hassan",
            ),
        )

        val parser = OfflineTemplateNaturalLanguageParser()
        val navyOver200 = parser.execute("Navy more than 200m", listOf(cart), listOf(rollNavy, rollLowCrimson), lots)
        assertEquals(1, navyOver200.items.size)
        assertEquals("RL-SLK-005", navyOver200.items.first().rollCode)

        val lowStockRes = parser.execute("Low stock", listOf(cart), listOf(rollNavy, rollLowCrimson), lots)
        assertEquals(1, lowStockRes.items.size)
        assertEquals("RL-SLK-009", lowStockRes.items.first().rollCode)

        val searchUseCase = SearchShadesByColorUseCase(repo)
        val ranked = searchUseCase.rankMatches(
            targetLab = ColorScience.hexToLab("#112C74")!!,
            carts = listOf(cart),
            rollsById = mapOf(rollNavy.id to rollNavy, rollLowCrimson.id to rollLowCrimson),
            maxDeltaE = 10.0f,
        )
        assertEquals(1, ranked.size)
        assertEquals("s-navy", ranked.first().swatch.id)

        val consistency = ShadeConsistencyUseCase(repo, FakeMillRepo()).evaluate(
            cart = cart,
            rolls = listOf(rollNavy, rollLowCrimson),
            lots = lots,
        )
        assertEquals(3.1f, consistency.worstDeltaE, 0.01f)

        val insights = ComputeMillInsightsUseCase(repo, FakeMillRepo()).compute(
            carts = listOf(cart),
            rolls = listOf(rollNavy, rollLowCrimson),
            fabricNamesById = mapOf("fab-silk-180" to "Silk 180"),
            syncQueue = listOf(
                SyncQueueEntryModel(
                    id = "sq-1",
                    entityType = "SHADE_CART",
                    entityId = "c1",
                    operation = "UPSERT",
                    status = SyncState.SYNCED,
                    attemptCount = 1,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            lots = lots,
            reservations = emptyList(),
            cuts = emptyList(),
            favoritedIds = setOf("s-navy"),
        )
        assertEquals(1, insights.lowStockCount)
        assertEquals(100.0f, insights.syncHealthScore, 0.01f)
        assertTrue(insights.totalInventoryValue > 0.0)
    }
}

private class FakeMillRepo : com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract {
    override fun observeAllLots() = kotlinx.coroutines.flow.flowOf(emptyList<DyeLotModel>())
    override fun observeLotsForRoll(rollId: String) = kotlinx.coroutines.flow.flowOf(emptyList<DyeLotModel>())
    override fun observeActiveReservations() = kotlinx.coroutines.flow.flowOf(emptyList<com.alazhartex.shadecart.domain.model.ReservationModel>())
    override fun observeAllReservations() = kotlinx.coroutines.flow.flowOf(emptyList<com.alazhartex.shadecart.domain.model.ReservationModel>())
    override fun observeCutLogs() = kotlinx.coroutines.flow.flowOf(emptyList<com.alazhartex.shadecart.domain.model.CutLogModel>())
    override fun observePriceTiers() = kotlinx.coroutines.flow.flowOf(emptyList<com.alazhartex.shadecart.domain.model.PriceTierModel>())
    override fun observeAllSwatchesWithLab() = kotlinx.coroutines.flow.flowOf(emptyList<SwatchModel>())
    override suspend fun createRoll(roll: WarehouseRollModel) = Result.success(roll)
    override suspend fun createDyeLot(lot: DyeLotModel) = Result.success(lot)
    override suspend fun reserveMeters(rollId: String, swatchId: String, meters: Double, operatorName: String, note: String) =
        Result.failure<com.alazhartex.shadecart.domain.model.ReservationModel>(UnsupportedOperationException())
    override suspend fun releaseReservation(reservationId: String) = Result.success(Unit)
    override suspend fun logCut(rollId: String, swatchId: String, metersCut: Double, pieceCount: Int, operatorName: String) =
        Result.failure<com.alazhartex.shadecart.domain.model.CutLogModel>(UnsupportedOperationException())
    override suspend fun updateSwatchPrice(swatchId: String, pricePerMeter: Double?) = Unit
    override suspend fun upsertPriceTier(tier: com.alazhartex.shadecart.domain.model.PriceTierModel) = Unit
    override suspend fun getLowStockRolls() = emptyList<WarehouseRollModel>()
}
