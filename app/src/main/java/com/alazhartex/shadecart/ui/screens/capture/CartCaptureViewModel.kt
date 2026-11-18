package com.alazhartex.shadecart.ui.screens.capture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import com.alazhartex.shadecart.domain.model.CalibrationPreferencesState
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.usecase.DeleteCalibrationProfileUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveCalibrationStateUseCase
import com.alazhartex.shadecart.domain.usecase.SaveCalibrationProfileUseCase
import com.alazhartex.shadecart.domain.usecase.SelectCalibrationProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CartCaptureUiState(
    val isBusy: Boolean = false,
    val busyMessage: String = "Downsampling to <=2048px...",
    val restorableCapturePath: String? = null,
    val pendingTempCapturePath: String? = null,
    val hasRequestedCameraPermission: Boolean = false,
    val calibrationState: CalibrationPreferencesState = CalibrationPreferencesState(),
    val liveFocusScore: Float? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CartCaptureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bitmapProcessor: BitmapProcessor,
    observeCalibrationState: ObserveCalibrationStateUseCase,
    private val saveCalibrationProfile: SaveCalibrationProfileUseCase,
    private val selectCalibrationProfile: SelectCalibrationProfileUseCase,
    private val deleteCalibrationProfile: DeleteCalibrationProfileUseCase,
) : ViewModel() {

    companion object {
        private const val KEY_TEMP_CAPTURE_PATH = "capture_temp_path"
        private const val KEY_RESTORABLE_PATH = "capture_restorable_path"
        private const val KEY_REQUESTED_CAMERA = "capture_requested_camera"
    }

    private val localState = MutableStateFlow(restoreInitialState())

    val uiState: StateFlow<CartCaptureUiState> = combine(
        localState,
        observeCalibrationState(),
    ) { local, cal ->
        local.copy(calibrationState = cal)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = localState.value,
    )

    private fun restoreInitialState(): CartCaptureUiState {
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
        localState.update { it.copy(hasRequestedCameraPermission = true) }
    }

    fun updateLiveFocusScore(score: Float) {
        localState.update { it.copy(liveFocusScore = score) }
    }

    fun saveCalibration(profile: CalibrationProfile) {
        viewModelScope.launch {
            saveCalibrationProfile(profile, setAsActive = true)
        }
    }

    fun selectCalibration(profileId: String) {
        viewModelScope.launch {
            selectCalibrationProfile(profileId)
        }
    }

    fun deleteCalibration(profileId: String) {
        viewModelScope.launch {
            deleteCalibrationProfile(profileId)
        }
    }

    fun prepareTempCaptureFile(): File {
        val file = bitmapProcessor.newTempCaptureFile("jpg")
        savedStateHandle[KEY_TEMP_CAPTURE_PATH] = file.absolutePath
        localState.update { it.copy(pendingTempCapturePath = file.absolutePath, errorMessage = null) }
        return file
    }

    fun onPhotoCaptured(tempFile: File, onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            localState.update {
                it.copy(isBusy = true, busyMessage = "Downsampling to <=2048px...", errorMessage = null)
            }
            runCatching {
                val boundedPath = bitmapProcessor.finalizeCapturedTempFile(tempFile.absolutePath)
                savedStateHandle[KEY_TEMP_CAPTURE_PATH] = null
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                localState.update {
                    it.copy(
                        isBusy = false,
                        pendingTempCapturePath = null,
                        restorableCapturePath = boundedPath,
                    )
                }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                localState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Capture processing failed")
                }
            }
        }
    }

    fun onBurstCaptured(files: List<File>, onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            localState.update {
                it.copy(isBusy = true, busyMessage = "Stacking 3 burst frames (median HDR)...", errorMessage = null)
            }
            runCatching {
                val boundedPath = bitmapProcessor.mergeBurstToBoundedOriginal(files.map { it.absolutePath })
                files.forEach { it.delete() }
                savedStateHandle[KEY_TEMP_CAPTURE_PATH] = null
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                localState.update {
                    it.copy(
                        isBusy = false,
                        pendingTempCapturePath = null,
                        restorableCapturePath = boundedPath,
                    )
                }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                localState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Burst stack failed")
                }
            }
        }
    }

    fun onGalleryPhotoPicked(uri: Uri, onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            localState.update {
                it.copy(isBusy = true, busyMessage = "Downsampling to <=2048px...", errorMessage = null)
            }
            runCatching {
                val boundedPath = bitmapProcessor.importUriToBoundedOriginal(uri)
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                localState.update { it.copy(isBusy = false, restorableCapturePath = boundedPath) }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                localState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Unable to import selected image")
                }
            }
        }
    }

    fun onLoadSampleCart(onReadyForEdit: (String) -> Unit) {
        viewModelScope.launch {
            localState.update {
                it.copy(isBusy = true, busyMessage = "Preparing sample A4 shade cart...", errorMessage = null)
            }
            runCatching {
                val boundedPath = bitmapProcessor.importAssetToBoundedOriginal("sample_shade_cart.png")
                savedStateHandle[KEY_RESTORABLE_PATH] = boundedPath
                boundedPath
            }.onSuccess { boundedPath ->
                localState.update { it.copy(isBusy = false, restorableCapturePath = boundedPath) }
                onReadyForEdit(boundedPath)
            }.onFailure { err ->
                localState.update {
                    it.copy(isBusy = false, errorMessage = err.message ?: "Failed to load sample cart")
                }
            }
        }
    }

    fun resumePendingCapture(onReadyForEdit: (String) -> Unit) {
        val candidate = localState.value.restorableCapturePath ?: return
        if (candidate == localState.value.pendingTempCapturePath && File(candidate).exists()) {
            onPhotoCaptured(File(candidate), onReadyForEdit)
        } else if (File(candidate).exists()) {
            onReadyForEdit(candidate)
        }
    }

    fun discardRestorableCapture() {
        savedStateHandle[KEY_RESTORABLE_PATH] = null
        savedStateHandle[KEY_TEMP_CAPTURE_PATH] = null
        localState.update { it.copy(restorableCapturePath = null, pendingTempCapturePath = null) }
    }

    fun onCaptureError(message: String) {
        localState.update { it.copy(isBusy = false, errorMessage = message) }
    }
}
