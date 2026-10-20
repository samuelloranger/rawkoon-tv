package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.data.optStringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Rawkoon refused a request: the title is already requested (409). */
class AlreadyRequested : RawkoonException()

/** Rawkoon refused a request: the title is already in the library (400). */
class AlreadyInLibrary : RawkoonException()

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

data class RequestEntry(val id: Int, val status: String, val denyReason: String?)

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
)

/**
 * The TMDB side of Rawkoon: discovery rows, search, a title's details, its request state and the
 * requests themselves. Rawkoon proxies TMDB (it holds the key), caches it, and adds what is in its
 * own library to every title. Every call is made with the profile's Rawkoon [token]; a refused one
 * is [RawkoonException.Unauthorized].
 */
class RawkoonDiscover(private val client: RawkoonClient, private val token: String, private val language: String) {
    /** One page of an explore category: trending, popular_movies, popular_shows, upcoming_movies, on_the_air... */
    suspend fun explore(category: String, page: Int = 1): TitlePage =
        parsePage(get("api/medias/explore/$category", "page" to page.toString()))

    /** TMDB's discover for one genre of movies or shows, most popular first. */
    suspend fun discover(isMovie: Boolean, genreId: Int, page: Int = 1): TitlePage =
        parsePage(get("api/medias/discover", "type" to type(isMovie), "genre_id" to genreId.toString(), "page" to page.toString()))

    suspend fun genres(isMovie: Boolean): List<Genre> = parseGenres(get("api/medias/genres", "type" to type(isMovie)))

    /** Movies and shows matching [text]: one page of up to 20 titles, for two letters or more. */
    suspend fun search(text: String): List<TmdbTitle> = parsePage(get("api/medias/tmdb-search", "q" to text.trim(), "kind" to "any")).items

    suspend fun similar(isMovie: Boolean, tmdbId: Int): List<TmdbTitle> =
        parsePage(get("api/medias/similar/$tmdbId", "type" to type(isMovie))).items

    suspend fun modal(isMovie: Boolean, tmdbId: Int): TitleModal = parseModal(get("api/medias/modal/${type(isMovie)}/$tmdbId"))

    suspend fun status(isMovie: Boolean, tmdbId: Int): TitleStatus =
        parseStatus(get("api/requests/status", "tmdb_id" to tmdbId.toString(), "type" to requestType(isMovie)))

    /** Requests the whole title (Rawkoon has no per-season requests); returns the new request's id. */
    suspend fun request(isMovie: Boolean, tmdbId: Int, title: String, posterUrl: String?, year: Int?): Int {
        val body = JSONObject().put("type", requestType(isMovie)).put("tmdb_id", tmdbId).put("title", title)
            .put("poster_url", posterUrl ?: JSONObject.NULL).put("year", year ?: JSONObject.NULL)
        val response = client.send("POST", "api/requests", body, token)
        return when (response.code) {
            409 -> throw AlreadyRequested()
            400 -> throw AlreadyInLibrary()
            else -> response.okJson().optInt("id")
        }
    }

    suspend fun upcoming(): List<UpcomingEntry> = parseUpcoming(get("api/dashboard/upcoming"))

    /** The requests of this account (an admin's account gets everyone's). */
    suspend fun requests(): List<RequestSummary> = parseRequests(get("api/requests"))

    private suspend fun get(path: String, vararg params: Pair<String, String>): JSONObject {
        val query = (params.toList() + ("language" to language)).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return client.send("GET", "$path?$query", token = token).okJson()
    }

    companion object {
        private fun type(isMovie: Boolean) = if (isMovie) "movie" else "tv"

        private fun requestType(isMovie: Boolean) = if (isMovie) "movie" else "show"

        internal fun parsePage(json: JSONObject): TitlePage = TitlePage(
            items = json.objects("items").mapNotNull(::parseTitle),
            page = json.optInt("page", 1),
            totalPages = json.optInt("total_pages", 1),
        )

        private fun parseTitle(json: JSONObject): TmdbTitle? {
            val isMovie = when (json.optString("media_type")) {
                "movie" -> true
                "tv" -> false
                else -> return null
            }
            val id = json.optInt("tmdb_id")
            val title = json.optString("title")
            if (id <= 0 || title.isEmpty()) return null
            return TmdbTitle(
                tmdbId = id,
                isMovie = isMovie,
                title = title,
                year = json.optIntOrNull("release_year"),
                posterUrl = json.optStringOrNull("poster_url"),
                overview = json.optStringOrNull("overview"),
                voteAverage = json.optDoubleOrNull("vote_average"),
                libraryId = json.optIntOrNull("library_id"),
                libraryStatus = json.optStringOrNull("library_status"),
            )
        }

        internal fun parseGenres(json: JSONObject): List<Genre> =
            json.objects("genres").mapNotNull { g -> g.optStringOrNull("name")?.let { Genre(g.optInt("id"), it) } }

        internal fun parseModal(json: JSONObject): TitleModal {
            val details = json.optJSONObject("details")
            val credits = json.optJSONObject("credits")
            val first = details?.optStringOrNull("release_date") ?: details?.optStringOrNull("first_air_date")
            val last = details?.optStringOrNull("last_air_date")
            val status = details?.optStringOrNull("status")
            val runtime = details?.optIntOrNull("runtime") ?: details?.optJSONArray("episode_run_times")?.optInt(0)
            return TitleModal(
                overview = details?.optStringOrNull("overview"),
                tagline = details?.optStringOrNull("tagline"),
                genres = details?.objects("genres").orEmpty().mapNotNull { it.optStringOrNull("name") },
                runtimeMinutes = runtime?.takeIf { it > 0 },
                year = year(first),
                endYear = year(last),
                continuing = status == "Returning Series" || status == "In Production",
                seasons = details?.optIntOrNull("number_of_seasons"),
                episodes = details?.optIntOrNull("number_of_episodes"),
                voteAverage = details?.optDoubleOrNull("vote_average")?.takeIf { it > 0 },
                backdropUrl = details?.optStringOrNull("primary_backdrop_url"),
                logoUrl = details?.optJSONObject("media_stills")?.objects("logos")?.firstNotNullOfOrNull { it.optStringOrNull("url") },
                cast = credits?.objects("cast").orEmpty().mapNotNull { c ->
                    c.optStringOrNull("name")?.let { CastMember(c.optInt("id"), it, c.optStringOrNull("character"), c.optStringOrNull("profile_url")) }
                },
            )
        }

        internal fun parseStatus(json: JSONObject): TitleStatus = TitleStatus(
            library = json.optJSONObject("library")?.let {
                LibraryEntry(it.optString("status"), it.optIntOrNull("episode_count"), it.optIntOrNull("downloaded_episode_count"))
            },
            request = json.optJSONObject("request")?.let {
                RequestEntry(it.optInt("id"), it.optString("status"), it.optStringOrNull("deny_reason"))
            },
        )

        /** A disabled upcoming list (no TMDB key on the server) answers `enabled: false` with no items. */
        internal fun parseUpcoming(json: JSONObject): List<UpcomingEntry> {
            if (!json.optBoolean("enabled")) return emptyList()
            return json.objects("items").mapNotNull {
                val type = it.optString("media_type")
                if (type != "movie" && type != "tv") return@mapNotNull null
                UpcomingEntry(
                    id = it.optString("id"),
                    title = it.optString("title"),
                    isMovie = type == "movie",
                    releaseDate = it.optStringOrNull("release_date"),
                    posterUrl = it.optStringOrNull("poster_url"),
                    backdropUrl = it.optStringOrNull("backdrop_url"),
                    overview = it.optStringOrNull("overview"),
                    tmdbUrl = it.optStringOrNull("tmdb_url"),
                    libraryId = it.optIntOrNull("library_id"),
                    season = it.optIntOrNull("season_number"),
                    episode = it.optIntOrNull("episode_number"),
                    episodeEnd = it.optIntOrNull("episode_number_end"),
                    episodeCount = it.optIntOrNull("episode_count"),
                )
            }
        }

        internal fun parseRequests(json: JSONObject): List<RequestSummary> = json.objects("requests").map {
            val type = it.optString("type")
            RequestSummary(
                id = it.optInt("id"),
                tmdbId = it.optIntOrNull("tmdb_id"),
                isMovie = type == "movie",
                type = type,
                title = it.optString("title"),
                posterUrl = it.optStringOrNull("poster_url"),
                year = it.optIntOrNull("year"),
                status = it.optString("status"),
                requestedBy = it.optJSONObject("requested_by")?.optStringOrNull("id"),
                createdAt = it.optStringOrNull("created_at"),
                decidedAt = it.optStringOrNull("decided_at"),
            )
        }

        private fun year(date: String?) = date?.take(4)?.toIntOrNull()

        private fun JSONObject.objects(key: String): List<JSONObject> {
            val array: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

        private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }
    }
}
