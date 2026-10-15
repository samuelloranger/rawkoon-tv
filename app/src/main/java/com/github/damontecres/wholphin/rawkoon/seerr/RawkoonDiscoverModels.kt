package com.github.damontecres.wholphin.rawkoon.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A title in a Rawkoon list (explore, discover, search, similar) */
@Serializable
data class RkItem(
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("already_exists") val alreadyExists: Boolean = false,
    @SerialName("library_id") val libraryId: Int? = null,
) {
    val isMovie: Boolean get() = mediaType == "movie"
}

/** `/explore/:category`, `/discover`, `/similar/:id` and `/tmdb-search` all answer with a list of [RkItem] */
@Serializable
data class RkItemPage(
    val items: List<RkItem> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int? = null,
)

@Serializable
data class RkGenre(
    val id: Int,
    val name: String,
)

@Serializable
data class RkGenres(
    val genres: List<RkGenre> = emptyList(),
)

@Serializable
data class RkProvider(
    val id: Int,
    val name: String,
    @SerialName("logo_url") val logoUrl: String? = null,
)

@Serializable
data class RkProviders(
    val providers: List<RkProvider> = emptyList(),
)

@Serializable
data class RkTrailer(
    val key: String? = null,
    val name: String? = null,
)

@Serializable
data class RkRatings(
    @SerialName("imdb_rating") val imdbRating: String? = null,
    @SerialName("rotten_tomatoes") val rottenTomatoes: String? = null,
)

@Serializable
data class RkCast(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_url") val profileUrl: String? = null,
)

@Serializable
data class RkCredits(
    val cast: List<RkCast> = emptyList(),
    val directors: List<String> = emptyList(),
)

@Serializable
data class RkTranslation(
    @SerialName("language_code") val languageCode: String = "",
    val title: String = "",
)

@Serializable
data class RkExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class RkStill(
    val url: String,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class RkStills(
    val posters: List<RkStill> = emptyList(),
)

@Serializable
data class RkSeason(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
)

@Serializable
data class RkDetails(
    val runtime: Int? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val tagline: String? = null,
    val genres: List<RkGenre> = emptyList(),
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val status: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("title_translations") val titleTranslations: List<RkTranslation> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    val homepage: String? = null,
    @SerialName("external_ids") val externalIds: RkExternalIds? = null,
    @SerialName("primary_backdrop_url") val primaryBackdropUrl: String? = null,
    @SerialName("media_stills") val mediaStills: RkStills? = null,
    @SerialName("tv_type") val tvType: String? = null,
    @SerialName("episode_run_times") val episodeRunTimes: List<Int> = emptyList(),
    val seasons: List<RkSeason> = emptyList(),
)

@Serializable
data class RkDownloadedEpisode(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
)

@Serializable
data class RkLibraryEpisodes(
    @SerialName("in_library") val inLibrary: Boolean = false,
    val downloaded: List<RkDownloadedEpisode> = emptyList(),
)

/** `/modal/:mediaType/:tmdbId`: everything a title page shows */
@Serializable
data class RkModal(
    val trailer: RkTrailer? = null,
    val ratings: RkRatings? = null,
    val credits: RkCredits? = null,
    val details: RkDetails? = null,
    @SerialName("library_episodes") val libraryEpisodes: RkLibraryEpisodes? = null,
)

@Serializable
data class RkRequestUser(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class RkRequest(
    val id: Int,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    val type: String = "",
    val title: String = "",
    @SerialName("poster_url") val posterUrl: String? = null,
    val year: Int? = null,
    val status: String = "",
    @SerialName("requested_by") val requestedBy: RkRequestUser? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
)

@Serializable
data class RkRequests(
    val requests: List<RkRequest> = emptyList(),
)
