package com.alazhartex.shadecart.ui.components

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
import androidx.compose.ui.unit.dp
import com.alazhartex.shadecart.data.repo.ConflictResolution
import com.alazhartex.shadecart.data.repo.ConflictSource
import com.alazhartex.shadecart.data.repo.SyncConflict

@Composable
fun ConflictBanner(
    conflict: SyncConflict,
    onResolve: (cartId: String, resolution: ConflictResolution) -> Unit,
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
                ConflictSource.CONCURRENT_LOCAL_WRITE ->
                    "A concurrent write modified this cart (local v${conflict.localSnapshot.version} vs stored v${conflict.remoteSnapshot.version}). Choose which changes to keep."
                ConflictSource.REMOTE_SYNC_VERSION_MISMATCH ->
                    "Remote sync has v${conflict.remoteSnapshot.version} while your local cart is v${conflict.localSnapshot.version} (${conflict.localSnapshot.swatches.size} vs ${conflict.remoteSnapshot.swatches.size} swatches)."
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
                    onClick = { onResolve(conflict.cartId, ConflictResolution.KEEP_REMOTE) },
                    modifier = Modifier.testTag("conflict_keep_remote"),
                ) {
                    Text("Use Remote")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onResolve(conflict.cartId, ConflictResolution.KEEP_LOCAL) },
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
