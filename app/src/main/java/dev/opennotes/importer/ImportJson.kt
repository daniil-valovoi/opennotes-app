package dev.opennotes.importer

import dev.opennotes.data.TodoItem
import org.json.JSONArray
import org.json.JSONObject

/** Mirror of the Rust import model. All coordinates are in page pixels. */
data class ParsedNote(
    val title: String,
    val createdMs: Long,
    val modifiedMs: Long,
    val pages: List<ParsedPage>,
    val warnings: List<String>,
)

data class ParsedPage(
    val index: Int,
    val width: Int,
    val height: Int,
    val text: String,
    val todos: List<TodoItem>,
    val strokes: List<ParsedStroke>,
    val shapes: List<ParsedShape>,
    val lines: List<ParsedLine>,
    val media: List<ParsedMedia>,
) {
    val hasInk: Boolean get() = strokes.isNotEmpty() || shapes.isNotEmpty() || lines.isNotEmpty()
}

data class ParsedStroke(
    /** ARGB. */
    val color: Int,
    val width: Float,
    /** Flat x,y pairs. */
    val points: FloatArray,
)

data class ParsedShape(
    /** 1 ellipse, 2 triangle, 3 right triangle, 4 rectangle, 8 diamond. */
    val kind: Int,
    val bbox: FloatArray,
    val rotation: Float,
    val stroke: Int?,
    val fill: Int?,
    val width: Float,
)

data class ParsedLine(
    val begin: FloatArray,
    val end: FloatArray,
    /** Flat x,y pairs for elbow/curve lines, empty for straight lines. */
    val points: FloatArray,
    val color: Int,
    val width: Float,
)

data class ParsedMedia(
    val name: String,
    val mimeType: String,
    val bbox: FloatArray,
)

object ImportJson {
    fun parse(json: String): ParsedNote {
        val root = JSONObject(json)
        val pages = root.optJSONArray("pages").mapItems { obj ->
            ParsedPage(
                index = obj.optInt("index"),
                width = obj.optInt("width", 1080),
                height = obj.optInt("height", 1527),
                text = obj.optString("text"),
                todos = obj.optJSONArray("todos").mapItems { todo ->
                    TodoItem(text = todo.optString("text"), checked = todo.optBoolean("checked"))
                },
                strokes = obj.optJSONArray("strokes").mapItems { stroke ->
                    ParsedStroke(
                        color = argb(stroke.optJSONArray("color")),
                        width = stroke.optDouble("width", 3.0).toFloat(),
                        points = pairs(stroke.optJSONArray("points")),
                    )
                },
                shapes = obj.optJSONArray("shapes").mapItems { shape ->
                    ParsedShape(
                        kind = shape.optInt("kind"),
                        bbox = floats(shape.optJSONArray("bbox"), 4),
                        rotation = shape.optDouble("rotation", 0.0).toFloat(),
                        stroke = if (!shape.isNull("stroke")) shape.optLong("stroke").toInt() else null,
                        fill = if (!shape.isNull("fill")) shape.optLong("fill").toInt() else null,
                        width = shape.optDouble("width", 2.0).toFloat(),
                    )
                },
                lines = obj.optJSONArray("lines").mapItems { line ->
                    ParsedLine(
                        begin = floats(line.optJSONArray("begin"), 2),
                        end = floats(line.optJSONArray("end"), 2),
                        points = pairs(line.optJSONArray("points")),
                        color = line.optLong("color", 0xff000000L).toInt(),
                        width = line.optDouble("width", 2.0).toFloat(),
                    )
                },
                media = obj.optJSONArray("media").mapItems { media ->
                    ParsedMedia(
                        name = media.optString("name"),
                        mimeType = media.optString("mime_type"),
                        bbox = floats(media.optJSONArray("bbox"), 4),
                    )
                },
            )
        }
        return ParsedNote(
            title = root.optString("title"),
            createdMs = root.optLong("created_ms"),
            modifiedMs = root.optLong("modified_ms"),
            pages = pages,
            warnings = root.optJSONArray("warnings").toStringList(),
        )
    }

    private fun argb(array: JSONArray?): Int {
        if (array == null || array.length() < 3) return 0xff000000.toInt()
        return (0xff shl 24) or
            (array.optInt(0) shl 16) or
            (array.optInt(1) shl 8) or
            array.optInt(2)
    }

    private fun floats(array: JSONArray?, size: Int): FloatArray {
        val out = FloatArray(size)
        if (array != null) {
            for (index in 0 until minOf(size, array.length())) {
                out[index] = array.optDouble(index).toFloat()
            }
        }
        return out
    }

    private fun pairs(array: JSONArray?): FloatArray {
        if (array == null) return FloatArray(0)
        val out = FloatArray(array.length() * 2)
        for (index in 0 until array.length()) {
            val pair = array.optJSONArray(index) ?: continue
            out[index * 2] = pair.optDouble(0).toFloat()
            out[index * 2 + 1] = pair.optDouble(1).toFloat()
        }
        return out
    }
}

private inline fun <T> JSONArray?.mapItems(transform: (JSONObject) -> T): List<T> {
    val array = this ?: return emptyList()
    return buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(transform(item))
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return buildList(array.length()) {
        for (index in 0 until array.length()) add(array.optString(index, ""))
    }
}
