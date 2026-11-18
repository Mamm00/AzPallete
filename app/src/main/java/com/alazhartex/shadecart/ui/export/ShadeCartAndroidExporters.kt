package com.alazhartex.shadecart.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.alazhartex.shadecart.domain.export.LookbookHtmlExporter
import com.alazhartex.shadecart.domain.export.ShadeCartFormatters
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * B1 / C1 Android FileProvider export engine producing:
 * - Crisp A4 PDF shade cards (`PdfDocument` + bundled `brand_logo.png` + `ZXing` QR code + pinked swatches)
 * - Printable A4 warehouse roll QR label sheets
 * - Shareable PNG shade cards
 * - GIMP `.gpl`, Procreate `.swatches`, `.csv`, and offline `.html` lookbooks inside `cacheDir/exports/`
 */
object ShadeCartAndroidExporters {

    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    fun generateQrBitmap(content: String, sizePx: Int = 256): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    fun exportA4Pdf(
        context: Context,
        cart: ShadeCartModel,
        isCalibrated: Boolean = false,
    ): File {
        val outFile = File(exportsDir(context), "AlAzharTex_${safeSlug(cart.fabricCode)}.pdf")
        val document = PdfDocument()

        val swatches = cart.swatches.sortedBy { it.positionOrder }
        val perPage = 10
        val chunks = if (swatches.isEmpty()) listOf(emptyList()) else swatches.chunked(perPage)
        val logoBitmap = runCatching {
            context.assets.open("brand_logo.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        val qrBitmap = generateQrBitmap("azpallete://cart/${cart.id}", 140)

        try {
            chunks.forEachIndexed { pageIndex, pageSwatches ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                drawPdfPage(
                    canvas = page.canvas,
                    cart = cart,
                    pageSwatches = pageSwatches,
                    pageNumber = pageIndex + 1,
                    totalPages = chunks.size,
                    isCalibrated = isCalibrated,
                    logoBitmap = logoBitmap,
                    qrBitmap = qrBitmap,
                )
                document.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } finally {
            document.close()
            logoBitmap?.recycle()
            if (!qrBitmap.isRecycled) qrBitmap.recycle()
        }
        return outFile
    }

    private fun drawPdfPage(
        canvas: Canvas,
        cart: ShadeCartModel,
        pageSwatches: List<com.alazhartex.shadecart.domain.model.SwatchModel>,
        pageNumber: Int,
        totalPages: Int,
        isCalibrated: Boolean,
        logoBitmap: Bitmap?,
        qrBitmap: Bitmap,
    ) {
        val bgPaint = Paint().apply { color = Color.parseColor("#FAF9F5") }
        canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat(), bgPaint)

        val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#102A71") }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102A71")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 11f
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }

        val margin = 32f
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, RectF(margin, 26f, margin + 44f, 70f), null)
        }

        val textStart = margin + (if (logoBitmap != null) 54f else 0f)
        canvas.drawText("Al AzharTex · Digital Shade Cart", textStart, 42f, titlePaint)
        canvas.drawText(
            "${cart.fabricName} (${cart.fabricCode}) · ${cart.weightGsm} GSM · ${cart.widthCm} cm" +
                if (isCalibrated) " · Calibrated ✓" else "",
            textStart,
            60f,
            subtitlePaint,
        )

        canvas.drawBitmap(qrBitmap, null, RectF(A4_WIDTH_PT - margin - 64f, 22f, A4_WIDTH_PT - margin, 86f), null)
        canvas.drawLine(margin, 94f, A4_WIDTH_PT - margin, 94f, navyPaint.apply { strokeWidth = 1.5f })

        val colGap = 18f
        val colWidth = (A4_WIDTH_PT - 2f * margin - colGap) / 2f
        val rowHeight = 132f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(60, 0, 0, 0)
            strokeWidth = 1f
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#E2E0D8")
            strokeWidth = 1f
        }
        val cardBgPaint = Paint().apply { color = Color.WHITE }

        pageSwatches.forEachIndexed { idx, sw ->
            val col = idx % 2
            val row = idx / 2
            val x = margin + col * (colWidth + colGap)
            val y = 110f + row * rowHeight
            val cardRect = RectF(x, y, x + colWidth, y + rowHeight - 10f)
            canvas.drawRoundRect(cardRect, 8f, 8f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 8f, 8f, cardBorderPaint)

            val swW = colWidth - 20f
            val swH = 74f
            val swLeft = x + 10f
            val swTop = y + 8f
            val coords = ShadeCartFormatters.buildPinkedPerimeterCoords(swW, swH)
            val path = Path()
            coords.forEachIndexed { pIdx, (px, py) ->
                if (pIdx == 0) path.moveTo(swLeft + px, swTop + py)
                else path.lineTo(swLeft + px, swTop + py)
            }
            path.close()
            fillPaint.color = HexColorValidator.parseArgbOrNull(sw.colorHex) ?: Color.DKGRAY
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#171B26")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(
                "#${sw.swatchNumber} · ${sw.colorName.ifBlank { sw.colorHex }}",
                x + 10f,
                swTop + swH + 16f,
                labelPaint,
            )
            val stockText = sw.linkedRoll?.let { r ->
                "${r.rollCode} (${String.format(Locale.US, "%.0f", r.availableMeters)}m)"
            } ?: ""
            canvas.drawText(
                "${sw.colorHex.uppercase(Locale.US)}  $stockText".trim(),
                x + 10f,
                swTop + swH + 30f,
                monoPaint,
            )
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
        }
        canvas.drawText(
            "Al AzharTex · Page $pageNumber of $totalPages · azpallete://cart/${cart.id}",
            margin,
            A4_HEIGHT_PT - 20f,
            footerPaint,
        )
    }

    fun exportRollQrLabelSheetPdf(
        context: Context,
        rolls: List<WarehouseRollModel>,
    ): File {
        val outFile = File(exportsDir(context), "AlAzharTex_Roll_QR_Labels.pdf")
        val document = PdfDocument()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102A71")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 9f
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        try {
            val pages = if (rolls.isEmpty()) listOf(emptyList()) else rolls.chunked(12)
            pages.forEachIndexed { pageIdx, chunk ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIdx + 1).create(),
                )
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val margin = 24f
                val cellW = (A4_WIDTH_PT - margin * 2f - 16f) / 2f
                val cellH = (A4_HEIGHT_PT - margin * 2f - 40f) / 6f

                chunk.forEachIndexed { idx, roll ->
                    val col = idx % 2
                    val row = idx / 2
                    val left = margin + col * (cellW + 16f)
                    val top = margin + row * (cellH + 8f)
                    canvas.drawRect(left, top, left + cellW, top + cellH, borderPaint)

                    val payload = roll.qrPayload.ifBlank { "azpallete://roll/${roll.rollCode}" }
                    val qr = generateQrBitmap(payload, 96)
                    try {
                        canvas.drawBitmap(qr, null, RectF(left + 8f, top + 12f, left + 84f, top + 88f), null)
                    } finally {
                        if (!qr.isRecycled) qr.recycle()
                    }
                    canvas.drawText(roll.rollCode, left + 92f, top + 28f, labelPaint)
                    canvas.drawText("Shade #${roll.shadeLabel} · ${roll.colorName}", left + 92f, top + 46f, subPaint)
                    canvas.drawText("${roll.warehouseShed} · ${roll.availableMeters.toInt()}m", left + 92f, top + 62f, subPaint)
                    if (roll.lotCode.isNotBlank()) {
                        canvas.drawText("Lot: ${roll.lotCode}", left + 92f, top + 78f, subPaint)
                    }
                }
                document.finishPage(page)
            }
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } finally {
            document.close()
        }
        return outFile
    }

    fun exportPngCard(context: Context, cart: ShadeCartModel): File {
        val width = 1080
        val rows = ((cart.swatches.size + 1) / 2).coerceAtLeast(1)
        val height = 240 + rows * 260
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FAF9F5"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102A71")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 26f
        }
        canvas.drawText("${cart.fabricName} (${cart.fabricCode})", 48f, 84f, titlePaint)
        canvas.drawText("${cart.weightGsm} GSM · ${cart.widthCm} cm", 48f, 128f, subPaint)

        val qr = generateQrBitmap("azpallete://cart/${cart.id}", 136)
        canvas.drawBitmap(qr, null, Rect(width - 184, 32, width - 48, 168), null)
        if (!qr.isRecycled) qr.recycle()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardW = (width - 120f) / 2f
        cart.swatches.sortedBy { it.positionOrder }.forEachIndexed { idx, sw ->
            val col = idx % 2
            val row = idx / 2
            val x = 48f + col * (cardW + 24f)
            val y = 210f + row * 250f
            val coords = ShadeCartFormatters.buildPinkedPerimeterCoords(cardW - 16f, 150f)
            val path = Path()
            coords.forEachIndexed { pIdx, (px, py) ->
                if (pIdx == 0) path.moveTo(x + px, y + py) else path.lineTo(x + px, y + py)
            }
            path.close()
            fillPaint.color = HexColorValidator.parseArgbOrNull(sw.colorHex) ?: Color.DKGRAY
            canvas.drawPath(path, fillPaint)
            canvas.drawText("#${sw.swatchNumber} · ${sw.colorHex.uppercase(Locale.US)}", x, y + 188f, subPaint)
        }

        val outFile = File(exportsDir(context), "AlAzharTex_${safeSlug(cart.fabricCode)}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        return outFile
    }

    fun exportGimpPalette(context: Context, cart: ShadeCartModel): File =
        writeBytes(context, "AlAzharTex_${safeSlug(cart.fabricCode)}.gpl", ShadeCartFormatters.toGimpPaletteText(cart).toByteArray(StandardCharsets.UTF_8))

    fun exportProcreateSwatches(context: Context, cart: ShadeCartModel): File =
        writeBytes(context, "AlAzharTex_${safeSlug(cart.fabricCode)}.swatches", ShadeCartFormatters.toProcreateSwatchesZipBytes(cart))

    fun exportCsv(context: Context, cart: ShadeCartModel): File =
        writeBytes(context, "AlAzharTex_${safeSlug(cart.fabricCode)}.csv", ShadeCartFormatters.toCsvText(cart).toByteArray(StandardCharsets.UTF_8))

    fun exportOfflineHtmlLookbook(
        context: Context,
        carts: List<ShadeCartModel>,
        calibrated: Boolean = false,
    ): File {
        val html = LookbookHtmlExporter.generateHtml(carts = carts, calibrated = calibrated)
        return writeBytes(context, "AlAzharTex_Lookbook.html", html.toByteArray(StandardCharsets.UTF_8))
    }

    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        captionText: String? = null,
        preferWhatsApp: Boolean = false,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!captionText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, captionText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (preferWhatsApp) {
                setPackage("com.whatsapp")
            }
        }
        val chooser = if (preferWhatsApp) {
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) {
                intent
            } else {
                Intent.createChooser(intent.apply { setPackage(null) }, "Share Shade Cart")
            }
        } else {
            Intent.createChooser(intent, "Share Shade Cart")
        }
        context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sharePlainText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun writeBytes(context: Context, fileName: String, bytes: ByteArray): File {
        val file = File(exportsDir(context), fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun safeSlug(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "cart" }
}
