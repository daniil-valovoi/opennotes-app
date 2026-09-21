package dev.opennotes.data

import org.json.JSONArray
import org.json.JSONObject

data class TodoItem(val text: String, val checked: Boolean)

/** TODO blocks keep their items as a small JSON array so the schema stays simple. */
object TodoJson {
    fun encode(items: List<TodoItem>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().put("text", item.text).put("checked", item.checked))
        }
        return array.toString()
    }

    fun decode(json: String?): List<TodoItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(TodoItem(obj.optString("text"), obj.optBoolean("checked")))
                }
            }
        }.getOrElse { emptyList() }
    }
}

/** TEXT blocks keep their font size in the same JSON column, avoiding a schema change. */
object TextSizeJson {
    fun encode(size: Int): String? =
        if (size == TextSize.NORMAL) null else JSONObject().put("size", size).toString()

    fun decode(json: String?): Int {
        if (json.isNullOrBlank() || !json.trimStart().startsWith("{")) return TextSize.NORMAL
        return runCatching {
            val size = JSONObject(json).optInt("size", TextSize.NORMAL)
            if (size in TextSize.all) size else TextSize.NORMAL
        }.getOrElse { TextSize.NORMAL }
    }
}
