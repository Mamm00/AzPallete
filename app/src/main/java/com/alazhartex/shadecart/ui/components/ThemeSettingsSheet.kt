package com.alazhartex.shadecart.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.BuildConfig
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.validation.HexColorValidator

/**
 * Stateless Material 3 [ModalBottomSheet] exposing DataStore-backed appearance preferences and
 * background sync queue diagnostics.
 *
 * The QA conflict-simulation button is gated behind [showDebugSyncTools] (`BuildConfig.DEBUG`) so
 * release APK builds hide debug-only test controls (Pyre, *Android Development with Jetpack Compose
 * & Kotlin*, Chapter 5.4, 10.4, & 12.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeAndSyncSettingsSheet(
    preferences: ThemeSettings,
    syncQueue: List<SyncQueueEntryModel>,
    isConflictArmed: Boolean,
    onDismiss: () -> Unit,
    onSelectThemeMode: (ThemeModeSetting) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectAccentPalette: (AccentPaletteSetting) -> Unit,
    onSyncNow: () -> Unit,
    onArmSimulatedConflict: () -> Unit,
    showDebugSyncTools: Boolean = BuildConfig.DEBUG,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            BrandLogoBadge(logoSize = 40.dp)

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Appearance & Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeModeSetting.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.themeMode == mode,
                        onClick = { onSelectThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("theme_mode_${mode.name}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dynamic Color (Material You)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Follows device wallpaper on Android 12+ (disable to use custom brand accent)"
                        } else {
                            "Available on Android 12 (API 31) and above — using accent palette"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.useDynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    modifier = Modifier.testTag("dynamic_color_switch"),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Accent Palette (Fallback / Override)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            AccentPaletteSetting.entries.forEach { palette ->
                val selected = preferences.accentPalette == palette
                val primaryColor = Color(HexColorValidator.parseArgbOrNull(palette.primaryHex) ?: 0xFF102A71.toInt())
                val secondaryColor = Color(HexColorValidator.parseArgbOrNull(palette.secondaryHex) ?: 0xFFD90A0A.toInt())

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelectAccentPalette(palette) }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag("accent_palette_${palette.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(secondaryColor),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = palette.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Sync Queue & Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))

            val pendingCount = syncQueue.count { it.status == SyncState.PENDING }
            val syncedCount = syncQueue.count { it.status == SyncState.SYNCED }
            val conflictCount = syncQueue.count { it.status == SyncState.CONFLICT }

            Text(
                text = "Pending: $pendingCount · Synced: $syncedCount · Conflicts: $conflictCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Now")
                }
                if (showDebugSyncTools) {
                    OutlinedButton(
                        onClick = onArmSimulatedConflict,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_conflict_debug_button"),
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isConflictArmed) "Armed" else "Simulate Conflict")
                    }
                }
            }
        }
    }
}
