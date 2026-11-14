package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbIndex
import cloud.rawkoon.tv.jellyfin.TmdbRef
import org.json.JSONArray

/**
 * One shelf entry as this profile can see it: a library title to open and play, or a TMDB title
 * the profile doesn't have, which opens the Discover page where it can be requested.
 */
sealed interface ShelfCard {
    val item: MediaItem

    /** The library title; [season] is set for a show's season placed on its own. */
    data class Library(override val item: MediaItem, val season: Int?) : ShelfCard

    /** A title the library doesn't have. */
    data class Requestable(override val item: MediaItem) : ShelfCard
}

/** An entry with the card it became, so a group header can be read back from the entry. */
data class ResolvedShelfEntry(val entry: ShelfEntry, val card: ShelfCard)

/**
 * Turns shelf entries into cards and Home rows. A title the library has is the library's own item,
 * with its art, progress and watched check. A title Rawkoon says is in the library but this
 * profile's Jellyfin account can't see is dropped: it is there, just not for this profile. The rest
 * stay, dimmed, so a shelf reads as the whole list and not only what happens to be downloaded.
 */
object ShelfRows {
    /** The id of Home's one row of shelves. */
    const val HOME_ROW_ID = "rawkoon:shelves"

    /**
     * Home's row of shelves: a cover card per shelf showing now, in the server's order, as on the
     * Shelves page; null when there is none. Each item stands for its shelf (name, description and
     * a backdrop to preview); the shelves themselves ride in [HomeRow.extra] for the cover cards.
     */
    fun homeRow(shelves: List<ShelfSummary>, title: String): HomeRow? {
        val shown = shelves.filter { it.visibleNow }
        if (shown.isEmpty()) return null
        val extra = JSONArray().apply { shown.forEach { put(it.toJson()) } }.toString()
        return HomeRow(HOME_ROW_ID, title, shown.map(::asItem), landscape = true, extra = extra)
    }

    /** The shelves of Home's shelves [row]; empty for any other row or a payload that doesn't read. */
    fun shelvesOf(row: HomeRow): List<ShelfSummary> {
        if (row.id != HOME_ROW_ID) return emptyList()
        val array = runCatching { JSONArray(row.extra) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseShelfSummary) }
    }

    /** A shelf as the item its card previews: its name and description, and its backdrop when it has one. */
    private fun asItem(shelf: ShelfSummary) = MediaItem(
        id = "shelf-${shelf.id}",
        kind = MediaItem.Kind.OTHER,
        title = shelf.name,
        seriesName = null,
        episodeLabel = null,
        overview = shelf.description.orEmpty(),
        year = null,
        runtimeMinutes = null,
        officialRating = null,
        communityRating = null,
        posterUrl = null,
        thumbUrl = null,
        backdropUrl = (shelf.cover as? ShelfCover.Backdrop)?.url,
        logoUrl = null,
        progress = null,
        played = false,
    )

    /** The library item ids [entries] point at, for one batched read of their current state. */
    fun libraryIds(entries: List<ShelfEntry>, index: TmdbIndex?): List<String> =
        if (index == null) emptyList() else entries.mapNotNull { index.itemId(it.tmdbId, isShow = !it.isMovie) }

    /**
     * [entries] as cards, in their display order, using [items] (the index's titles as the server
     * has them now). Null when there is no [index]: it never built and nothing was saved, so
     * "in the library but not visible here" can't be told from "unknown", and dropping those
     * entries would read as a shelf with nothing to watch.
     */
    fun resolve(entries: List<ShelfEntry>, index: TmdbIndex?, items: Map<String, MediaItem>, text: RawkoonText): List<ResolvedShelfEntry>? {
        if (index == null) return null
        return entries.mapNotNull { entry ->
            val library = index.itemId(entry.tmdbId, isShow = !entry.isMovie)?.let(items::get)
            when {
                library != null -> ResolvedShelfEntry(entry, ShelfCard.Library(library, entry.season))
                entry.inLibrary -> null
                else -> ResolvedShelfEntry(entry, ShelfCard.Requestable(missing(entry, text)))
            }
        }
    }

    /**
     * The shelf's cards by header, in display order: one group per header, in the order the headers
     * first appear, with the ungrouped ones first under a null name. A shelf without headers is one
     * group.
     */
    fun groups(cards: List<ResolvedShelfEntry>): List<Pair<String?, List<ResolvedShelfEntry>>> {
        val grouped = LinkedHashMap<String?, MutableList<ResolvedShelfEntry>>()
        cards.forEach { grouped.getOrPut(it.entry.groupLabel) { mutableListOf() } += it }
        return grouped.map { (label, entries) -> label to entries }
    }

    /** A title the library doesn't have: its TMDB poster, marked so the card reads as one to ask for. */
    private fun missing(entry: ShelfEntry, text: RawkoonText): MediaItem = MediaItem.tmdb(
        id = "shelf-${entry.id}",
        isMovie = entry.isMovie,
        title = entry.title,
        tmdb = TmdbRef(
            id = entry.tmdbId,
            inLibrary = false,
            badge = Badge(text.request(), Badge.Tone.NEUTRAL),
            subtitle = entry.season?.let(text::season) ?: entry.year?.toString(),
        ),
        year = entry.year,
        posterUrl = entry.posterUrl,
    )
}
