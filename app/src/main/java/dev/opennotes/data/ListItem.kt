package dev.opennotes.data

import org.json.JSONArray
import org.json.JSONObject

data class ListItem(val text: String)

/** LIST blocks keep their items as a JSON array of objects or strings. */
object ListItemJson {
    fun encode(items: List<ListItem>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().put("text", item.text))
        }
        return array.toString()
    }

    fun decode(json: String?): List<ListItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index)
                    if (obj != null) {
                        add(ListItem(obj.optString("text", "")))
                    } else {
                        add(ListItem(array.optString(index, "")))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }
}
