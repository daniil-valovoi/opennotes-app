package dev.opennotes.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.opennotes.data.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private data class DoodleStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val eraser: Boolean,
)

private val PenColors = listOf(
    Color(0xFF1A1A1A),
    Color(0xFF1447FF),
    Color(0xFFD32F2F),
    Color(0xFF2E7D32),
    Color(0xFFF57C00),
    Color(0xFF7B1FA2),
)

/**
 * Full-screen ink canvas. Strokes are flattened into a PNG when the user taps Done.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DoodleScreen(
    noteId: Long,
    repository: NotesRepository,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var penColor by remember { mutableStateOf(PenColors.first()) }
    var penWidth by remember { mutableFloatStateOf(5f) }
    var eraser by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<DoodleStroke>() }
    val current = remember { mutableStateListOf<Offset>() }
    val scope = rememberCoroutineScope()

    val activeColor = if (eraser) Color.White else penColor
    val activeWidth = if (eraser) penWidth * 3f else penWidth

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doodle") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        enabled = strokes.isNotEmpty() && !saving && canvasSize.width > 0,
                        onClick = {
                            saving = true
                            val width = canvasSize.width
                            val height = canvasSize.height
                            val snapshot = strokes.toList()
                            scope.launch {
                                val bitmap = withContext(Dispatchers.Default) {
                                    flattenStrokes(snapshot, width, height)
                                }
                                repository.addImageBlock(noteId, bitmap)
                                bitmap.recycle()
                                saving = false
                                onFinished()
                            }
                        },
                    ) {
                        Text(if (saving) "Saving…" else "Done")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PenColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(
                                        width = if (!eraser && color == penColor) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        penColor = color
                                        eraser = false
                                    },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.Center)
                                        .background(color, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Slider(
                            value = penWidth,
                            onValueChange = { penWidth = it },
                            valueRange = 1f..24f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { eraser = !eraser },
                            modifier = Modifier.background(
                                if (eraser) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape,
                            ),
                        ) {
                            Icon(
                                if (eraser) Icons.Default.Clear else Icons.Default.Create,
                                contentDescription = if (eraser) "Pen" else "Eraser",
                            )
                        }
                        TextButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        ) {
                            Text("Undo")
                        }
                        Text(
                            if (eraser) "Eraser" else "Pen",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(eraser, penColor, penWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            current.clear()
                            current.add(offset)
                        },
                        onDrag = { change, _ ->
                            current.add(change.position)
                            change.consume()
                        },
                        onDragEnd = {
                            if (current.size > 1) {
                                strokes.add(
                                    DoodleStroke(current.toList(), activeColor, activeWidth, eraser),
                                )
                            }
                            current.clear()
                        },
                        onDragCancel = { current.clear() },
                    )
                },
        ) {
            drawRect(Color.White)
            for (stroke in strokes) drawDoodleStroke(stroke)
            if (current.size > 1) {
                drawDoodleStroke(DoodleStroke(current.toList(), activeColor, activeWidth, eraser))
            }
        }
    }
}

private fun DrawScope.drawDoodleStroke(stroke: DoodleStroke) {
    if (stroke.points.size < 2) return
    drawPath(
        path = smoothPath(stroke.points),
        color = stroke.color,
        style = Stroke(
            width = stroke.width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    var index = 1
    while (index < points.lastIndex) {
        val control = points[index]
        val next = points[index + 1]
        path.quadraticTo(
            control.x,
            control.y,
            (control.x + next.x) / 2f,
            (control.y + next.y) / 2f,
        )
        index += 1
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

/** Rasterize the doodle at 1080 px wide, preserving the canvas aspect ratio. */
private fun flattenStrokes(strokes: List<DoodleStroke>, canvasWidth: Int, canvasHeight: Int): Bitmap {
    val pageWidth = 1080
    val pageHeight = max(1, (pageWidth * canvasHeight.toFloat() / canvasWidth).roundToInt())
    val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val scale = pageWidth.toFloat() / canvasWidth
    canvas.scale(scale, scale)

    for (stroke in strokes) {
        if (stroke.points.size < 2) continue
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color.toArgb()
            strokeWidth = stroke.width
        }
        val path = android.graphics.Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)
        var index = 1
        while (index < stroke.points.lastIndex) {
            val control = stroke.points[index]
            val next = stroke.points[index + 1]
            path.quadTo(
                control.x,
                control.y,
                (control.x + next.x) / 2f,
                (control.y + next.y) / 2f,
            )
            index += 1
        }
        val last = stroke.points.last()
        path.lineTo(last.x, last.y)
        canvas.drawPath(path, paint)
    }
    return bitmap
}
