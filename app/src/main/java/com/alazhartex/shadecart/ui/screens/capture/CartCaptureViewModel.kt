package com.alazhartex.shadecart.ui.screens.capture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Immutable presentation state for the A4 Shade Cart Capture & Upload screen
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.3).
 */
data class CartCaptureUiState(
    val isBusy: Boolean = false,
    val restorableCapturePath: String? = null,
    val pendingTempCapturePath: String? = null,
    val hasRequestedCameraPermission: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * `@HiltViewModel` coordinating CameraX capture temp files, Photo Picker gallery imports, sample
 * A4 asset loading, and `SavedStateHandle` process-death recovery
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.2, 6.3, & 7.1).
 */
@HiltViewModel
class CartCaptureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bitmapProcessor: BitmapProcessor,
) : ViewModel() {

    companion object {
        private const val KEY_TEMP_CAPTURE_PATH = "capture_temp_path"
        private const val KEY_RESTORABLE_PATH = "capture_restorable_path"
        private const val KEY_REQUESTED_CAMERA = "capture_requested_camera"
    }

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<CartCaptureUiState> = _uiState.asStateFlow()

    private fun restoreInitialState(): CartCaptureUiState {
        // Survive process death: if an in-progress temp/original capture file exists on disk, surface it.
        val savedFinal = savedStateHandle.get<String>(KEY_RESTORABLE_PATH)
            ?.takeIf { File(it).exists() }
        val savedTemp = savedStateHandle.get<String>(KEY_TEMP_CAPTURE_PATH)
            ?.takeIf { File(it).exists() }
        val requestedCamera = savedStateHandle.get<Boolean>(KEY_REQUESTED_CAMERA) ?: false

        return CartCaptureUiState(
            restorableCapturePath = savedFinal ?: savedTemp,
            pendingTempCapturePath = savedTemp,
            hasRequestedCameraPermission = requestedCamera,
        )
    }

    fun markCameraPermissionRequested() {
        savedStateHandle[KEY_REQUESTED_CAMERA] = true
        _uiState.update { it.copy(hasRequestedCameraPermission = true) }
    }

    fun prepareTempCaptureFile(): File {
        val file = bitmapProcessor.newTempCaptureFile("jpg")
        savedStateHandle[KEY_TEMP_CAPTURE_PATH] = file.absolutePath
        _uiState.update { it.copy(pendingTempCapturePath = file.absolutePath, errorMessage = null) }
        return file
    }

    fun onPhotoCaptured(tempFile: File, onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                val boundedPath = bitmapProcessor.finalizeCapturedTempFile(tempFile.absolutePath)
                savedStateHandle[KEY_TEMP_CAPTURE_PATH] = null
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingTempCapturePath = null,
                        restorableCapturePath = boundedPath,
                    )
                }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Capture processing failed")
                }
            }
        }
    }

    fun onGalleryPhotoPicked(uri: Uri, onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                val boundedPath = bitmapProcessor.importUriToBoundedOriginal(uri)
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                _uiState.update { it.copy(isBusy = false, restorableCapturePath = boundedPath) }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Unable to import selected image")
                }
            }
        }
    }

    fun onLoadSampleCart(onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching {
                val boundedPath = bitmapProcessor.importAssetToBoundedOriginal("sample_shade_cart.png")
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                _uiState.update { it.copy(isBusy = false, restorableCapturePath = boundedPath) }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Failed to load sample cart")
                }
            }
        }
    }

    fun resumePendingCapture(onReadyForEdit: (String) -> Unit) {
        val candidate = _uiState.value.restorableCapturePath ?: return
        if (candidate == _uiState.value.pendingTempCapturePath && File(candidate).exists()) {
            onPhotoCaptured(File(candidate), onReadyForEdit)
        } else if (File(candidate).exists()) {
            onReadyForEdit(candidate)
        }
    }

    fun discardRestorableCapture() {
        savedStateHandle[KEY_RESTORABLE_PATH] = null
        savedStateHandle[KEY_TEMP_CAPTURE_PATH] = null
        _uiState.update { it.copy(restorableCapturePath = null, pendingTempCapturePath = null) }
    }

    fun onCaptureError(message: String) {
        _uiState.update { it.copy(isBusy = false, errorMessage = message) }
    }
}
