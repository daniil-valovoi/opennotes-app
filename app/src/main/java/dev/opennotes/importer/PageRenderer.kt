package dev.opennotes.importer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws decoded ink, shapes and lines into a page-sized bitmap.
 *
 * Samsung coordinates are already page pixels, so the only transform is a uniform
 * downscale that keeps enormous pages within a sane bitmap size.
 */
object PageRenderer {
    const val BACKGROUND = 0xFFFFFFFF.toInt()

    fun render(page: ParsedPage, maxDimension: Int = 2600): Bitmap? {
        if (!page.hasInk || page.width <= 0 || page.height <= 0) return null
        val scale = min(1f, maxDimension.toFloat() / max(page.width, page.height))
        val width = max(1, (page.width * scale).roundToInt())
        val height = max(1, (page.height * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        canvas.scale(scale, scale)

        for (shape in page.shapes) drawShape(canvas, shape)
        for (line in page.lines) drawLine(canvas, line)
        for (stroke in page.strokes) drawStroke(canvas, stroke)

        return bitmap
    }

    private fun drawStroke(canvas: Canvas, stroke: ParsedStroke) {
        val points = stroke.points
        if (points.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            strokeWidth = stroke.width.coerceAtLeast(1f)
        }
        if (points.size == 2) {
            canvas.drawCircle(points[0], points[1], paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
            return
        }

        val path = Path().apply {
            moveTo(points[0], points[1])
            var index = 2
            while (index + 3 < points.size) {
                val cx = points[index]
                val cy = points[index + 1]
                val nx = points[index + 2]
                val ny = points[index + 3]
                quadTo(cx, cy, (cx + nx) / 2f, (cy + ny) / 2f)
                index += 2
            }
            lineTo(points[points.size - 2], points[points.size - 1])
        }
        canvas.drawPath(path, paint)
    }

    private fun drawLine(canvas: Canvas, line: ParsedLine) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = line.color
            strokeWidth = line.width.coerceAtLeast(1f)
        }
        val path = Path()
        if (line.points.size >= 4) {
            path.moveTo(line.points[0], line.points[1])
            var index = 2
            while (index + 1 < line.points.size) {
                path.lineTo(line.points[index], line.points[index + 1])
                index += 2
            }
        } else {
            path.moveTo(line.begin[0], line.begin[1])
            path.lineTo(line.end[0], line.end[1])
        }
        canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, shape: ParsedShape) {
        val left = shape.bbox[0]
        val top = shape.bbox[1]
        val right = shape.bbox[2]
        val bottom = shape.bbox[3]
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shape.fill ?: Color.TRANSPARENT
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = shape.stroke ?: Color.TRANSPARENT
            strokeWidth = shape.width.coerceAtLeast(1f)
        }

        canvas.save()
        if (shape.rotation != 0f) {
            canvas.rotate(shape.rotation, left + width / 2f, top + height / 2f)
        }

        when (shape.kind) {
            1 -> {
                if (shape.fill != null) canvas.drawOval(left, top, right, bottom, fill)
                if (shape.stroke != null) canvas.drawOval(left, top, right, bottom, stroke)
            }
            4 -> {
                if (shape.fill != null) canvas.drawRect(left, top, right, bottom, fill)
                if (shape.stroke != null) canvas.drawRect(left, top, right, bottom, stroke)
            }
            else -> {
                val path = Path()
                when (shape.kind) {
                    2 -> { // triangle, apex at top center
                        path.moveTo(left + width / 2f, top)
                        path.lineTo(right, bottom)
                        path.lineTo(left, bottom)
                    }
                    3 -> { // right triangle
                        path.moveTo(left, top)
                        path.lineTo(right, bottom)
                        path.lineTo(left, bottom)
                    }
                    8 -> { // diamond
                        path.moveTo(left + width / 2f, top)
                        path.lineTo(right, top + height / 2f)
                        path.lineTo(left + width / 2f, bottom)
                        path.lineTo(left, top + height / 2f)
                    }
                    else -> {
                        canvas.restore()
                        return
                    }
                }
                path.close()
                if (shape.fill != null) canvas.drawPath(path, fill)
                if (shape.stroke != null) canvas.drawPath(path, stroke)
            }
        }
        canvas.restore()
    }
}
