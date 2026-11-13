package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbIndex
import cloud.rawkoon.tv.jellyfin.TmdbRef

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
    /** How many shelves may add a Home row, however many ask for one. */
    const val MAX_HOME_ROWS = 2

    /** The shelves that get a Home row now, in the server's order. */
    fun homeShelves(shelves: List<ShelfSummary>): List<ShelfSummary> =
        shelves.filter { it.homeRow && it.visibleNow }.take(MAX_HOME_ROWS)

    /** One row per shelf with something to show; a shelf with nothing visible on this profile is left out. */
    fun rows(details: List<ShelfDetail>, index: TmdbIndex, text: RawkoonText, max: Int = RawkoonHome.MAX_ITEMS): List<HomeRow> =
        details.mapNotNull { detail ->
            val cards = resolve(detail.entries, index, text).take(max)
            if (cards.isEmpty()) null else HomeRow(homeRowId(detail.summary.id), detail.summary.name, cards.map { it.card.item }, landscape = false)
        }

    /** [entries] as cards, in their display order. */
    fun resolve(entries: List<ShelfEntry>, index: TmdbIndex, text: RawkoonText): List<ResolvedShelfEntry> = entries.mapNotNull { entry ->
        val library = index.item(entry.tmdbId, isShow = !entry.isMovie)
        when {
            library != null -> ResolvedShelfEntry(entry, ShelfCard.Library(library, entry.season))
            entry.inLibrary -> null
            else -> ResolvedShelfEntry(entry, ShelfCard.Requestable(missing(entry, text)))
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
