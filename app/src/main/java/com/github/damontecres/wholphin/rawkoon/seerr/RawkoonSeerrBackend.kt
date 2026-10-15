package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.rawkoon.CreateRequest
import com.github.damontecres.wholphin.rawkoon.RawkoonClient
import com.github.damontecres.wholphin.rawkoon.RawkoonException
import com.github.damontecres.wholphin.rawkoon.RawkoonMediaType
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.rawkoon.TitleStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class SeerrReply(
    val code: Int,
    val body: JsonElement,
)

@Serializable
private data class SeerrRequestBody(
    val mediaType: String = "",
    val mediaId: Int = 0,
)

/**
 * Answers the Seerr API calls that the discover and request screens make, from Rawkoon.
 *
 * The screens talk to a generated Seerr client; this is what sits at the other end of it when the user is signed in to
 * Rawkoon. Each route maps one Seerr call to Rawkoon calls and writes the answer in Seerr's shape ([SeerrJson]).
 * Anything Rawkoon has no equivalent for (person credits, editing or cancelling a request, quality profiles) answers
 * with an empty or refused reply so the screens degrade instead of failing.
 */
@Singleton
class RawkoonSeerrBackend
    @Inject
    constructor(
        private val rawkoon: RawkoonClient,
        private val settings: RawkoonSettingsRepository,
        private val jellyfinLookup: JellyfinTmdbLookup,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        internal var languageTag: () -> String = { Locale.getDefault().toLanguageTag() }

        private val items = ConcurrentHashMap<Pair<Boolean, Int>, RkItem>()
        private val modalRatings = ConcurrentHashMap<Pair<Boolean, Int>, RkRatings>()

        suspend fun handle(
            method: String,
            path: String,
            query: Map<String, String>,
            body: String?,
        ): SeerrReply =
            try {
                route(method, path.trimEnd('/'), query, body) ?: reply(404, "Not found")
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                reply(401, "Rawkoon session ended")
            } catch (e: RawkoonException.Http) {
                Timber.w("Rawkoon answered %d for %s %s", e.code, method, path)
                reply(e.code, "Rawkoon error")
            } catch (e: RawkoonException) {
                Timber.w(e, "Rawkoon call failed for %s %s", method, path)
                reply(BAD_GATEWAY, "Rawkoon unreachable")
            } catch (e: Exception) {
                Timber.e(e, "Seerr call failed for %s %s", method, path)
                reply(SERVER_ERROR, "Unexpected error")
            }

        private suspend fun route(
            method: String,
            path: String,
            query: Map<String, String>,
            body: String?,
        ): SeerrReply? {
            MEDIA_PATH.matchEntire(path)?.let { match ->
                if (method != "GET") return null
                val isMovie = match.groupValues[1] == "movie"
                val id = match.groupValues[2].toInt()
                return when (match.groupValues[3]) {
                    "" -> details(id, isMovie)
                    "ratings" -> ratings(id, isMovie)
                    else -> similar(id, isMovie)
                }
            }
            PERSON_CREDITS.matchEntire(path)?.let {
                return ok(buildJsonObject { put("cast", JsonArray(emptyList())) ; put("crew", JsonArray(emptyList())) })
            }
            if (REQUEST_BY_ID.matches(path)) return reply(FORBIDDEN, "Rawkoon requests cannot be changed from here")
            return when ("$method $path") {
                "GET /settings/public" -> ok(SeerrJson.publicSettings())
                "GET /auth/me" -> ok(SeerrJson.user(settings.current().userName))
                "GET /search" -> search(query)
                "GET /discover/movies" -> discover(true, query)
                "GET /discover/tv" -> discover(false, query)
                "GET /discover/trending" -> explore("trending", query)
                "GET /discover/movies/upcoming" -> explore("upcoming_movies", query)
                "GET /discover/tv/upcoming" -> explore("on_the_air", query)
                "GET /discover/genreslider/movie" -> genres(true)
                "GET /discover/genreslider/tv" -> genres(false)
                "GET /watchproviders/movies" -> providers(true)
                "GET /watchproviders/tv" -> providers(false)
                "GET /watchproviders/regions" -> ok(JsonArray(emptyList()))
                "GET /request" -> requests()
                "POST /request" -> createRequest(body)
                else -> null
            }
        }

        private fun ok(body: JsonElement) = SeerrReply(OK, body)

        private fun reply(
            code: Int,
            message: String,
        ) = SeerrReply(code, SeerrJson.error(message))

        private fun remember(list: List<RkItem>): List<RkItem> {
            // Only a lookup table for titles seen recently, so it can simply start over
            if (items.size > MAX_REMEMBERED) items.clear()
            list.forEach { items[it.isMovie to it.tmdbId] = it }
            return list
        }

        private suspend fun get(
            path: String,
            query: Map<String, String> = emptyMap(),
        ): String = rawkoon.call("GET", path, query + ("language" to languageTag())).bodyOrThrow()

        private fun page(
            list: List<RkItem>,
            page: Int,
            totalPages: Int,
            totalResults: Int = totalPages * SEERR_PAGE_SIZE,
        ) = ok(SeerrJson.resultsPage(remember(list), page, totalPages, totalResults))

        private suspend fun explore(
            category: String,
            query: Map<String, String>,
        ): SeerrReply {
            val page = query["page"]?.toIntOrNull() ?: 1
            val result = json.decodeFromString<RkItemPage>(get("api/medias/explore/$category", mapOf("page" to page.toString())))
            return page(result.items, page, result.totalPages)
        }

        private suspend fun search(query: Map<String, String>): SeerrReply {
            val text = query["query"].orEmpty().trim()
            // Rawkoon answers one page of up to 20 titles
            if (text.length < 2 || (query["page"]?.toIntOrNull() ?: 1) > 1) return page(emptyList(), 1, 1, 0)
            val result = json.decodeFromString<RkItemPage>(get("api/medias/tmdb-search", mapOf("q" to text, "kind" to "any")))
            return page(result.items, 1, 1, result.items.size)
        }

        /**
         * Seerr pages hold 20 titles and Rawkoon's discover pages hold 48, so a Seerr page is cut out of one or two of them
         */
        private suspend fun discover(
            isMovie: Boolean,
            query: Map<String, String>,
        ): SeerrReply {
            val page = query["page"]?.toIntOrNull() ?: 1
            val offset = (page - 1) * SEERR_PAGE_SIZE
            val params =
                buildMap {
                    put("type", if (isMovie) "movie" else "tv")
                    put("sort_by", rawkoonSort(query["sortBy"], isMovie))
                    query["genre"]?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { put("genre_id", it) }
                    query["watchProviders"]?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { put("provider_id", it) }
                }

            suspend fun fetch(rawkoonPage: Int) = json.decodeFromString<RkItemPage>(get("api/medias/discover", params + ("page" to rawkoonPage.toString())))
            val first = fetch(offset / DISCOVER_PAGE_SIZE + 1)
            var list = first.items.drop(offset % DISCOVER_PAGE_SIZE).take(SEERR_PAGE_SIZE)
            if (list.size < SEERR_PAGE_SIZE && first.page < first.totalPages) {
                list = list + fetch(first.page + 1).items.take(SEERR_PAGE_SIZE - list.size)
            }
            val total = first.totalResults ?: (first.totalPages * DISCOVER_PAGE_SIZE)
            return page(list, page, (total + SEERR_PAGE_SIZE - 1) / SEERR_PAGE_SIZE, total)
        }

        private suspend fun genres(isMovie: Boolean): SeerrReply {
            val result = json.decodeFromString<RkGenres>(get("api/medias/genres", mapOf("type" to if (isMovie) "movie" else "tv")))
            return ok(SeerrJson.genreSlider(result.genres))
        }

        private suspend fun providers(isMovie: Boolean): SeerrReply {
            val result = json.decodeFromString<RkProviders>(get("api/medias/streaming-providers", mapOf("type" to if (isMovie) "movie" else "tv")))
            return ok(SeerrJson.watchProviders(result.providers))
        }

        private suspend fun similar(
            id: Int,
            isMovie: Boolean,
        ): SeerrReply {
            val result = json.decodeFromString<RkItemPage>(get("api/medias/similar/$id", mapOf("type" to if (isMovie) "movie" else "tv")))
            return page(result.items, 1, 1, result.items.size)
        }

        private suspend fun status(
            id: Int,
            isMovie: Boolean,
        ): TitleStatus? =
            try {
                rawkoon.status(id, if (isMovie) RawkoonMediaType.MOVIE else RawkoonMediaType.SHOW)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                throw e
            } catch (e: RawkoonException) {
                // The page is still worth showing without knowing where the title stands
                Timber.w(e, "No Rawkoon status for %d", id)
                null
            }

        private suspend fun details(
            id: Int,
            isMovie: Boolean,
        ): SeerrReply =
            coroutineScope {
                val modalCall = async { json.decodeFromString<RkModal>(get("api/medias/modal/${if (isMovie) "movie" else "tv"}/$id")) }
                val statusCall = async { status(id, isMovie) }
                val modal = modalCall.await()
                val title = items[isMovie to id]
                val status = statusCall.await()
                modal.ratings?.let { modalRatings[isMovie to id] = it }
                val availability = SeerrJson.availabilityOf(status?.library, status?.request, !isMovie)
                val jellyfinId =
                    if (availability == SeerrAvailability.AVAILABLE || availability == SeerrAvailability.PARTIALLY_AVAILABLE) {
                        val name = SeerrJson.titleOf(title?.title, modal.details)
                        val year = title?.releaseYear ?: (modal.details?.releaseDate ?: modal.details?.firstAirDate)?.take(YEAR_LENGTH)?.toIntOrNull()
                        jellyfinLookup.find(id, name, year, !isMovie)
                    } else {
                        null
                    }
                val titleText = SeerrJson.titleOf(title?.title, modal.details)
                if (isMovie) {
                    ok(SeerrJson.movieDetails(id, titleText, title?.posterUrl, modal, SeerrJson.mediaInfo(id, availability, jellyfinId)))
                } else {
                    val seasons = SeerrJson.seasonStatuses(modal.details, modal.libraryEpisodes, availability)
                    ok(
                        SeerrJson.tvDetails(
                            id,
                            titleText,
                            title?.posterUrl,
                            modal,
                            SeerrJson.mediaInfo(id, availability, jellyfinId, seasons),
                            seasons,
                        ),
                    )
                }
            }

        /**
         * Only what is already known: the score of a title seen in a list or opened, never a call per focused card
         */
        private fun ratings(
            id: Int,
            isMovie: Boolean,
        ): SeerrReply {
            val known = modalRatings[isMovie to id]
            val body =
                if (isMovie) {
                    SeerrJson.movieRatings(known?.rottenTomatoes, known?.imdbRating, items[true to id]?.voteAverage)
                } else {
                    SeerrJson.tvRatings(known?.rottenTomatoes)
                }
            return body?.let(::ok) ?: reply(NOT_FOUND, "No rating")
        }

        private suspend fun requests(): SeerrReply {
            val result = json.decodeFromString<RkRequests>(rawkoon.call("GET", "api/requests").bodyOrThrow())
            val wanted = result.requests.filter { (it.type == "movie" || it.type == "show") && it.tmdbId != null }
            remember(
                wanted.map {
                    RkItem(it.tmdbId!!, if (it.type == "movie") "movie" else "tv", it.title, it.year, it.posterUrl)
                },
            )
            return ok(SeerrJson.requestsPage(wanted))
        }

        /**
         * Rawkoon requests the whole title: the seasons, quality profile and folder the screens may send are ignored
         */
        private suspend fun createRequest(body: String?): SeerrReply {
            val request = json.decodeFromString<SeerrRequestBody>(body.orEmpty())
            val isMovie = request.mediaType == "movie"
            val known = items[isMovie to request.mediaId]
            val create =
                if (known != null) {
                    CreateRequest(if (isMovie) "movie" else "show", request.mediaId, known.title, known.posterUrl, known.releaseYear)
                } else {
                    val modal = json.decodeFromString<RkModal>(get("api/medias/modal/${if (isMovie) "movie" else "tv"}/${request.mediaId}"))
                    val details = modal.details
                    val title = SeerrJson.titleOf(null, details) ?: return reply(BAD_REQUEST, "Unknown title")
                    val year = (details?.releaseDate ?: details?.firstAirDate)?.take(YEAR_LENGTH)?.toIntOrNull()
                    val poster = details?.mediaStills?.posters?.maxByOrNull { it.voteAverage ?: 0.0 }?.url
                    CreateRequest(if (isMovie) "movie" else "show", request.mediaId, title, poster, year)
                }
            val response = rawkoon.call("POST", "api/requests", body = json.encodeToString(CreateRequest.serializer(), create))
            if (response.code !in 200..299) return reply(response.code, "Request refused")
            val id = json.decodeFromString<CreatedRequest>(response.body).id
            return ok(
                buildJsonObject {
                    put("id", id)
                    put("status", 1)
                    put("type", if (isMovie) "movie" else "tv")
                },
            )
        }

        @Serializable
        private data class CreatedRequest(
            val id: Int,
        )

        companion object {
            const val SEERR_PAGE_SIZE = 20
            private const val DISCOVER_PAGE_SIZE = 48
            private const val YEAR_LENGTH = 4
            private const val MAX_REMEMBERED = 2000
            private const val OK = 200
            private const val BAD_REQUEST = 400
            private const val FORBIDDEN = 403
            private const val NOT_FOUND = 404
            private const val SERVER_ERROR = 500
            private const val BAD_GATEWAY = 502

            private val MEDIA_PATH = Regex("/(movie|tv)/(\\d+)(?:/(similar|recommendations|ratings))?")
            private val PERSON_CREDITS = Regex("/person/\\d+/combined_credits")
            private val REQUEST_BY_ID = Regex("/request/\\d+")

            private val SORTS = setOf("popularity.desc", "popularity.asc", "vote_average.desc", "vote_average.asc")

            /**
             * Rawkoon sorts by popularity, rating or newest; Seerr's name sort and oldest-first have no equivalent
             */
            fun rawkoonSort(
                seerrSort: String?,
                isMovie: Boolean,
            ): String =
                when {
                    seerrSort in SORTS -> seerrSort!!
                    seerrSort == "release_date.desc" && isMovie -> "primary_release_date.desc"
                    seerrSort == "first_air_date.desc" && !isMovie -> "first_air_date.desc"
                    else -> "popularity.desc"
                }
        }
    }
