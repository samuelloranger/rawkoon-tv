package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbRef
import java.time.Instant

/** The "My list" row: the account's watchlist, newest addition first. */
object WatchlistRows {
    fun row(all: List<WatchlistEntry>, text: RawkoonText): HomeRow? {
        val items = newestFirst(all).map(::item)
        if (items.isEmpty()) return null
        return HomeRow(RawkoonHome.WATCHLIST_ID, text.watchlistTitle(), items, landscape = false)
    }

    /** At most [RawkoonHome.MAX_ITEMS], the most recently added first; an entry with no readable date sorts last. */
    fun newestFirst(all: List<WatchlistEntry>): List<WatchlistEntry> =
        all.sortedByDescending { e -> e.addedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH }.take(RawkoonHome.MAX_ITEMS)

    // A list answer carries no library state: the card reads as in the library, so opening it prefers the library page when there is one
    fun item(entry: WatchlistEntry): MediaItem = MediaItem.tmdb(
        id = "watchlist-${if (entry.isMovie) "movie" else "tv"}-${entry.tmdbId}",
        isMovie = entry.isMovie,
        title = entry.title,
        tmdb = TmdbRef(id = entry.tmdbId, inLibrary = true, subtitle = entry.year?.toString()),
        overview = entry.overview.orEmpty(),
        year = entry.year,
        communityRating = entry.voteAverage?.toFloat(),
        posterUrl = entry.posterUrl,
    )
}
