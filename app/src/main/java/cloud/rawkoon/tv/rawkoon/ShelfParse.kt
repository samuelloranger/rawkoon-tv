package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.data.objects
import cloud.rawkoon.tv.data.optIntOrNull
import cloud.rawkoon.tv.data.optStringOrNull
import org.json.JSONObject

// Shelves as Rawkoon's routes answer with them. Pure, and parsed off the main thread.

internal fun parseShelves(json: JSONObject): List<ShelfSummary> = json.objects("shelves").mapNotNull(::parseShelfSummary)

internal fun parseShelf(json: JSONObject): ShelfDetail? = json.optJSONObject("shelf")?.let(::parseShelfDetail)

/** A shelf with its entries, as a detail answer carries it and as its saved copy keeps it. */
internal fun parseShelfDetail(json: JSONObject): ShelfDetail? {
    val summary = parseShelfSummary(json) ?: return null
    return ShelfDetail(summary, json.objects("entries").mapNotNull(::parseShelfEntry))
}

internal fun parseShelfMemberships(json: JSONObject): List<ShelfMembership> = json.objects("shelves").mapNotNull {
    val id = it.optInt("id")
    val name = it.optString("name")
    if (id <= 0 || name.isEmpty()) return@mapNotNull null
    ShelfMembership(
        id = id,
        name = name,
        holds = it.optBoolean("holds"),
        entryId = it.optIntOrNull("entry_id"),
        order = ShelfOrder.of(it.optStringOrNull("order_mode")),
    )
}

internal fun parseShelfSummary(json: JSONObject): ShelfSummary? {
    val id = json.optInt("id")
    val name = json.optString("name")
    if (id <= 0 || name.isEmpty()) return null
    return ShelfSummary(
        id = id,
        name = name,
        description = json.optStringOrNull("description"),
        cover = parseCover(json.optJSONObject("cover")),
        entryCount = json.optInt("entry_count"),
        inLibraryCount = json.optInt("in_library_count"),
        homeRow = json.optBoolean("home_row"),
        visibleNow = json.optBoolean("visible_now", true),
        order = ShelfOrder.of(json.optStringOrNull("order_mode")),
    )
}

private fun parseCover(json: JSONObject?): ShelfCover {
    if (json == null) return ShelfCover.Mosaic(emptyList())
    // An uploaded cover is one image, drawn like a backdrop
    when (json.optString("style")) {
        "backdrop" -> return ShelfCover.Backdrop(json.optStringOrNull("backdrop_url"))
        "upload" -> return ShelfCover.Backdrop(json.optStringOrNull("url"))
    }
    val array = json.optJSONArray("posters") ?: return ShelfCover.Mosaic(emptyList())
    return ShelfCover.Mosaic((0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) })
}

private fun parseShelfEntry(json: JSONObject): ShelfEntry? {
    val type = json.optString("type")
    val tmdbId = json.optInt("tmdb_id")
    val title = json.optString("title")
    if ((type != "movie" && type != "show") || tmdbId <= 0 || title.isEmpty()) return null
    return ShelfEntry(
        id = json.optInt("id"),
        tmdbId = tmdbId,
        isMovie = type == "movie",
        season = json.optIntOrNull("season"),
        title = title,
        year = json.optIntOrNull("year"),
        posterUrl = json.optStringOrNull("poster_url"),
        groupLabel = json.optStringOrNull("group_label"),
        position = json.optInt("position"),
        inLibrary = json.optBoolean("in_library"),
    )
}
