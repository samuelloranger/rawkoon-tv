package cloud.rawkoon.tv.rawkoon

/** A TMDB title as Rawkoon lists it: explore rows, discover, search and similar titles. */
data class TmdbTitle(
    val tmdbId: Int,
    val isMovie: Boolean,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
    val voteAverage: Double?,
    /** Set when Rawkoon has the title in its library, whatever its state. */
    val libraryId: Int?,
    /** The library entry's state; only the search answers carry it. */
    val libraryStatus: String?,
)

data class TitlePage(val items: List<TmdbTitle>, val page: Int, val totalPages: Int)

data class Genre(val id: Int, val name: String)

data class CastMember(val id: Int, val name: String, val character: String?, val photoUrl: String?)

/** Everything a title page shows beyond the list entry. */
data class TitleModal(
    val overview: String?,
    val tagline: String?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val year: Int?,
    val endYear: Int?,
    val continuing: Boolean,
    val seasons: Int?,
    val episodes: Int?,
    val voteAverage: Double?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val cast: List<CastMember>,
)

data class LibraryEntry(val status: String, val episodeCount: Int?, val downloadedEpisodeCount: Int?)

/** [mine] is true when this account made the request. */
data class RequestEntry(val id: Int, val status: String, val denyReason: String?, val mine: Boolean = false)

/** What Rawkoon knows about a title: its library entry and the request on it; either may be absent. */
data class TitleStatus(val library: LibraryEntry?, val request: RequestEntry?)

/** One entry of the dashboard's upcoming list: a release, or an episode (range) airing on [releaseDate]. */
data class UpcomingEntry(
    val id: String,
    val title: String,
    val isMovie: Boolean,
    val releaseDate: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val tmdbUrl: String?,
    val libraryId: Int?,
    val season: Int?,
    val episode: Int?,
    val episodeEnd: Int?,
    val episodeCount: Int?,
)

/** A media request as `GET /api/requests` lists it. */
data class RequestSummary(
    val id: Int,
    val tmdbId: Int?,
    val isMovie: Boolean,
    /** "movie", "show" or a type this client doesn't handle (books). */
    val type: String,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val status: String,
    val requestedBy: String?,
    val createdAt: String?,
    val decidedAt: String?,
    /** The library entry the request was approved into; null before approval. */
    val libraryMediaId: Int? = null,
)
