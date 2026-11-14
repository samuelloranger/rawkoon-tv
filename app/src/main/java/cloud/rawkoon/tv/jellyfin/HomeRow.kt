package cloud.rawkoon.tv.jellyfin

import org.json.JSONArray
import org.json.JSONObject

/**
 * A titled row of items on the home screen. [landscape] rows show stills with progress. [extra] is
 * what a row of something other than titles needs to draw its cards (the shelves row's shelves).
 */
data class HomeRow(val id: String, val title: String, val items: List<MediaItem>, val landscape: Boolean, val extra: String = "") {
    /** Changes whenever anything a card shows changes. */
    val signature: String
        get() = items.joinToString("|") { "${it.id}:${it.progress}:${it.played}:${it.thumbUrl}:${it.posterUrl}:${it.tmdb}" } + extra

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("landscape", landscape)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } }).put("extra", extra)

    companion object {
        fun listToJson(rows: List<HomeRow>): String = JSONArray().apply { rows.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String): List<HomeRow> {
            val array = JSONArray(text)
            return (0 until array.length()).map { i ->
                val row = array.getJSONObject(i)
                val items = row.getJSONArray("items")
                HomeRow(
                    id = row.getString("id"),
                    title = row.getString("title"),
                    items = (0 until items.length()).map { MediaItem.fromJson(items.getJSONObject(it)) },
                    landscape = row.getBoolean("landscape"),
                    extra = row.optString("extra"),
                )
            }
        }
    }
}
