package com.alazhartex.shadecart.ui.screens.edit

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alazhartex.shadecart.data.model.CropAspectPreset
import com.alazhartex.shadecart.data.model.EditStack
import com.alazhartex.shadecart.data.model.EditStep
import com.alazhartex.shadecart.data.model.EditStepType
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import com.alazhartex.shadecart.domain.imaging.TwoColumnSwatchDetector
import com.alazhartex.shadecart.domain.model.FabricTypeModel
import com.alazhartex.shadecart.domain.model.ShadeCartCreateInput
import com.alazhartex.shadecart.domain.model.ShadeCartSaveOutcome
import com.alazhartex.shadecart.domain.model.SwatchInput
import com.alazhartex.shadecart.domain.usecase.CreateOrGetShadeCartUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveFabricTypesUseCase
import com.alazhartex.shadecart.domain.usecase.ObserveShadeCartByIdUseCase
import com.alazhartex.shadecart.domain.usecase.SaveShadeCartUseCase
import com.alazhartex.shadecart.domain.vision.SegmentationModelState
import com.alazhartex.shadecart.domain.vision.SegmentationOutcome
import com.alazhartex.shadecart.domain.vision.SubjectSegmentationGateway
import com.alazhartex.shadecart.domain.vision.TextRecognitionGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Editable UI model for an automatically detected or user-adjusted swatch on the card editor.
 */
@Serializable
data class DetectedSwatchEditable(
    val id: String,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String,
    val bboxLeft: Float,
    val bboxTop: Float,
    val bboxRight: Float,
    val bboxBottom: Float,
)

/**
 * Immutable presentation state for non-destructive card editing, OCR metadata review,
 * Subject Segmentation download prompts, and two-column swatch inspection
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 6.3 & 10.1).
 */
data class CartEditUiState(
    val sessionId: String = "",
    val existingCartId: String? = null,
    val expectedVersion: Long = 1L,
    val originalImagePath: String? = null,
    val workingPreviewPath: String? = null,
    val editStack: EditStack = EditStack(),
    val segmentationModelState: SegmentationModelState = SegmentationModelState.Checking,
    val nonBlockingMessage: String? = null,
    val fabricName: String = "",
    val fabricCode: String = "",
    val weightGsmText: String = "",
    val widthCmText: String = "",
    val rawHeaderOcr: String = "",
    val detectedSwatches: List<DetectedSwatchEditable> = emptyList(),
    val fabricTypes: List<FabricTypeModel> = emptyList(),
    val selectedFabricTypeId: String? = null,
    val isProcessingImage: Boolean = false,
    val isSaving: Boolean = false,
    val hasRunInitialAnalysis: Boolean = false,
)

/**
 * `@HiltViewModel` backing [CartEditRoute], coordinating non-destructive CanHub cropping,
 * ML Kit Text Recognition & Subject Segmentation gateways, two-column pinked-swatch detection,
 * and Clean Architecture persistence UseCases (Pyre, Chapter 1.2, 6.3, 7.1, & 10.1).
 */
@HiltViewModel
class CartEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeShadeCartById: ObserveShadeCartByIdUseCase,
    private val observeFabricTypes: ObserveFabricTypesUseCase,
    private val createOrGetShadeCart: CreateOrGetShadeCartUseCase,
    private val saveShadeCartUseCase: SaveShadeCartUseCase,
    private val bitmapProcessor: BitmapProcessor,
    private val textRecognitionGateway: TextRecognitionGateway,
    private val subjectSegmentationGateway: SubjectSegmentationGateway,
    private val swatchDetector: TwoColumnSwatchDetector,
    private val json: Json,
) : ViewModel() {

    companion object {
        private const val KEY_SESSION_ID = "edit_session_id"
        private const val KEY_CART_ID = "cartId"
        private const val KEY_IMAGE_PATH = "imagePath"
        private const val KEY_WORKING_PATH = "edit_working_path"
        private const val KEY_EDIT_STACK = "edit_stack_json"
        private const val KEY_FABRIC_NAME = "edit_fabric_name"
        private const val KEY_FABRIC_CODE = "edit_fabric_code"
        private const val KEY_WEIGHT_GSM = "edit_weight_gsm"
        private const val KEY_WIDTH_CM = "edit_width_cm"
        private const val KEY_RAW_OCR = "edit_raw_ocr"
        private const val KEY_SWATCHES_JSON = "edit_swatches_json"
        private const val KEY_ANALYZED = "edit_analyzed"
    }

    private val sessionId: String = savedStateHandle.get<String>(KEY_SESSION_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_SESSION_ID] = it }

    private val _uiState = MutableStateFlow(restoreStateFromHandle())
    val uiState: StateFlow<CartEditUiState> = _uiState.asStateFlow()

    init {
        loadCatalogAndExistingCartIfAny()
        refreshSegmentationAvailability()
    }

    /** Decodes a memory-bounded `RGB_565` preview bitmap off the main thread. */
    suspend fun loadPreviewBitmap(imagePath: String): Bitmap? =
        bitmapProcessor.loadRgb565Preview(imagePath)

    private fun restoreStateFromHandle(): CartEditUiState {
        val stackJson = savedStateHandle.get<String>(KEY_EDIT_STACK)
        val stack = stackJson?.let { runCatching { json.decodeFromString<EditStack>(it) }.getOrNull() }
            ?: EditStack()
        val swatchesJson = savedStateHandle.get<String>(KEY_SWATCHES_JSON)
        val restoredSwatches = swatchesJson?.let {
            runCatching { json.decodeFromString<List<DetectedSwatchEditable>>(it) }.getOrNull()
        }.orEmpty()

        val initialImg = savedStateHandle.get<String>(KEY_IMAGE_PATH)
        val working = savedStateHandle.get<String>(KEY_WORKING_PATH) ?: initialImg

        return CartEditUiState(
            sessionId = sessionId,
            existingCartId = savedStateHandle.get<String>(KEY_CART_ID),
            originalImagePath = initialImg,
            workingPreviewPath = working,
            editStack = stack,
            fabricName = savedStateHandle.get<String>(KEY_FABRIC_NAME).orEmpty(),
            fabricCode = savedStateHandle.get<String>(KEY_FABRIC_CODE).orEmpty(),
            weightGsmText = savedStateHandle.get<String>(KEY_WEIGHT_GSM).orEmpty(),
            widthCmText = savedStateHandle.get<String>(KEY_WIDTH_CM).orEmpty(),
            rawHeaderOcr = savedStateHandle.get<String>(KEY_RAW_OCR).orEmpty(),
            detectedSwatches = restoredSwatches,
            hasRunInitialAnalysis = savedStateHandle.get<Boolean>(KEY_ANALYZED) ?: false,
        )
    }

    private fun loadCatalogAndExistingCartIfAny() {
        viewModelScope.launch {
            observeFabricTypes().collect { fabrics ->
                _uiState.update { it.copy(fabricTypes = fabrics) }
            }
        }

        val cartId = savedStateHandle.get<String>(KEY_CART_ID)
        if (!cartId.isNullOrBlank()) {
            viewModelScope.launch {
                val existing = observeShadeCartById(cartId).first()
                if (existing != null) {
                    val stack = runCatching {
                        json.decodeFromString<EditStack>(existing.editStackJson)
                    }.getOrDefault(EditStack())
                    val swatches = existing.swatches.map { sw ->
                        DetectedSwatchEditable(
                            id = sw.id,
                            swatchNumber = sw.swatchNumber,
                            colorHex = sw.colorHex,
                            colorName = sw.colorName,
                            bboxLeft = sw.bboxLeft,
                            bboxTop = sw.bboxTop,
                            bboxRight = sw.bboxRight,
                            bboxBottom = sw.bboxBottom,
                        )
                    }
                    _uiState.update { state ->
                        if (state.hasRunInitialAnalysis) {
                            state.copy(expectedVersion = existing.version)
                        } else {
                            state.copy(
                                existingCartId = existing.id,
                                expectedVersion = existing.version,
                                originalImagePath = state.originalImagePath ?: existing.originalImagePath,
                                workingPreviewPath = state.workingPreviewPath
                                    ?: existing.editedImagePath
                                    ?: existing.originalImagePath,
                                editStack = stack,
                                fabricName = state.fabricName.ifBlank { existing.fabricName },
                                fabricCode = state.fabricCode.ifBlank { existing.fabricCode },
                                weightGsmText = state.weightGsmText.ifBlank { existing.weightGsm.toString() },
                                widthCmText = state.widthCmText.ifBlank { existing.widthCm.toString() },
                                rawHeaderOcr = existing.headerOcrRaw,
                                selectedFabricTypeId = existing.fabricTypeId,
                                detectedSwatches = if (state.detectedSwatches.isEmpty()) swatches else state.detectedSwatches,
                                hasRunInitialAnalysis = true,
                            )
                        }
                    }
                }
            }
        } else if (!_uiState.value.hasRunInitialAnalysis && !_uiState.value.originalImagePath.isNullOrBlank()) {
            analyzeCurrentImage()
        }
    }

    fun refreshSegmentationAvailability() {
        viewModelScope.launch {
            val state = subjectSegmentationGateway.checkModelAvailability()
            _uiState.update { it.copy(segmentationModelState = state) }
        }
    }

    fun downloadSegmentationModel() {
        viewModelScope.launch {
            subjectSegmentationGateway.downloadModel().collect { state ->
                _uiState.update { current ->
                    val message = when (state) {
                        is SegmentationModelState.Available ->
                            "ML Kit Subject Segmentation model ready. You can now remove backgrounds."
                        is SegmentationModelState.UnavailableFallback -> state.reason
                        else -> current.nonBlockingMessage
                    }
                    current.copy(
                        segmentationModelState = state,
                        nonBlockingMessage = message,
                    )
                }
            }
        }
    }

    fun setAspectPreset(preset: CropAspectPreset) {
        val currentStack = _uiState.value.editStack
        val updated = currentStack.copy(
            aspectPreset = preset,
            history = currentStack.history + EditStep(
                id = UUID.randomUUID().toString(),
                type = EditStepType.ASPECT_PRESET,
                description = "Aspect: ${preset.label}",
                timestampMillis = System.currentTimeMillis(),
            ),
        )
        persistEditStack(updated)
    }

    fun applyCrop(normalizedRect: NormalizedRect) {
        val currentStack = _uiState.value.editStack
        val updated = currentStack.copy(
            cropRect = normalizedRect,
            history = currentStack.history + EditStep(
                id = UUID.randomUUID().toString(),
                type = EditStepType.CROP,
                description = "Crop ${(normalizedRect.width() * 100).toInt()}%x${(normalizedRect.height() * 100).toInt()}%",
                timestampMillis = System.currentTimeMillis(),
            ),
        )
        rerenderNonDestructivePreview(updated)
    }

    fun rotate90Degrees() {
        val currentStack = _uiState.value.editStack
        val nextAngle = (currentStack.rotationDegrees + 90) % 360
        val updated = currentStack.copy(
            rotationDegrees = nextAngle,
            history = currentStack.history + EditStep(
                id = UUID.randomUUID().toString(),
                type = EditStepType.ROTATION,
                description = "Rotate to ${nextAngle}°",
                timestampMillis = System.currentTimeMillis(),
            ),
        )
        rerenderNonDestructivePreview(updated)
    }

    fun toggleBackgroundRemoval() {
        val state = _uiState.value
        val originalPath = state.originalImagePath ?: return
        if (state.editStack.backgroundRemoved) {
            val updated = state.editStack.copy(
                backgroundRemoved = false,
                history = state.editStack.history + EditStep(
                    id = UUID.randomUUID().toString(),
                    type = EditStepType.BACKGROUND_REMOVAL,
                    description = "Restored original background",
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
            rerenderNonDestructivePreview(updated)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingImage = true, nonBlockingMessage = null) }
            val cropOnlyStack = state.editStack.copy(backgroundRemoved = false)
            val croppedPath = bitmapProcessor.renderNonDestructiveEdit(
                originalPath = originalPath,
                editStack = cropOnlyStack,
                sessionId = "${sessionId}_seg_src",
            )
            val bitmap = bitmapProcessor.loadProcessingBitmap(croppedPath)
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isProcessingImage = false,
                        nonBlockingMessage = "Could not load preview for segmentation; continuing crop-only.",
                    )
                }
                return@launch
            }

            val outcome = try {
                subjectSegmentationGateway.segmentForegroundToMask(
                    bitmap = bitmap,
                    maskOutFile = bitmapProcessor.maskFile(sessionId),
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            when (outcome) {
                is SegmentationOutcome.Success -> {
                    val updated = state.editStack.copy(
                        backgroundRemoved = true,
                        segmentationMaskPath = outcome.maskFilePath,
                        history = state.editStack.history + EditStep(
                            id = UUID.randomUUID().toString(),
                            type = EditStepType.BACKGROUND_REMOVAL,
                            description = "ML Kit Subject Segmentation",
                            timestampMillis = System.currentTimeMillis(),
                        ),
                    )
                    rerenderNonDestructivePreview(updated)
                }

                is SegmentationOutcome.ModelNotDownloaded -> {
                    _uiState.update {
                        it.copy(
                            isProcessingImage = false,
                            segmentationModelState = SegmentationModelState.NotDownloaded,
                            nonBlockingMessage = "${outcome.reason} Crop-only editing remains available.",
                        )
                    }
                }

                is SegmentationOutcome.FailedFallbackToCropOnly -> {
                    _uiState.update {
                        it.copy(
                            isProcessingImage = false,
                            nonBlockingMessage = outcome.reason,
                        )
                    }
                }
            }
        }
    }

    fun resetToOriginal() {
        val original = _uiState.value.originalImagePath
        val cleared = EditStack()
        persistEditStack(cleared)
        savedStateHandle[KEY_WORKING_PATH] = original
        _uiState.update {
            it.copy(
                workingPreviewPath = original,
                nonBlockingMessage = "Restored non-destructive original image.",
            )
        }
    }

    private fun rerenderNonDestructivePreview(newStack: EditStack) {
        val original = _uiState.value.originalImagePath ?: return
        persistEditStack(newStack)
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingImage = true) }
            runCatching {
                bitmapProcessor.renderNonDestructiveEdit(
                    originalPath = original,
                    editStack = newStack,
                    sessionId = sessionId,
                )
            }.onSuccess { renderedPath ->
                savedStateHandle[KEY_WORKING_PATH] = renderedPath
                _uiState.update {
                    it.copy(
                        isProcessingImage = false,
                        workingPreviewPath = renderedPath,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingImage = false,
                        nonBlockingMessage = err.message ?: "Failed to render edit stack",
                    )
                }
            }
        }
    }

    fun analyzeCurrentImage() {
        val targetPath = _uiState.value.workingPreviewPath ?: _uiState.value.originalImagePath ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingImage = true, nonBlockingMessage = null) }
            runCatching {
                val bitmap = bitmapProcessor.loadProcessingBitmap(targetPath)
                    ?: error("Unable to load shade cart image")
                try {
                    val ocr = textRecognitionGateway.recognizeShadeCart(bitmap)
                    val detected = swatchDetector.detectSwatches(
                        bitmap = bitmap,
                        numericLabels = ocr.numericSwatchLabels,
                    )
                    Pair(ocr, detected)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }.onSuccess { (ocr, detected) ->
                val editableSwatches = detected.map { d ->
                    DetectedSwatchEditable(
                        id = UUID.randomUUID().toString(),
                        swatchNumber = d.swatchNumber,
                        colorHex = d.colorHex,
                        colorName = d.colorName,
                        bboxLeft = d.outerBounds.left,
                        bboxTop = d.outerBounds.top,
                        bboxRight = d.outerBounds.right,
                        bboxBottom = d.outerBounds.bottom,
                    )
                }
                val newName = ocr.header.fabricName.ifBlank { _uiState.value.fabricName }
                val newCode = ocr.header.fabricCode.ifBlank { _uiState.value.fabricCode }
                val newWeight = ocr.header.weightGsm?.toString() ?: _uiState.value.weightGsmText
                val newWidth = ocr.header.widthCm?.toString() ?: _uiState.value.widthCmText

                savedStateHandle[KEY_FABRIC_NAME] = newName
                savedStateHandle[KEY_FABRIC_CODE] = newCode
                savedStateHandle[KEY_WEIGHT_GSM] = newWeight
                savedStateHandle[KEY_WIDTH_CM] = newWidth
                savedStateHandle[KEY_RAW_OCR] = ocr.header.rawHeaderText
                savedStateHandle[KEY_ANALYZED] = true
                persistSwatches(editableSwatches)

                _uiState.update {
                    it.copy(
                        isProcessingImage = false,
                        hasRunInitialAnalysis = true,
                        fabricName = newName,
                        fabricCode = newCode,
                        weightGsmText = newWeight,
                        widthCmText = newWidth,
                        rawHeaderOcr = ocr.header.rawHeaderText,
                        detectedSwatches = editableSwatches,
                        nonBlockingMessage = if (editableSwatches.isEmpty()) {
                            "No swatch regions detected automatically. You can save and add swatches manually."
                        } else {
                            "Detected ${editableSwatches.size} swatches across two-column layout."
                        },
                    )
                }
            }.onFailure { err ->
                savedStateHandle[KEY_ANALYZED] = true
                _uiState.update {
                    it.copy(
                        isProcessingImage = false,
                        hasRunInitialAnalysis = true,
                        nonBlockingMessage = "OCR/Swatch analysis issue: ${err.message ?: "unknown"}. You can edit fields manually.",
                    )
                }
            }
        }
    }

    fun onFabricNameChanged(value: String) {
        savedStateHandle[KEY_FABRIC_NAME] = value
        _uiState.update { it.copy(fabricName = value) }
    }

    fun onFabricCodeChanged(value: String) {
        savedStateHandle[KEY_FABRIC_CODE] = value
        _uiState.update { it.copy(fabricCode = value) }
    }

    fun onWeightGsmChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        savedStateHandle[KEY_WEIGHT_GSM] = digitsOnly
        _uiState.update { it.copy(weightGsmText = digitsOnly) }
    }

    fun onWidthCmChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        savedStateHandle[KEY_WIDTH_CM] = digitsOnly
        _uiState.update { it.copy(widthCmText = digitsOnly) }
    }

    fun onSwatchNumberEdited(swatchId: String, newNumber: String) {
        val updated = _uiState.value.detectedSwatches.map {
            if (it.id == swatchId) it.copy(swatchNumber = newNumber) else it
        }
        persistSwatches(updated)
        _uiState.update { it.copy(detectedSwatches = updated) }
    }

    fun saveShadeCart(onSaved: (cartId: String) -> Unit) {
        val state = _uiState.value
        val weight = state.weightGsmText.toIntOrNull() ?: 180
        val width = state.widthCmText.toIntOrNull() ?: 150
        val cleanName = state.fabricName.ifBlank { "Untitled Fabric" }
        val cleanCode = state.fabricCode.ifBlank { "CART-${sessionId.take(6).uppercase()}" }
        val stackJson = json.encodeToString(state.editStack)

        val swatchInputs = state.detectedSwatches.mapIndexed { idx, sw ->
            SwatchInput(
                id = sw.id,
                fabricTypeId = state.selectedFabricTypeId ?: "",
                swatchNumber = sw.swatchNumber.ifBlank { "${idx + 1}" },
                colorHex = sw.colorHex,
                colorName = sw.colorName,
                positionOrder = idx,
                bboxLeft = sw.bboxLeft,
                bboxTop = sw.bboxTop,
                bboxRight = sw.bboxRight,
                bboxBottom = sw.bboxBottom,
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (state.existingCartId != null) {
                saveShadeCartUseCase(
                    cartId = state.existingCartId,
                    expectedVersion = state.expectedVersion,
                    fabricTypeId = state.selectedFabricTypeId,
                    fabricName = cleanName,
                    fabricCode = cleanCode,
                    weightGsm = weight,
                    widthCm = width,
                    originalImagePath = state.originalImagePath,
                    editedImagePath = state.workingPreviewPath,
                    editStackJson = stackJson,
                    headerOcrRaw = state.rawHeaderOcr,
                    swatches = swatchInputs,
                )
            } else {
                createOrGetShadeCart(
                    ShadeCartCreateInput(
                        fabricTypeId = state.selectedFabricTypeId,
                        fabricName = cleanName,
                        fabricCode = cleanCode,
                        weightGsm = weight,
                        widthCm = width,
                        originalImagePath = state.originalImagePath,
                        editedImagePath = state.workingPreviewPath,
                        editStackJson = stackJson,
                        headerOcrRaw = state.rawHeaderOcr,
                        swatches = swatchInputs,
                    ),
                )
            }

            when (result) {
                is ShadeCartSaveOutcome.Saved -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved(result.cartId)
                }

                is ShadeCartSaveOutcome.Conflict -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            nonBlockingMessage = "Conflict detected while saving; resolve in the banner above.",
                        )
                    }
                    onSaved(result.conflict.cartId)
                }

                is ShadeCartSaveOutcome.NotFound -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            nonBlockingMessage = "Shade cart record no longer exists.",
                        )
                    }
                }
            }
        }
    }

    private fun persistEditStack(stack: EditStack) {
        savedStateHandle[KEY_EDIT_STACK] = json.encodeToString(stack)
        _uiState.update { it.copy(editStack = stack) }
    }

    private fun persistSwatches(swatches: List<DetectedSwatchEditable>) {
        savedStateHandle[KEY_SWATCHES_JSON] = json.encodeToString(swatches)
    }
}
