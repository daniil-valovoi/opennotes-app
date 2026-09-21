package dev.opennotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Pinch-to-zoom viewer for imported handwriting pages and images. */
@Composable
fun ImageViewerScreen(path: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014)),
    ) {
        LocalImage(
            path = path,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (next > 1f) offset + pan else Offset.Zero
                        scale = next
                    }
                },
            maxDimension = 3000,
            contentScale = ContentScale.Fit,
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0x88000000), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
