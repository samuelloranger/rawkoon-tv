package cloud.rawkoon.tv.rawkoon

import org.json.JSONArray
import org.json.JSONObject

/** How a shelf orders its titles: by hand, or by the server (release year, rating, when added). */
enum class ShelfOrder {
    MANUAL,
    RELEASE,
    RATING,
    ADDED,
    ;

    companion object {
        fun of(value: String?): ShelfOrder = when (value) {
            "release" -> RELEASE
            "rating" -> RATING
            "added" -> ADDED
            else -> MANUAL
        }
    }
}

/** What the Collections row and the Home rows draw on a shelf's cover. */
sealed interface ShelfCover {
    /** Up to four posters, drawn as a 2×2 mosaic. */
    data class Mosaic(val posters: List<String>) : ShelfCover

    /** A chosen title's backdrop; null when the server could not find one. */
    data class Backdrop(val url: String?) : ShelfCover
}

/** A shelf as the lists show it: its name, cover and how many titles it has. */
data class ShelfSummary(
    val id: Int,
    val name: String,
    val description: String?,
    val cover: ShelfCover,
    val entryCount: Int,
    val inLibraryCount: Int,
    val homeRow: Boolean,
    val visibleNow: Boolean,
    val order: ShelfOrder,
)

/** One title on a shelf, as the server stores it: a TMDB id, and the library's answer when it has it. */
data class ShelfEntry(
    val id: Int,
    val tmdbId: Int,
    val isMovie: Boolean,
    /** A show's season placed on its own (story order); null for the whole title. */
    val season: Int?,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val groupLabel: String?,
    val position: Int,
    val inLibrary: Boolean,
)

/** One shelf with its entries in display order. */
data class ShelfDetail(val summary: ShelfSummary, val entries: List<ShelfEntry>) {
    fun toJson(): JSONObject = summary.toJson().put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
}

/** The shelf as its saved copy keeps it: the same fields the routes answer with. */
fun ShelfSummary.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).putOpt("description", description)
    .put("cover", cover.toJson())
    .put("entry_count", entryCount).put("in_library_count", inLibraryCount)
    .put("home_row", homeRow).put("visible_now", visibleNow).put("order_mode", order.name.lowercase())

private fun ShelfCover.toJson(): JSONObject = when (this) {
    is ShelfCover.Mosaic -> JSONObject().put("style", "mosaic").put("posters", JSONArray().apply { posters.forEach { put(it) } })
    is ShelfCover.Backdrop -> JSONObject().put("style", "backdrop").putOpt("backdrop_url", url)
}

private fun ShelfEntry.toJson(): JSONObject = JSONObject()
    .put("id", id).put("tmdb_id", tmdbId).put("type", if (isMovie) "movie" else "show")
    .putOpt("season", season).put("title", title).putOpt("year", year)
    .putOpt("poster_url", posterUrl).putOpt("group_label", groupLabel)
    .put("position", position).put("in_library", inLibrary)

/** One row of the "Add to shelf" menu: whether the shelf already holds the title, and its entry when it does. */
data class ShelfMembership(
    val id: Int,
    val name: String,
    val holds: Boolean,
    val entryId: Int?,
    val order: ShelfOrder,
)

/** The stable prefix of a shelf's Home row, so one can be picked out of the home's rows by id. */
const val SHELF_HOME_PREFIX = "rawkoon:shelf:"

fun homeRowId(shelfId: Int) = "$SHELF_HOME_PREFIX$shelfId"

/** The shelf id a row id stands for, or null for any other row. */
fun shelfIdOf(rowId: String): Int? =
    if (rowId.startsWith(SHELF_HOME_PREFIX)) rowId.removePrefix(SHELF_HOME_PREFIX).toIntOrNull() else null
