package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.data.objects
import cloud.rawkoon.tv.data.optDoubleOrNull
import cloud.rawkoon.tv.data.optIntOrNull
import cloud.rawkoon.tv.data.optStringOrNull
import org.json.JSONObject

// Rawkoon's answers as the models: pure, and called off the main thread by RawkoonDiscover.

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
        RequestEntry(it.optInt("id"), it.optString("status"), it.optStringOrNull("deny_reason"), it.optBoolean("mine"))
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
