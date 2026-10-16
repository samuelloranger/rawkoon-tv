package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.model.TvResult
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.rawkoon.LibraryEntry
import com.github.damontecres.wholphin.rawkoon.RequestEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class SeerrJsonTest {
    private val seerr = Serializer.kotlinxSerializationJson
    private val rawkoon = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> decode(element: JsonElement): T =
        seerr.decodeFromString(seerr.encodeToString(JsonElement.serializer(), element))

    private fun lib(
        status: String,
        episodes: Int? = null,
        downloaded: Int? = null,
    ) = LibraryEntry(1, status, episodes, downloaded)

    private fun req(status: String) = RequestEntry(1, status)

    @Test
    fun a_list_movie_reads_as_a_seerr_movie_result() {
        val item = RkItem(603, "movie", "The Matrix", 1999, "https://image.tmdb.org/t/p/w342/poster.jpg", "Neo", 8.2, alreadyExists = false)
        val movie = decode<MovieResult>(SeerrJson.listItem(item))
        assertEquals(603, movie.id)
        assertEquals("movie", movie.mediaType)
        assertEquals("The Matrix", movie.title)
        assertEquals("/poster.jpg", movie.posterPath)
        assertEquals("1999-01-01", movie.releaseDate)
        assertEquals(8.2, movie.voteAverage?.toDouble() ?: 0.0, 0.001)
        assertEquals(SeerrAvailability.UNKNOWN.status, movie.mediaInfo?.status)
    }

    @Test
    fun a_list_show_reads_as_a_seerr_tv_result_and_a_tracked_title_is_in_progress() {
        val item = RkItem(1399, "tv", "Thrones", 2011, null, null, null, alreadyExists = true, libraryId = 4)
        val tv = decode<TvResult>(SeerrJson.listItem(item))
        assertEquals("Thrones", tv.name)
        assertEquals("2011-01-01", tv.firstAirDate)
        assertNull(tv.posterPath)
        assertEquals(SeerrAvailability.PROCESSING.status, tv.mediaInfo?.status)
    }

    @Test
    fun tmdb_image_urls_become_paths_the_app_can_prefix() {
        assertEquals("/a/b.jpg", SeerrJson.imagePath("https://image.tmdb.org/t/p/w1280/a/b.jpg"))
        assertEquals("/p.jpg", SeerrJson.imagePath("https://image.tmdb.org/t/p/w92/p.jpg"))
        assertNull(SeerrJson.imagePath(null))
        assertNull(SeerrJson.imagePath("https://elsewhere.example.test/p.jpg"))
    }

    @Test
    fun availability_follows_the_library_entry_first_and_then_the_request() {
        assertEquals(SeerrAvailability.UNKNOWN, SeerrJson.availabilityOf(null, null, false))
        assertEquals(SeerrAvailability.AVAILABLE, SeerrJson.availabilityOf(lib("downloaded"), null, false))
        assertEquals(SeerrAvailability.AVAILABLE, SeerrJson.availabilityOf(lib("upgrading"), req("approved"), false))
        assertEquals(SeerrAvailability.PROCESSING, SeerrJson.availabilityOf(lib("downloading"), null, false))
        assertEquals(SeerrAvailability.PROCESSING, SeerrJson.availabilityOf(lib("wanted"), req("pending"), false))
        assertEquals(SeerrAvailability.BLOCKLISTED, SeerrJson.availabilityOf(lib("skipped"), null, false))
        assertEquals(SeerrAvailability.PENDING, SeerrJson.availabilityOf(null, req("pending"), false))
        assertEquals(SeerrAvailability.PROCESSING, SeerrJson.availabilityOf(null, req("approved"), false))
        assertEquals(SeerrAvailability.AVAILABLE, SeerrJson.availabilityOf(null, req("available"), false))
        assertEquals(SeerrAvailability.BLOCKLISTED, SeerrJson.availabilityOf(null, req("denied"), false))
    }

    @Test
    fun a_show_with_some_episodes_is_partially_available_and_with_all_of_them_available() {
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, SeerrJson.availabilityOf(lib("downloading", 10, 4), null, true))
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, SeerrJson.availabilityOf(lib("returning", 10, 4), null, true))
        assertEquals(SeerrAvailability.AVAILABLE, SeerrJson.availabilityOf(lib("returning", 10, 10), null, true))
        assertEquals(SeerrAvailability.PROCESSING, SeerrJson.availabilityOf(lib("wanted", 10, 0), null, true))
        // A movie has no episodes: a stray count never makes it partial
        assertEquals(SeerrAvailability.PROCESSING, SeerrJson.availabilityOf(lib("wanted", 10, 4), null, false))
    }

    private val movieModal =
        """
        {"trailer":{"key":"abc123","name":"Official Trailer"},
         "ratings":{"imdb_rating":"8.7","rotten_tomatoes":"83%","metacritic":null},
         "credits":{"cast":[{"id":6384,"name":"Keanu Reeves","character":"Neo","profile_url":"https://image.tmdb.org/t/p/w185/k.jpg"}],"directors":["Lana Wachowski"]},
         "details":{"runtime":136,"overview":"A hacker.","vote_average":8.2,"release_date":"1999-03-30","tagline":"Welcome","homepage":"https://example.test",
           "genres":[{"id":28,"name":"Action"}],"status":"Released","original_title":"The Matrix",
           "title_translations":[{"language_code":"fr","title":"Matrice"},{"language_code":"en","title":"The Matrix"}],
           "original_language":"en","external_ids":{"imdb_id":"tt0133093"},
           "primary_backdrop_url":"https://image.tmdb.org/t/p/w1280/back.jpg",
           "media_stills":{"posters":[{"url":"https://image.tmdb.org/t/p/w342/low.jpg","vote_average":3.0},{"url":"https://image.tmdb.org/t/p/w342/high.jpg","vote_average":5.1}]},
           "episode_run_times":[],"seasons":[]},
         "library_episodes":null}
        """.trimIndent()

    @Test
    fun movie_details_carry_the_title_credits_trailer_and_media_info() {
        val modal = rawkoon.decodeFromString<RkModal>(movieModal)
        val jellyfinId = UUID.randomUUID()
        val title = SeerrJson.titleOf(null, modal.details)
        val movie =
            decode<MovieDetails>(
                SeerrJson.movieDetails(603, title, null, modal, SeerrJson.mediaInfo(603, SeerrAvailability.AVAILABLE, jellyfinId)),
            )
        assertEquals("The Matrix", movie.title)
        assertEquals("/back.jpg", movie.backdropPath)
        assertEquals("/high.jpg", movie.posterPath)
        assertEquals(136.0, movie.runtime?.toDouble() ?: 0.0, 0.001)
        assertEquals(listOf("Action"), movie.genres?.mapNotNull { it.name })
        assertEquals(
            "Neo",
            movie.credits
                ?.cast
                ?.single()
                ?.character,
        )
        assertEquals(
            "/k.jpg",
            movie.credits
                ?.cast
                ?.single()
                ?.profilePath,
        )
        assertEquals(
            "Lana Wachowski",
            movie.credits
                ?.crew
                ?.single()
                ?.name,
        )
        val trailer = movie.relatedVideos?.single()
        assertEquals(RelatedVideo.Type.TRAILER, trailer?.type)
        assertEquals("https://www.youtube.com/watch?v=abc123", trailer?.url)
        assertEquals(SeerrAvailability.AVAILABLE.status, movie.mediaInfo?.status)
        assertEquals(jellyfinId.toString(), movie.mediaInfo?.jellyfinMediaId)
    }

    @Test
    fun the_title_prefers_what_the_list_said_then_english_then_the_original() {
        val details = rawkoon.decodeFromString<RkModal>(movieModal).details
        assertEquals("From list", SeerrJson.titleOf("From list", details))
        assertEquals("The Matrix", SeerrJson.titleOf(null, details))
        assertEquals("Original", SeerrJson.titleOf(null, RkDetails(originalTitle = "Original")))
        assertNull(SeerrJson.titleOf(null, null))
    }

    @Test
    fun tv_details_list_seasons_with_their_download_state() {
        val modal =
            rawkoon.decodeFromString<RkModal>(
                """
                {"details":{"overview":"Winter","first_air_date":"2011-04-17","status":"Ended","number_of_seasons":2,"number_of_episodes":20,
                  "original_title":"Game","genres":[],"title_translations":[],
                  "seasons":[{"season_number":1,"name":"Season 1","episode_count":10},{"season_number":2,"name":"Season 2","episode_count":10}]},
                 "library_episodes":{"in_library":true,"downloaded":[
                   {"season_number":1,"episode_number":1},{"season_number":1,"episode_number":2},{"season_number":1,"episode_number":3},{"season_number":1,"episode_number":4},{"season_number":1,"episode_number":5},
                   {"season_number":1,"episode_number":6},{"season_number":1,"episode_number":7},{"season_number":1,"episode_number":8},{"season_number":1,"episode_number":9},{"season_number":1,"episode_number":10},
                   {"season_number":2,"episode_number":1}]}}
                """.trimIndent(),
            )
        val seasons = SeerrJson.seasonStatuses(modal.details, modal.libraryEpisodes, SeerrAvailability.PARTIALLY_AVAILABLE)
        val tv =
            decode<TvDetails>(
                SeerrJson.tvDetails(
                    1399,
                    "Game",
                    "https://image.tmdb.org/t/p/w342/p.jpg",
                    modal,
                    SeerrJson.mediaInfo(1399, SeerrAvailability.PARTIALLY_AVAILABLE, null, seasons),
                    seasons,
                ),
            )
        assertEquals("Game", tv.name)
        assertEquals("/p.jpg", tv.posterPath)
        assertEquals(2, tv.numberOfSeason)
        assertEquals(20, tv.numberOfEpisodes)
        assertEquals(listOf(1, 2), tv.seasons?.map { it.seasonNumber })
        assertEquals(
            listOf(SeerrAvailability.AVAILABLE.status, SeerrAvailability.PARTIALLY_AVAILABLE.status),
            tv.seasons?.map { it.status },
        )
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE.status, tv.mediaInfo?.status)
        assertNotNull(tv.mediaInfo?.seasons)
    }

    @Test
    fun an_untouched_season_of_a_requested_show_takes_the_show_state() {
        val details = RkDetails(seasons = listOf(RkSeason(1, "S1", 8)))
        val status = SeerrJson.seasonStatuses(details, RkLibraryEpisodes(), SeerrAvailability.PENDING).single()
        assertEquals(SeerrAvailability.PENDING.status, decode<com.github.damontecres.wholphin.api.seerr.model.Season>(status).status)
    }

    @Test
    fun ratings_are_built_from_what_is_known() {
        assertEquals(83, SeerrJson.percent("83%"))
        assertNull(SeerrJson.percent(null))
        assertNull(SeerrJson.percent("n/a"))
        val both = SeerrJson.movieRatings("83%", "8.7", 8.2)!!
        assertEquals("""{"criticsScore":83,"audienceScore":87}""", Json.encodeToString(JsonElement.serializer(), both))
        val vote = SeerrJson.movieRatings(null, null, 7.8)!!
        assertEquals("""{"audienceScore":78}""", Json.encodeToString(JsonElement.serializer(), vote))
        assertNull(SeerrJson.movieRatings(null, null, null))
        assertNull(SeerrJson.tvRatings(null))
        assertNotNull(SeerrJson.tvRatings("90%"))
    }

    @Test
    fun media_info_round_trips() {
        val info = decode<MediaInfo>(SeerrJson.mediaInfo(5, SeerrAvailability.PENDING))
        assertEquals(5, info.tmdbId)
        assertEquals(2, info.status)
        assertNull(info.jellyfinMediaId)
    }
}
