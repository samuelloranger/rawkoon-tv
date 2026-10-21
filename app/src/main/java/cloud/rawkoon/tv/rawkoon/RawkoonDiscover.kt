package cloud.rawkoon.tv.rawkoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Rawkoon refused a request: the title is already requested (409). */
class AlreadyRequested : RawkoonException()

/** Rawkoon refused a request: the title is already in the library (400). */
class AlreadyInLibrary : RawkoonException()

/**
 * The TMDB side of Rawkoon: discovery rows, search, a title's details, its request state and the
 * requests themselves. Rawkoon proxies TMDB (it holds the key), caches it, and adds what is in its
 * own library to every title. Every call is made with the profile's Rawkoon [token]; a refused one
 * is [RawkoonException.Unauthorized].
 */
class RawkoonDiscover(private val client: RawkoonClient, private val token: String, private val language: String) {
    /** One page of an explore category: trending, popular_movies, popular_shows, upcoming_movies, on_the_air... */
    suspend fun explore(category: String, page: Int = 1): TitlePage =
        get("api/medias/explore/$category", "page" to page.toString(), parse = ::parsePage)

    /** TMDB's discover for one genre of movies or shows, most popular first. */
    suspend fun discover(isMovie: Boolean, genreId: Int, page: Int = 1): TitlePage =
        get("api/medias/discover", "type" to type(isMovie), "genre_id" to genreId.toString(), "page" to page.toString(), parse = ::parsePage)

    suspend fun genres(isMovie: Boolean): List<Genre> = get("api/medias/genres", "type" to type(isMovie), parse = ::parseGenres)

    /** Movies and shows matching [text]: one page of up to 20 titles, for two letters or more. */
    suspend fun search(text: String): List<TmdbTitle> =
        get("api/medias/tmdb-search", "q" to text.trim(), "kind" to "any", parse = ::parsePage).items

    suspend fun similar(isMovie: Boolean, tmdbId: Int): List<TmdbTitle> =
        get("api/medias/similar/$tmdbId", "type" to type(isMovie), parse = ::parsePage).items

    suspend fun modal(isMovie: Boolean, tmdbId: Int): TitleModal = get("api/medias/modal/${type(isMovie)}/$tmdbId", parse = ::parseModal)

    suspend fun status(isMovie: Boolean, tmdbId: Int): TitleStatus =
        get("api/requests/status", "tmdb_id" to tmdbId.toString(), "type" to requestType(isMovie), parse = ::parseStatus)

    /** Requests the whole title (Rawkoon has no per-season requests); returns the new request's id. */
    suspend fun request(isMovie: Boolean, tmdbId: Int, title: String, posterUrl: String?, year: Int?): Int {
        val body = JSONObject().put("type", requestType(isMovie)).put("tmdb_id", tmdbId).put("title", title)
            .put("poster_url", posterUrl ?: JSONObject.NULL).put("year", year ?: JSONObject.NULL)
        val response = client.send("POST", "api/requests", body, token)
        return withContext(Dispatchers.Default) {
            when (response.code) {
                409 -> throw AlreadyRequested()
                // Rawkoon's 400 for a title already in the library carries this message; any other 400 is a malformed request
                400 -> throw if (runCatching { JSONObject(response.body).optString("error") }.getOrNull() == "Already in your library") AlreadyInLibrary() else RawkoonException.Http(400)
                else -> response.okJson().optInt("id")
            }
        }
    }

    /** Withdraws the account's own pending or declined request; one that is already gone counts as withdrawn. */
    suspend fun withdraw(requestId: Int) {
        val response = client.send("DELETE", "api/requests/$requestId", token = token)
        // Only a failure's answer matters; okJson throws the matching error for it
        if (response.code != 404 && response.code !in 200..299) response.okJson()
    }

    /** The Rawkoon user id of the token, to tell the account's own requests apart. */
    suspend fun userId(): String = client.bootstrap(token).userId

    suspend fun upcoming(): List<UpcomingEntry> = get("api/dashboard/upcoming", parse = ::parseUpcoming)

    /** The requests of this account; `mine` makes the server filter before its cap, which older servers ignore. */
    suspend fun requests(): List<RequestSummary> = get("api/requests", "mine" to "1", parse = ::parseRequests)

    /** The answer, read after the network call and parsed off the main thread, so callers get typed results. */
    private suspend fun <T> get(path: String, vararg params: Pair<String, String>, parse: (JSONObject) -> T): T {
        val query = (params.toList() + ("language" to language)).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val response = client.send("GET", "$path?$query", token = token)
        return withContext(Dispatchers.Default) { parse(response.okJson()) }
    }

    private companion object {
        fun type(isMovie: Boolean) = if (isMovie) "movie" else "tv"

        fun requestType(isMovie: Boolean) = if (isMovie) "movie" else "show"
    }
}
