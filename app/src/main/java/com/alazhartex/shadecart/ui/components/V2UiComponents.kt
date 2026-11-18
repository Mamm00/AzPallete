package com.alazhartex.shadecart.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.data.model.NormalizedRect
import com.alazhartex.shadecart.domain.imaging.ColorCalibrator
import com.alazhartex.shadecart.domain.imaging.ColorScience
import com.alazhartex.shadecart.domain.model.CalibrationProfile
import com.alazhartex.shadecart.domain.model.DrapeLightingMode
import com.alazhartex.shadecart.domain.model.DrapeScene
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.WeaveTag
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.ui.screens.edit.DetectedSwatchEditable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * D1: Lightweight infinite shimmer loading placeholder with zero external dependencies.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(offsetX, 0f),
                    end = Offset(offsetX + 280f, 180f),
                ),
            ),
    )
}

/**
 * D4: Color-blind accessibility texture cue drawn over swatch chips alongside semantic ΔE label.
 */
@Composable
fun ColorBlindPatternDot(
    colorHex: String,
    patternSeed: Int,
    modifier: Modifier = Modifier,
) {
    val argb = HexColorValidator.parseArgbOrNull(colorHex) ?: 0xFF334155.toInt()
    Canvas(modifier = modifier.size(18.dp)) {
        drawCircle(color = Color(argb))
        val lineColor = Color.White.copy(alpha = 0.65f)
        when (patternSeed % 3) {
            0 -> drawLine(lineColor, Offset(3f, size.height - 3f), Offset(size.width - 3f, 3f), strokeWidth = 1.5f)
            1 -> drawCircle(lineColor, radius = 2.5f)
            else -> {
                drawLine(lineColor, Offset(4f, size.height / 2f), Offset(size.width - 4f, size.height / 2f), strokeWidth = 1.5f)
                drawLine(lineColor, Offset(size.width / 2f, 4f), Offset(size.width / 2f, size.height - 4f), strokeWidth = 1.5f)
            }
        }
    }
}

/**
 * A1: Reads live ambient light sensor (`Sensor.TYPE_LIGHT`) cleanly via `DisposableEffect`.
 * Emits `-1f` on devices without a light sensor.
 */
@Composable
fun rememberAmbientLux(): Float {
    val context = LocalContext.current
    var lux by remember { mutableFloatStateOf(-1f) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (manager == null || sensor == null) {
            lux = -1f
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    lux = event.values.firstOrNull() ?: -1f
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { manager.unregisterListener(listener) }
        }
    }
    return lux
}

@Composable
fun AmbientLuxChip(
    lux: Float,
    activeProfileName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = lux >= 0f && (lux < 150f || lux > 2000f)
    val label = when {
        lux < 0f && activeProfileName != null -> "ShadeLock: $activeProfileName ✓"
        lux < 0f -> "ShadeLock: Uncalibrated"
        warning -> "⚠ ${lux.toInt()} lux (Adjust light)"
        activeProfileName != null -> "${lux.toInt()} lux · $activeProfileName ✓"
        else -> "${lux.toInt()} lux · Tap to Calibrate"
    }
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = if (activeProfileName != null) Icons.Rounded.CheckCircle else Icons.Rounded.WbSunny,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        label = { Text(label, maxLines = 1) },
        modifier = modifier.testTag("ambient_lux_chip"),
    )
}

/**
 * A1: Calibration sheet allowing the user to calibrate with an 18% gray card / white paper
 * or Al AzharTex Navy target, switch saved calibration profiles, or record ambient lux.
 */
@Composable
fun ShadeLockCalibrationDialog(
    profiles: List<CalibrationProfile>,
    activeProfileId: String?,
    currentAmbientLux: Float,
    sampledWhiteRgb: Triple<Int, Int, Int>? = null,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveNewProfile: (CalibrationProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var profileName by remember { mutableStateOf("Showroom D65") }
    var targetPreset by remember { mutableStateOf("WHITE_PAPER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shadelock_calibration)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (currentAmbientLux >= 0f) {
                        "Current light sensor: ${currentAmbientLux.toInt()} lux (ideal 200–1500 lux)"
                    } else {
                        "Light sensor unavailable — pick a standard lighting profile or calibrate from neutral white patch."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (profiles.isNotEmpty()) {
                    Text(
                        text = "Saved Profiles",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    profiles.forEach { prof ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = prof.id == activeProfileId,
                                onClick = { onSelectProfile(prof.id) },
                                label = { Text("${prof.name} (G ${String.format(Locale.US, "%.2f/%.2f/%.2f", prof.gainR, prof.gainG, prof.gainB)})") },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDeleteProfile(prof.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete profile")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("New Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetPreset == "WHITE_PAPER",
                        onClick = { targetPreset = "WHITE_PAPER" },
                        label = { Text("A4 White Paper") },
                    )
                    FilterChip(
                        selected = targetPreset == "DAYLIGHT_LED",
                        onClick = { targetPreset = "DAYLIGHT_LED" },
                        label = { Text("Showroom LED") },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sample = sampledWhiteRgb ?: when (targetPreset) {
                        "DAYLIGHT_LED" -> Triple(248, 240, 224)
                        else -> Triple(246, 244, 240)
                    }
                    val profile = ColorCalibrator.calibrateFromWhiteSample(
                        profileId = "cal-${UUID.randomUUID().toString().take(8)}",
                        name = profileName.ifBlank { "Custom Profile" },
                        measuredWhiteRgb = sample,
                        ambientLux = currentAmbientLux,
                    )
                    onSaveNewProfile(profile)
                    onDismiss()
                },
            ) {
                Text("Save & Activate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * A4: Interactive Swatch Overlay Canvas over the digitized shade cart bitmap.
 * Allows dragging existing swatch bounding boxes, deleting boxes, adding a new box,
 * and displaying live `#HEX` + CIE `L*a*b*` feedback on touch.
 */
@Composable
fun SwatchOverlayCanvas(
    bitmap: Bitmap?,
    swatches: List<DetectedSwatchEditable>,
    selectedSwatchId: String?,
    onSelectSwatch: (String) -> Unit,
    onMoveSwatchBox: (swatchId: String, newBounds: NormalizedRect) -> Unit,
    onAddSwatchBox: () -> Unit,
    onDeleteSwatchBox: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1).toFloat() }
                        ?.coerceIn(0.6f, 1.8f) ?: 1.33f,
                )
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("swatch_overlay_canvas"),
        ) {
            if (bitmap != null) {
                val imgBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(swatches, selectedSwatchId) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val nx = startOffset.x / w
                                    val ny = startOffset.y / h
                                    val hit = swatches.firstOrNull { sw ->
                                        nx in sw.bboxLeft..sw.bboxRight && ny in sw.bboxTop..sw.bboxBottom
                                    }
                                    if (hit != null) onSelectSwatch(hit.id)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val target = swatches.firstOrNull { it.id == selectedSwatchId } ?: return@detectDragGestures
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val dx = dragAmount.x / w
                                    val dy = dragAmount.y / h
                                    val bw = (target.bboxRight - target.bboxLeft).coerceAtLeast(0.05f)
                                    val bh = (target.bboxBottom - target.bboxTop).coerceAtLeast(0.04f)
                                    val newL = (target.bboxLeft + dx).coerceIn(0f, 1f - bw)
                                    val newT = (target.bboxTop + dy).coerceIn(0f, 1f - bh)
                                    onMoveSwatchBox(
                                        target.id,
                                        NormalizedRect(newL, newT, newL + bw, newT + bh),
                                    )
                                },
                            )
                        },
                ) {
                    drawImage(
                        image = imgBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                    swatches.forEach { sw ->
                        val isSelected = sw.id == selectedSwatchId
                        val rectTopLeft = Offset(sw.bboxLeft * size.width, sw.bboxTop * size.height)
                        val rectSize = Size(
                            (sw.bboxRight - sw.bboxLeft) * size.width,
                            (sw.bboxBottom - sw.bboxTop) * size.height,
                        )
                        val hexColor = Color(HexColorValidator.parseArgbOrNull(sw.colorHex) ?: 0xFF102A71.toInt())
                        drawRect(
                            color = hexColor.copy(alpha = if (isSelected) 0.25f else 0.12f),
                            topLeft = rectTopLeft,
                            size = rectSize,
                        )
                        drawRect(
                            color = if (isSelected) Color(0xFFD90A0A) else Color(0xFF102A71),
                            topLeft = rectTopLeft,
                            size = rectSize,
                            style = Stroke(
                                width = if (isSelected) 3.dp.toPx() else 1.8.dp.toPx(),
                                pathEffect = if (!isSelected) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null,
                            ),
                        )
                    }
                }
            } else {
                Text(
                    text = "Load an image to interactively move & resample swatch boxes",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onAddSwatchBox, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Box")
            }
            if (selectedSwatchId != null) {
                OutlinedButton(onClick = { onDeleteSwatchBox(selectedSwatchId) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove Box", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove Selected")
                }
            }
        }
    }
}

/**
 * B3: 2.5D Fabric Drape Preview Canvas rendering high-res grayscale lighting/fold assets via
 * `BlendMode.Modulate` + realistic weave sheen + lighting temperature simulation + optional A/B split.
 */
@Composable
fun DrapePreviewDialog(
    primarySwatch: SwatchModel,
    compareSwatch: SwatchModel? = null,
    allSwatches: List<SwatchModel> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var scene by remember { mutableStateOf(DrapeScene.CURTAIN_FOLD) }
    var lighting by remember { mutableStateOf(DrapeLightingMode.DAYLIGHT_6500K) }
    var splitEnabled by remember { mutableStateOf(compareSwatch != null) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    var secondSwatch by remember { mutableStateOf(compareSwatch ?: allSwatches.firstOrNull { it.id != primarySwatch.id }) }

    val grayscaleBitmap by produceState<Bitmap?>(initialValue = null, key1 = scene) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(scene.assetPath).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drape_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrapeScene.entries.forEach { item ->
                        FilterChip(
                            selected = scene == item,
                            onClick = { scene = item },
                            label = { Text(item.label, maxLines = 1) },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(0xFFEAE8E0)),
                ) {
                    val bmp = grayscaleBitmap
                    if (bmp != null) {
                        val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                        val primaryColor = Color(
                            HexColorValidator.parseArgbOrNull(primarySwatch.colorHex) ?: 0xFF102A71.toInt(),
                        )
                        val compareColor = Color(
                            HexColorValidator.parseArgbOrNull(secondSwatch?.colorHex ?: primarySwatch.colorHex)
                                ?: 0xFFCD1820.toInt(),
                        )
                        val lightMatrix = remember(lighting) {
                            when (lighting) {
                                DrapeLightingMode.DAYLIGHT_6500K -> ColorMatrix()
                                DrapeLightingMode.INCANDESCENT_2800K -> ColorMatrix(
                                    floatArrayOf(
                                        1.10f, 0f, 0f, 0f, 0f,
                                        0f, 0.96f, 0f, 0f, 0f,
                                        0f, 0f, 0.78f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                )
                                DrapeLightingMode.STORE_LED_4000K -> ColorMatrix(
                                    floatArrayOf(
                                        1.02f, 0f, 0f, 0f, 0f,
                                        0f, 1.03f, 0f, 0f, 0f,
                                        0f, 0f, 0.94f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                )
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                        ) {
                            val splitX = if (splitEnabled && secondSwatch != null) size.width * splitRatio else size.width
                            drawImage(
                                image = imgBmp,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                colorFilter = ColorFilter.colorMatrix(lightMatrix),
                            )
                            drawRect(
                                color = primaryColor,
                                topLeft = Offset.Zero,
                                size = Size(splitX, size.height),
                                blendMode = BlendMode.Modulate,
                            )
                            if (splitEnabled && secondSwatch != null) {
                                drawRect(
                                    color = compareColor,
                                    topLeft = Offset(splitX, 0f),
                                    size = Size(size.width - splitX, size.height),
                                    blendMode = BlendMode.Modulate,
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(splitX, 0f),
                                    end = Offset(splitX, size.height),
                                    strokeWidth = 3f,
                                )
                            }
                            if (primarySwatch.weaveTag == WeaveTag.SATIN || primarySwatch.sheenScore > 0.2f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                                    ),
                                )
                            }
                        }
                    } else {
                        ShimmerBox(modifier = Modifier.fillMaxSize())
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrapeLightingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = lighting == mode,
                            onClick = { lighting = mode },
                            label = { Text(mode.label, maxLines = 1) },
                        )
                    }
                }

                if (allSwatches.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilterChip(
                            selected = splitEnabled,
                            onClick = { splitEnabled = !splitEnabled },
                            label = { Text("A/B Compare") },
                        )
                        if (splitEnabled && secondSwatch != null) {
                            Text(
                                text = "#${primarySwatch.swatchNumber} vs #${secondSwatch!!.swatchNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (splitEnabled) {
                        Slider(
                            value = splitRatio,
                            onValueChange = { splitRatio = it },
                            valueRange = 0.15f..0.85f,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
