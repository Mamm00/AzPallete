package com.alazhartex.shadecart.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.SwatchModel
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.model.WarehouseRollModel
import com.alazhartex.shadecart.domain.validation.HexColorValidator
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Stateless Compose card displaying a fabric color swatch modeled after Al AzharTex physical
 * shade-card cuts: pinked zigzag perimeter, swatch badge, validated HEX label, and clickable
 * warehouse roll inventory chip
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 1.2, 4.4, & 5.2).
 */
@Composable
fun PinkedSwatchCard(
    item: SwatchModel,
    onRollClick: (SwatchModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roll: WarehouseRollModel? = item.linkedRoll
    val argb = HexColorValidator.parseArgbOrNull(item.colorHex) ?: 0xFF334155.toInt()
    val swatchColor = Color(argb)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("swatch_chip_${item.swatchNumber}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PinkedFabricCut(
                    fillColor = swatchColor,
                    contentDescription = "Swatch #${item.swatchNumber} ${item.colorHex}",
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = "#${item.swatchNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.colorName.ifBlank { "Shade #${item.swatchNumber}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.colorHex.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            AssistChip(
                onClick = { onRollClick(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("roll_link_${item.swatchNumber}"),
                leadingIcon = {
                    Icon(
                        imageVector = if (roll != null) Icons.Rounded.Inventory2 else Icons.Rounded.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        text = if (roll != null) {
                            "${roll.rollCode} · ${roll.warehouseShed} · ${roll.lengthMeters.toInt()}m"
                        } else {
                            "No Roll linked · Tap to link"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * Custom Canvas composable rendering a pinked-scissors fabric border around [fillColor].
 */
@Composable
fun PinkedFabricCut(
    fillColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 10f / 7f,
) {
    Canvas(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val amp = size.height * 0.06f
        val tooth = size.width / 13f
        val path = buildPinkedPath(size.width, size.height, amp, tooth)
        drawPath(path = path, color = fillColor)
        drawPath(
            path = path,
            color = Color.Black.copy(alpha = 0.22f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun buildPinkedPath(width: Float, height: Float, amp: Float, tooth: Float): Path {
    val bodyLeft = amp
    val bodyRight = width - amp
    val bodyTop = amp
    val bodyBottom = height - amp
    val p = Path()

    p.moveTo(bodyLeft, bodyTop)

    var x = bodyLeft
    while (x < bodyRight - 0.5f) {
        val tipX = min(x + tooth / 2f, bodyRight)
        val endX = min(x + tooth, bodyRight)
        p.lineTo(tipX, 0f)
        p.lineTo(endX, bodyTop)
        x = endX
    }

    var y = bodyTop
    while (y < bodyBottom - 0.5f) {
        val tipY = min(y + tooth / 2f, bodyBottom)
        val endY = min(y + tooth, bodyBottom)
        p.lineTo(width, tipY)
        p.lineTo(bodyRight, endY)
        y = endY
    }

    x = bodyRight
    while (x > bodyLeft + 0.5f) {
        val tipX = max(x - tooth / 2f, bodyLeft)
        val endX = max(x - tooth, bodyLeft)
        p.lineTo(tipX, height)
        p.lineTo(endX, bodyBottom)
        x = endX
    }

    y = bodyBottom
    while (y > bodyTop + 0.5f) {
        val tipY = max(y - tooth / 2f, bodyTop)
        val endY = max(y - tooth, bodyTop)
        p.lineTo(0f, tipY)
        p.lineTo(bodyLeft, endY)
        y = endY
    }

    p.close()
    return p
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PinkedSwatchCardPreview() {
    AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
        PinkedSwatchCard(
            item = SwatchModel(
                id = "sw-5",
                shadeCartId = "cart-1",
                fabricTypeId = "fab-silk-180",
                rollId = "roll-slk-5",
                linkedRoll = WarehouseRollModel(
                    id = "roll-slk-5",
                    rollCode = "RL-SLK-005",
                    fabricTypeId = "fab-silk-180",
                    shadeLabel = "5",
                    colorName = "Azhar Navy",
                    colorHex = "#102A71",
                    warehouseShed = "wh-alwa",
                    lengthMeters = 480.0,
                    status = RollStockStatus.IN_STOCK,
                ),
                swatchNumber = "5",
                colorHex = "#102A71",
                colorName = "Azhar Navy",
                positionOrder = 0,
            ),
            onRollClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
