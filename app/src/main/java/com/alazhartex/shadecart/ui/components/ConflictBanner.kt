package com.alazhartex.shadecart.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.domain.model.ConflictDecision
import com.alazhartex.shadecart.domain.model.ConflictOrigin
import com.alazhartex.shadecart.domain.model.SyncConflictModel
import com.alazhartex.shadecart.domain.model.ThemeSettings
import com.alazhartex.shadecart.ui.theme.AlAzharTexTheme

/**
 * Stateless warning banner surfacing unresolved optimistic-locking or remote HTTP 409 sync
 * conflicts so operators choose between [ConflictDecision.KEEP_LOCAL] and
 * [ConflictDecision.KEEP_REMOTE] without losing edits
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 1.2 & Chapter 8.3).
 */
@Composable
fun ConflictBanner(
    conflict: SyncConflictModel,
    onResolve: (cartId: String, decision: ConflictDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("conflict_banner"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Version Conflict · ${conflict.fabricName} (${conflict.fabricCode})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            val reason = when (conflict.source) {
                ConflictOrigin.CONCURRENT_LOCAL_WRITE ->
                    "A concurrent write modified this cart (local v${conflict.localVersion} vs stored v${conflict.remoteVersion}). Choose which changes to keep."
                ConflictOrigin.REMOTE_SYNC_VERSION_MISMATCH ->
                    "Remote sync has v${conflict.remoteVersion} while your local cart is v${conflict.localVersion} (${conflict.localSwatchCount} vs ${conflict.remoteSwatchCount} swatches)."
            }
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { onResolve(conflict.cartId, ConflictDecision.KEEP_REMOTE) },
                    modifier = Modifier.testTag("conflict_keep_remote"),
                ) {
                    Text("Use Remote")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onResolve(conflict.cartId, ConflictDecision.KEEP_LOCAL) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("conflict_keep_local"),
                ) {
                    Text("Keep Local")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConflictBannerPreview() {
    AlAzharTexTheme(preferences = ThemeSettings(useDynamicColor = false)) {
        ConflictBanner(
            conflict = SyncConflictModel(
                cartId = "cart-1",
                fabricName = "Silk 180",
                fabricCode = "SLK-180",
                localVersion = 2L,
                remoteVersion = 3L,
                localSwatchCount = 12,
                remoteSwatchCount = 10,
                source = ConflictOrigin.REMOTE_SYNC_VERSION_MISMATCH,
                detectedAt = 0L,
            ),
            onResolve = { _, _ -> },
        )
    }
}
