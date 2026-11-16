package com.alazhartex.shadecart.domain.vision

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.di.IoDispatcher
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface TextRecognitionGateway {
    @WorkerThread
    suspend fun recognizeShadeCart(bitmap: Bitmap): ShadeCartOcrResult
}

/**
 * On-device OCR via Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`).
 * See README.md for the Google-hosted closed-source model disclosure.
 */
@Singleton
class MlKitTextRecognitionGateway @Inject constructor(
    private val parser: ShadeCartOcrParser = ShadeCartOcrParser(),
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextRecognitionGateway {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    @WorkerThread
    override suspend fun recognizeShadeCart(bitmap: Bitmap): ShadeCartOcrResult =
        withContext(ioDispatcher) {
            val w = bitmap.width.coerceAtLeast(1).toFloat()
            val h = bitmap.height.coerceAtLeast(1).toFloat()
            val input = InputImage.fromBitmap(bitmap, 0)
            val result = runCatching { recognizer.process(input).await() }.getOrNull()
                ?: return@withContext parser.parse(emptyList())

            val blocks = mutableListOf<OcrTextBlock>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val rect = line.boundingBox ?: continue
                    val norm = NormalizedRect(
                        left = (rect.left / w).coerceIn(0f, 1f),
                        top = (rect.top / h).coerceIn(0f, 1f),
                        right = (rect.right / w).coerceIn(0f, 1f),
                        bottom = (rect.bottom / h).coerceIn(0f, 1f),
                    )
                    blocks.add(OcrTextBlock(text = line.text, bounds = norm))

                    // Also emit numeric elements separately so swatch numbers grouped on a line
                    // with adjacent labels still map cleanly to their individual coordinates.
                    for (element in line.elements) {
                        val elText = element.text.trim().removePrefix("#")
                        if (elText.matches(Regex("""^\d{1,4}$"""))) {
                            val er = element.boundingBox ?: continue
                            blocks.add(
                                OcrTextBlock(
                                    text = elText,
                                    bounds = NormalizedRect(
                                        left = (er.left / w).coerceIn(0f, 1f),
                                        top = (er.top / h).coerceIn(0f, 1f),
                                        right = (er.right / w).coerceIn(0f, 1f),
                                        bottom = (er.bottom / h).coerceIn(0f, 1f),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
            parser.parse(blocks)
        }
}
