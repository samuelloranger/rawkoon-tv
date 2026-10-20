package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem

/** Where a title stands for this account, from Rawkoon's library entry and the request on it. */
sealed interface TitleState {
    /** Nothing yet: the title can be requested. */
    data object Requestable : TitleState

    /** Requested, waiting for an admin. */
    data object Pending : TitleState

    /** Approved or added to the library, not there yet. [percent] is known for a show being downloaded. */
    data class Processing(val downloading: Boolean, val percent: Int?) : TitleState

    /** A show with some of its episodes downloaded. */
    data object Partial : TitleState

    data object Available : TitleState

    /** The request was denied; [reason] is only known for the account's own requests. */
    data class Refused(val reason: String?) : TitleState

    /** Rawkoon's library skips this title: it will not be fetched. */
    data object Skipped : TitleState

    /** The library can serve it, so there may be a Jellyfin item to open. */
    val inLibrary: Boolean get() = this == Available || this == Partial

    /** Only a title nobody has asked for yet can be requested. */
    val canRequest: Boolean get() = this == Requestable

    companion object {
        internal val DOWNLOADED = setOf("downloaded", "upgrading")

        /** The library entry wins over the request: a title can be downloaded without ever being requested. */
        fun of(status: TitleStatus?, isShow: Boolean): TitleState {
            val library = status?.library
            if (library != null) {
                val total = library.episodeCount ?: 0
                val done = library.downloadedEpisodeCount ?: 0
                return when {
                    library.status == "skipped" -> Skipped
                    isShow && done > 0 && (total <= 0 || done < total) -> Partial
                    library.status in DOWNLOADED || (isShow && total > 0 && done >= total) -> Available
                    else -> Processing(library.status == "downloading", percent(library, isShow))
                }
            }
            val request = status?.request ?: return Requestable
            return when (request.status) {
                "pending" -> Pending
                "approved" -> Processing(downloading = false, percent = null)
                "available" -> Available
                "denied" -> Refused(request.denyReason)
                else -> Requestable
            }
        }

        private fun percent(library: LibraryEntry, isShow: Boolean): Int? {
            val total = library.episodeCount ?: 0
            return if (isShow && total > 0) (100 * (library.downloadedEpisodeCount ?: 0) / total).coerceIn(0, 99) else null
        }

        /**
         * The state of one of the account's own requests on the home row. Only an approved request is
         * worth the [live] look at the library: the others' state is the request's own.
         */
        fun ofRequest(request: RequestSummary, live: TitleStatus?): TitleState = when (request.status) {
            "pending" -> Pending
            "denied" -> Refused(null)
            "available" -> Available
            else -> {
                val library = live?.library
                // The row shows the download's progress, even once some episodes are in
                if (library?.status == "downloading") Processing(true, percent(library, !request.isMovie))
                else of(TitleStatus(library, RequestEntry(request.id, "approved", null)), !request.isMovie)
            }
        }
    }
}

/**
 * TMDB's image host answers any query string, and the shared card, header and backdrop code adds
 * its own `&key=value` sizing parameters to image URLs (Jellyfin's). Giving TMDB URLs a query
 * first keeps those appended parameters valid.
 */
fun tmdbImage(url: String?): String? = url?.let { if ('?' in it) it else "$it?src=tmdb" }

/** The plain URL again, for sending back to Rawkoon (a request stores its poster). */
fun rawTmdbImage(url: String?): String? = url?.removeSuffix("?src=tmdb")

/** A logo at a size that stays sharp in the title page's logo box; the modal only carries the small one. */
fun tmdbLogo(url: String?): String? = url?.replace("/w185/", "/w500/")?.let(::tmdbImage)

/**
 * A TMDB list entry as the shared UI shows it: cards, header and the title page all take a
 * [MediaItem]. A title Rawkoon already has is marked; a list answer carries no library state, so
 * it reads as in the library, while a search answer says when the entry is still on its way.
 */
fun TmdbTitle.toItem(text: RawkoonText): MediaItem = MediaItem(
    id = "tmdb-${if (isMovie) "movie" else "tv"}-$tmdbId",
    kind = if (isMovie) MediaItem.Kind.MOVIE else MediaItem.Kind.SERIES,
    title = title,
    seriesName = null,
    episodeLabel = null,
    overview = overview.orEmpty(),
    year = year,
    runtimeMinutes = null,
    officialRating = null,
    communityRating = voteAverage?.toFloat(),
    posterUrl = tmdbImage(posterUrl),
    thumbUrl = null,
    backdropUrl = null,
    logoUrl = null,
    progress = null,
    played = false,
    tmdbId = tmdbId,
    inLibrary = libraryId != null,
    badge = when {
        libraryId == null -> null
        libraryStatus == null || libraryStatus in TitleState.DOWNLOADED -> Badge(text.inLibrary(), Badge.Tone.READY)
        else -> Badge(text.inProgress(), Badge.Tone.ACTIVE)
    },
)
