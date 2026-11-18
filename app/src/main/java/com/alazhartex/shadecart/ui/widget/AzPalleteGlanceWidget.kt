package com.alazhartex.shadecart.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alazhartex.shadecart.MainActivity
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDatabaseEntryPoint {
    fun database(): AlAzharTexDatabase
}

data class WidgetCartSummary(
    val id: String,
    val fabricName: String,
    val fabricCode: String,
    val swatchCount: Int,
)

/**
 * D4: Jetpack Glance home screen widget displaying the 3 latest shade carts, low-stock count,
 * and 1-tap deep link shortcuts into AzPallete.
 */
class AzPalleteGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (recentCarts, lowStockCount) = withContext(Dispatchers.IO) {
            runCatching {
                val db = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetDatabaseEntryPoint::class.java,
                ).database()
                val carts = db.shadeCartDao().observeAllWithDetails().first().take(3).map {
                    WidgetCartSummary(
                        id = it.cart.id,
                        fabricName = it.cart.fabricName,
                        fabricCode = it.cart.fabricCode,
                        swatchCount = it.swatches.size,
                    )
                }
                val lowRolls = db.rollDao().getLowStockRolls().size
                carts to lowRolls
            }.getOrDefault(emptyList<WidgetCartSummary>() to 0)
        }

        provideContent {
            GlanceTheme {
                WidgetBody(
                    context = context,
                    carts = recentCarts,
                    lowStockCount = lowStockCount,
                )
            }
        }
    }

    @Composable
    private fun WidgetBody(
        context: Context,
        carts: List<WidgetCartSummary>,
        lowStockCount: Int,
    ) {
        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFFFAF9F5))
                .padding(12.dp)
                .cornerRadius(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AzPallete · Mill",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF102A71)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (lowStockCount > 0) {
                    Text(
                        text = "$lowStockCount Low",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFD90A0A)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }

            if (carts.isEmpty()) {
                Text(
                    text = "Tap to capture your first shade cart",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF475569)),
                        fontSize = 12.sp,
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            } else {
                carts.forEach { cart ->
                    val deepIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("azpallete://cart/${cart.id}"),
                        context,
                        MainActivity::class.java,
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFFFFFFFF))
                            .cornerRadius(8.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = cart.fabricName,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF171B26)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = "${cart.fabricCode} · ${cart.swatchCount} shades",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF64748B)),
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        androidx.glance.Button(
                            text = "Open",
                            onClick = actionStartActivity(deepIntent),
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))
            androidx.glance.Button(
                text = "Open AzPallete",
                onClick = actionStartActivity(openHomeIntent),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}

class AzPalleteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AzPalleteGlanceWidget()
}
