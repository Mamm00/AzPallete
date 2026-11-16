package com.alazhartex.shadecart.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor

/**
 * Loads an `RGB_565` downsampled preview bitmap off the main thread without keeping bitmaps
 * inside ViewModel state.
 */
@Composable
fun Rgb565PreviewImage(
    imagePath: String?,
    bitmapProcessor: BitmapProcessor,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val loadedBitmap by produceState<Bitmap?>(initialValue = null, key1 = imagePath) {
        value = if (imagePath.isNullOrBlank()) {
            null
        } else {
            bitmapProcessor.loadRgb565Preview(imagePath)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = loadedBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!imagePath.isNullOrBlank()) {
            CircularProgressIndicator()
        } else {
            Text(
                text = "No preview available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
