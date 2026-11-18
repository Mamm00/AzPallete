package com.alazhartex.shadecart.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.BuildConfig
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.model.AccentPaletteSetting
import com.alazhartex.shadecart.domain.model.AppLanguageSetting
import com.alazhartex.shadecart.domain.model.AppRole
import com.alazhartex.shadecart.domain.model.AppSessionPreferences
import com.alazhartex.shadecart.domain.model.SyncQueueEntryModel
import com.alazhartex.shadecart.domain.model.SyncState
import com.alazhartex.shadecart.domain.model.ThemeModeSetting
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.domain.validation.HexColorValidator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    sessionPreferences: AppSessionPreferences = AppSessionPreferences(),
    activeCalibrationName: String? = null,
    onSelectLanguage: (AppLanguageSetting) -> Unit = {},
    onSelectRole: (AppRole) -> Unit = {},
    onToggleGloveMode: (Boolean) -> Unit = {},
    onOpenCalibration: () -> Unit = {},
    onActivateBuyerMode: (pin: String, companyName: String) -> Unit = { _, _ -> },
    onExportBackup: (passphrase: String) -> Unit = {},
    onRestoreBackup: (passphrase: String) -> Unit = {},
    onOpenNearbyShare: () -> Unit = {},
) {
    var showBuyerPinSetup by remember { mutableStateOf(false) }
    var backupActionMode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            BrandLogoBadge(logoSize = 40.dp)

            Spacer(modifier = Modifier.height(14.dp))

            // D3: Language Switcher
            Text(
                text = stringResource(R.string.language_setting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguageSetting.entries.forEach { lang ->
                    FilterChip(
                        selected = sessionPreferences.language == lang,
                        onClick = { onSelectLanguage(lang) },
                        label = { Text(lang.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // E3: RoleGate Selector
            Text(
                text = stringResource(R.string.operator_role_setting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppRole.entries.forEach { role ->
                    FilterChip(
                        selected = sessionPreferences.role == role,
                        onClick = { onSelectRole(role) },
                        label = { Text(role.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // D4: Glove Mode & A1: ShadeLock Calibration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.glove_mode_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Enlarge buttons to ≥56dp for dye-house gloves",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sessionPreferences.gloveMode,
                    onCheckedChange = onToggleGloveMode,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenCalibration,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(activeCalibrationName ?: "ShadeLock Calibrate")
                }
                Button(
                    onClick = { showBuyerPinSetup = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buyer Mode")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Accent Palette (Fallback / Override)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

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

            // C5a + C5b: Encrypted Backup & Nearby Sync
            Text(
                text = "Offline Backup & Nearby P2P Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            sessionPreferences.lastBackupSizeBytes?.let { bytes ->
                Text(
                    text = "Last encrypted backup: ${bytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { backupActionMode = "EXPORT" },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Backup ZIP")
                }
                OutlinedButton(
                    onClick = { backupActionMode = "RESTORE" },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }
                OutlinedButton(
                    onClick = onOpenNearbyShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nearby")
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

    if (showBuyerPinSetup) {
        var pin by remember { mutableStateOf("1234") }
        var company by remember { mutableStateOf("Al AzharTex Partner") }
        AlertDialog(
            onDismissRequest = { showBuyerPinSetup = false },
            title = { Text("Enter Buyer Showcase Mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hides internal warehouse sheds, costs, and edit tools until unlocked with this 4-digit PIN.")
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Buyer / Company Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("4-Digit Unlock PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onActivateBuyerMode(pin.padStart(4, '0'), company)
                        showBuyerPinSetup = false
                        onDismiss()
                    },
                ) { Text("Lock & Showcase") }
            },
            dismissButton = { TextButton(onClick = { showBuyerPinSetup = false }) { Text("Cancel") } },
        )
    }

    backupActionMode?.let { mode ->
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { backupActionMode = null },
            title = { Text(if (mode == "EXPORT") "Encrypt Backup ZIP" else "Restore Encrypted Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AES-256-GCM encrypted archive with SHA-256 checksum.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (min 4 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = passphrase.length >= 4,
                    onClick = {
                        if (mode == "EXPORT") onExportBackup(passphrase) else onRestoreBackup(passphrase)
                        backupActionMode = null
                    },
                ) { Text(if (mode == "EXPORT") "Choose Folder" else "Choose Backup File") }
            },
            dismissButton = { TextButton(onClick = { backupActionMode = null }) { Text("Cancel") } },
        )
    }
}
