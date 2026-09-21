package dev.opennotes.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Decodes a local image file, downsampled to [maxDimension] to keep memory bounded. */
@Composable
fun LocalImage(
    path: String?,
    modifier: Modifier = Modifier,
    maxDimension: Int = 1280,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxDimension) {
        value = withContext(Dispatchers.IO) {
            path?.let { decodeSampled(it, maxDimension) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        }
    }
}

private fun decodeSampled(path: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
