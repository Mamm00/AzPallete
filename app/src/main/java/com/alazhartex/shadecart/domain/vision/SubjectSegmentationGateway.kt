package com.alazhartex.shadecart.domain.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.WorkerThread
import com.alazhartex.shadecart.di.IoDispatcher
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_FAILED
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SegmentationModelState {
    data object Checking : SegmentationModelState
    data object Available : SegmentationModelState
    data object NotDownloaded : SegmentationModelState
    data class Downloading(val progressPercent: Int?) : SegmentationModelState
    data class UnavailableFallback(val reason: String) : SegmentationModelState
}

sealed interface SegmentationOutcome {
    data class Success(val maskFilePath: String) : SegmentationOutcome
    data class ModelNotDownloaded(val reason: String) : SegmentationOutcome
    data class FailedFallbackToCropOnly(val reason: String) : SegmentationOutcome
}

interface SubjectSegmentationGateway {
    suspend fun checkModelAvailability(): SegmentationModelState
    fun downloadModel(): Flow<SegmentationModelState>

    @WorkerThread
    suspend fun segmentForegroundToMask(
        bitmap: Bitmap,
        maskOutFile: File,
    ): SegmentationOutcome
}

/**
 * Background removal using Google ML Kit Subject Segmentation via Google Play Services dynamic
 * module delivery (`ModuleInstallClient`).
 *
 * When the model is not yet downloaded or unavailable on the device, exposes [SegmentationModelState.NotDownloaded]
 * / [SegmentationOutcome.ModelNotDownloaded] so the editor gracefully falls back to crop-only mode
 * with a non-blocking prompt.
 */
@Singleton
class MlKitSubjectSegmentationGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SubjectSegmentationGateway {

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableConfidenceMask()
                        .build(),
                )
                .build(),
        )
    }

    override suspend fun checkModelAvailability(): SegmentationModelState =
        withContext(ioDispatcher) {
            runCatching {
                val client = ModuleInstall.getClient(context)
                val response = client.areModulesAvailable(segmenter).await()
                if (response.areModulesAvailable()) {
                    SegmentationModelState.Available
                } else {
                    SegmentationModelState.NotDownloaded
                }
            }.getOrElse { error ->
                SegmentationModelState.UnavailableFallback(
                    error.message ?: "Google Play Services ML Kit module unavailable on this device",
                )
            }
        }

    override fun downloadModel(): Flow<SegmentationModelState> = callbackFlow {
        trySend(SegmentationModelState.Downloading(progressPercent = 0))
        val client = ModuleInstall.getClient(context)

        val listener = object : InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                val info = update.progressInfo
                val percent = if (info != null && info.totalBytesToDownload > 0) {
                    ((info.bytesDownloaded * 100L) / info.totalBytesToDownload).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                when (update.installState) {
                    STATE_COMPLETED -> {
                        trySend(SegmentationModelState.Available)
                        close()
                    }
                    STATE_FAILED, STATE_CANCELED -> {
                        trySend(
                            SegmentationModelState.UnavailableFallback(
                                "Model download did not complete (error code ${update.errorCode}). Crop-only mode remains active.",
                            ),
                        )
                        close()
                    }
                    else -> {
                        trySend(SegmentationModelState.Downloading(percent))
                    }
                }
            }
        }

        val request = ModuleInstallRequest.newBuilder()
            .addApi(segmenter)
            .setListener(listener)
            .build()

        client.installModules(request)
            .addOnSuccessListener { response ->
                if (response.areModulesAlreadyInstalled()) {
                    trySend(SegmentationModelState.Available)
                    close()
                }
            }
            .addOnFailureListener { exc ->
                trySend(
                    SegmentationModelState.UnavailableFallback(
                        exc.message ?: "Unable to download ML Kit Subject Segmentation model",
                    ),
                )
                close()
            }

        awaitClose {
            client.unregisterListener(listener)
        }
    }

    @WorkerThread
    override suspend fun segmentForegroundToMask(
        bitmap: Bitmap,
        maskOutFile: File,
    ): SegmentationOutcome = withContext(ioDispatcher) {
        val availability = checkModelAvailability()
        if (availability is SegmentationModelState.NotDownloaded) {
            return@withContext SegmentationOutcome.ModelNotDownloaded(
                "Subject Segmentation model is not downloaded yet.",
            )
        }
        if (availability is SegmentationModelState.UnavailableFallback) {
            return@withContext SegmentationOutcome.FailedFallbackToCropOnly(availability.reason)
        }

        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = segmenter.process(image).await()
            val width = bitmap.width
            val height = bitmap.height
            val maskPixels = IntArray(width * height) { Color.TRANSPARENT }

            for (subject in result.subjects) {
                val confMask = subject.confidenceMask ?: continue
                confMask.rewind()
                for (j in 0 until subject.height) {
                    val py = subject.startY + j
                    if (py !in 0 until height) {
                        confMask.position(confMask.position() + subject.width)
                        continue
                    }
                    for (i in 0 until subject.width) {
                        val conf = confMask.get()
                        val px = subject.startX + i
                        if (px in 0 until width && conf > 0.5f) {
                            val alpha = (conf.coerceIn(0f, 1f) * 255f).toInt()
                            maskPixels[py * width + px] = Color.argb(alpha, 255, 255, 255)
                        }
                    }
                }
                confMask.rewind()
            }

            if (result.subjects.isEmpty()) {
                return@withContext SegmentationOutcome.FailedFallbackToCropOnly(
                    "No distinct foreground subject detected; kept crop-only image.",
                )
            }

            val maskBitmap = Bitmap.createBitmap(maskPixels, width, height, Bitmap.Config.ARGB_8888)
            try {
                maskOutFile.parentFile?.mkdirs()
                FileOutputStream(maskOutFile).use { fos ->
                    maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            } finally {
                if (!maskBitmap.isRecycled) {
                    maskBitmap.recycle()
                }
            }

            SegmentationOutcome.Success(maskOutFile.absolutePath)
        }.getOrElse { exc ->
            val msg = exc.message.orEmpty()
            if (msg.contains("Waiting for", ignoreCase = true) ||
                msg.contains("not downloaded", ignoreCase = true) ||
                msg.contains("unavailable", ignoreCase = true)
            ) {
                SegmentationOutcome.ModelNotDownloaded(
                    "ML Kit Subject Segmentation model is downloading or unavailable. Using crop-only fallback.",
                )
            } else {
                SegmentationOutcome.FailedFallbackToCropOnly(
                    exc.message ?: "Background segmentation failed; continuing crop-only.",
                )
            }
        }
    }
}
