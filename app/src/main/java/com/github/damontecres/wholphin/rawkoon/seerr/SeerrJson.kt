package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.rawkoon.LibraryEntry
import com.github.damontecres.wholphin.rawkoon.RequestEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Rawkoon data written as the JSON that Wholphin's generated Seerr client reads
 *
 * Field names are the Seerr API's. Image values are TMDB paths because the app prefixes them itself.
 */
object SeerrJson {
    /** The only user: Rawkoon decides who may request, so the app just needs the basic request permission */
    private const val PERMISSION_REQUEST = 32
    const val USER_ID = 1

    private val LIBRARY_DOWNLOADED = setOf("downloaded", "upgrading")

    /** `https://image.tmdb.org/t/p/w342/abc.jpg` becomes `/abc.jpg` */
    fun imagePath(url: String?): String? = url?.let { IMAGE_PATH.find(it)?.groupValues?.get(1) }

    private val IMAGE_PATH = Regex("/t/p/[^/]+(/[^?]+)")

    /**
     * Where a title stands for the user, from the Rawkoon library entry (what is being fetched) and the request
     * (what was asked for). A title that only shows up in a list is "in progress" when Rawkoon tracks it.
     */
    fun availabilityOf(
        library: LibraryEntry?,
        request: RequestEntry?,
        isShow: Boolean,
    ): SeerrAvailability {
        if (library != null) {
            val total = library.episodeCount ?: 0
            val done = library.downloadedEpisodeCount ?: 0
            return when {
                library.status == "skipped" -> SeerrAvailability.BLOCKLISTED
                isShow && done > 0 && (total <= 0 || done < total) -> SeerrAvailability.PARTIALLY_AVAILABLE
                library.status in LIBRARY_DOWNLOADED || (isShow && total > 0 && done >= total) -> SeerrAvailability.AVAILABLE
                else -> SeerrAvailability.PROCESSING
            }
        }
        return when (request?.status) {
            "pending" -> SeerrAvailability.PENDING
            "approved" -> SeerrAvailability.PROCESSING
            "available" -> SeerrAvailability.AVAILABLE
            "denied" -> SeerrAvailability.BLOCKLISTED
            else -> SeerrAvailability.UNKNOWN
        }
    }

    fun mediaInfo(
        tmdbId: Int,
        availability: SeerrAvailability,
        jellyfinId: UUID? = null,
        seasons: List<JsonObject>? = null,
    ): JsonObject =
        buildJsonObject {
            put("tmdbId", tmdbId)
            put("status", availability.status)
            jellyfinId?.let { put("jellyfinMediaId", it.toString()) }
            seasons?.let { put("seasons", JsonArray(it)) }
        }

    /** A title in a list: also reads as a Seerr movie, TV or search result */
    fun listItem(item: RkItem): JsonObject =
        buildJsonObject {
            put("id", item.tmdbId)
            put("mediaType", if (item.isMovie) "movie" else "tv")
            put("posterPath", imagePath(item.posterUrl))
            put("overview", item.overview)
            item.voteAverage?.let { put("voteAverage", it) }
            val date = item.releaseYear?.let { "$it-01-01" }
            if (item.isMovie) {
                put("title", item.title)
                put("originalTitle", item.title)
                put("releaseDate", date)
            } else {
                put("name", item.title)
                put("originalName", item.title)
                put("firstAirDate", date)
            }
            val availability = if (item.alreadyExists) SeerrAvailability.PROCESSING else SeerrAvailability.UNKNOWN
            put("mediaInfo", mediaInfo(item.tmdbId, availability))
        }

    /** A Seerr results page; Rawkoon's own page size is hidden by the caller */
    fun resultsPage(
        items: List<RkItem>,
        page: Int,
        totalPages: Int,
        totalResults: Int,
    ): JsonObject =
        buildJsonObject {
            put("page", page)
            put("totalPages", totalPages)
            put("totalResults", totalResults)
            put("results", JsonArray(items.map(::listItem)))
        }

    fun publicSettings(): JsonObject =
        buildJsonObject {
            put("initialized", true)
            put("movie4kEnabled", false)
            put("series4kEnabled", false)
            put("cacheImages", false)
        }

    fun user(name: String): JsonObject =
        buildJsonObject {
            put("id", USER_ID)
            put("username", name)
            put("permissions", PERMISSION_REQUEST)
        }

    fun genreSlider(genres: List<RkGenre>): JsonArray =
        JsonArray(
            genres.map {
                buildJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("backdrops", JsonArray(emptyList()))
                }
            },
        )

    fun watchProviders(providers: List<RkProvider>): JsonArray =
        JsonArray(
            providers.mapIndexed { index, provider ->
                buildJsonObject {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("logoPath", imagePath(provider.logoUrl))
                    put("displayPriority", index)
                }
            },
        )

    private fun genres(genres: List<RkGenre>) =
        JsonArray(
            genres.map {
                buildJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                }
            },
        )

    /** Cast with their photos; directors have no TMDB id in Rawkoon, so they get a stable negative one */
    private fun credits(credits: RkCredits?): JsonObject =
        buildJsonObject {
            put(
                "cast",
                JsonArray(
                    credits?.cast.orEmpty().map {
                        buildJsonObject {
                            put("id", it.id)
                            put("name", it.name)
                            put("character", it.character)
                            put("profilePath", imagePath(it.profileUrl))
                            put("mediaType", "person")
                        }
                    },
                ),
            )
            put(
                "crew",
                JsonArray(
                    credits?.directors.orEmpty().map {
                        buildJsonObject {
                            put("id", -(it.hashCode() and Int.MAX_VALUE) - 1)
                            put("name", it)
                            put("job", "Director")
                            put("mediaType", "person")
                        }
                    },
                ),
            )
        }

    private fun relatedVideos(trailer: RkTrailer?): JsonArray =
        trailer?.key?.takeIf { it.isNotBlank() }?.let { key ->
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("url", "https://www.youtube.com/watch?v=$key")
                        put("key", key)
                        put("name", trailer.name ?: "Trailer")
                        put("site", "YouTube")
                        put("type", "Trailer")
                    },
                ),
            )
        } ?: JsonArray(emptyList())

    /** The title Rawkoon would show: what the list said, else the English translation, else the original */
    fun titleOf(
        known: String?,
        details: RkDetails?,
    ): String? =
        known
            ?: details?.titleTranslations?.firstOrNull { it.languageCode == "en" }?.title?.takeIf { it.isNotBlank() }
            ?: details?.originalTitle

    private fun bestPoster(details: RkDetails?) =
        details
            ?.mediaStills
            ?.posters
            ?.maxByOrNull { it.voteAverage ?: 0.0 }
            ?.url

    fun movieDetails(
        tmdbId: Int,
        title: String?,
        posterUrl: String?,
        modal: RkModal,
        mediaInfo: JsonObject,
    ): JsonObject {
        val details = modal.details
        return buildJsonObject {
            put("id", tmdbId)
            put("title", title)
            put("originalTitle", details?.originalTitle)
            put("overview", details?.overview)
            put("tagline", details?.tagline)
            put("homepage", details?.homepage)
            put("imdbId", details?.externalIds?.imdbId)
            put("originalLanguage", details?.originalLanguage)
            put("status", details?.status)
            put("releaseDate", details?.releaseDate)
            details?.runtime?.let { put("runtime", it) }
            details?.voteAverage?.let { put("voteAverage", it) }
            put("backdropPath", imagePath(details?.primaryBackdropUrl))
            put("posterPath", imagePath(posterUrl ?: bestPoster(details)))
            put("genres", genres(details?.genres.orEmpty()))
            put("credits", credits(modal.credits))
            put("relatedVideos", relatedVideos(modal.trailer))
            put("mediaInfo", mediaInfo)
        }
    }

    /** One entry per season, with how much of it Rawkoon has downloaded */
    fun seasonStatuses(
        details: RkDetails?,
        episodes: RkLibraryEpisodes?,
        wholeShow: SeerrAvailability,
    ): List<JsonObject> =
        details?.seasons.orEmpty().map { season ->
            val done = episodes?.downloaded?.count { it.seasonNumber == season.seasonNumber } ?: 0
            val total = season.episodeCount ?: 0
            val status =
                when {
                    done > 0 && total > 0 && done >= total -> SeerrAvailability.AVAILABLE
                    done > 0 -> SeerrAvailability.PARTIALLY_AVAILABLE
                    wholeShow == SeerrAvailability.PENDING || wholeShow == SeerrAvailability.PROCESSING -> wholeShow
                    else -> SeerrAvailability.UNKNOWN
                }
            buildJsonObject {
                put("id", season.seasonNumber)
                put("seasonNumber", season.seasonNumber)
                put("name", season.name)
                put("episodeCount", season.episodeCount)
                put("status", status.status)
            }
        }

    fun tvDetails(
        tmdbId: Int,
        title: String?,
        posterUrl: String?,
        modal: RkModal,
        mediaInfo: JsonObject,
        seasons: List<JsonObject>,
    ): JsonObject {
        val details = modal.details
        return buildJsonObject {
            put("id", tmdbId)
            put("name", title)
            put("originalName", details?.originalTitle)
            put("overview", details?.overview)
            put("tagline", details?.tagline)
            put("homepage", details?.homepage)
            put("originalLanguage", details?.originalLanguage)
            put("type", details?.tvType)
            put("firstAirDate", details?.firstAirDate)
            put("lastAirDate", details?.lastAirDate)
            put("inProduction", details?.status in setOf("Returning Series", "In Production", "Planned", "Pilot"))
            details?.numberOfEpisodes?.let { put("numberOfEpisodes", it) }
            details?.numberOfSeasons?.let { put("numberOfSeason", it) }
            details?.voteAverage?.let { put("voteAverage", it) }
            put("episodeRunTime", JsonArray(details?.episodeRunTimes.orEmpty().map { JsonPrimitive(it) }))
            put("backdropPath", imagePath(details?.primaryBackdropUrl))
            put("posterPath", imagePath(posterUrl ?: bestPoster(details)))
            put("genres", genres(details?.genres.orEmpty()))
            put("credits", credits(modal.credits))
            put("relatedVideos", relatedVideos(modal.trailer))
            put("seasons", JsonArray(seasons))
            put("mediaInfo", mediaInfo)
        }
    }

    /** RT "93%" as 93, null when there is none */
    fun percent(text: String?): Int? = text?.trim()?.removeSuffix("%")?.toDoubleOrNull()?.toInt()

    fun movieRatings(
        rottenTomatoes: String?,
        imdbRating: String?,
        voteAverage: Double?,
    ): JsonObject? {
        val critics = percent(rottenTomatoes)
        val audience = imdbRating?.toDoubleOrNull()?.let { (it * 10).toInt() } ?: voteAverage?.let { (it * 10).toInt() }
        if (critics == null && audience == null) return null
        return buildJsonObject {
            critics?.let { put("criticsScore", it) }
            audience?.let { put("audienceScore", it) }
        }
    }

    fun tvRatings(rottenTomatoes: String?): JsonObject? =
        percent(rottenTomatoes)?.let { buildJsonObject { put("criticsScore", it) } }

    /** Seerr request status: 1 pending, 2 approved, 3 declined, 5 completed */
    private fun requestStatus(status: String): Int =
        when (status) {
            "approved" -> 2
            "denied" -> 3
            "available" -> 5
            else -> 1
        }

    fun request(request: RkRequest): JsonObject =
        buildJsonObject {
            put("id", request.id)
            put("status", requestStatus(request.status))
            put("type", if (request.type == "movie") "movie" else "tv")
            put("createdAt", request.createdAt)
            put("updatedAt", request.decidedAt ?: request.createdAt)
            put("media", buildJsonObject { request.tmdbId?.let { put("tmdbId", it) } })
            put("requestedBy", user(request.requestedBy?.name.orEmpty()))
        }

    fun requestsPage(requests: List<RkRequest>): JsonObject =
        buildJsonObject {
            put(
                "pageInfo",
                buildJsonObject {
                    put("pages", 1)
                    put("pageSize", requests.size)
                    put("results", requests.size)
                    put("page", 1)
                },
            )
            put("results", JsonArray(requests.map(::request)))
        }

    fun error(message: String): JsonObject = buildJsonObject { put("message", message) }
}
