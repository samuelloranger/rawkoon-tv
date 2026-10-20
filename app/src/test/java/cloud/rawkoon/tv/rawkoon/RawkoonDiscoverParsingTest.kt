package cloud.rawkoon.tv.rawkoon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shapes are the ones Rawkoon's routes answer with (explore, tmdb-search, modal, requests, dashboard). */
class RawkoonDiscoverParsingTest {
    private val page = JSONObject(
        """
        {"items":[
          {"id":"movie-101","tmdb_id":101,"media_type":"movie","title":"Example Movie","release_year":2024,
           "poster_url":"https://img.example.com/t/p/w342/a.jpg","overview":"A movie.","vote_average":7.25,
           "service":"prowlarr","already_exists":true,"can_add":true,"source_id":null,"library_id":12},
          {"id":"tv-202","tmdb_id":202,"media_type":"tv","title":"Example Show","release_year":null,
           "poster_url":null,"overview":null,"vote_average":null,"already_exists":false,"library_id":null},
          {"id":"person-1","tmdb_id":303,"media_type":"person","title":"Someone"},
          {"id":"tv-0","tmdb_id":0,"media_type":"tv","title":"No id"}
        ],"page":2,"total_pages":9}
        """,
    )

    @Test
    fun `an explore page keeps movies and shows and drops what is neither`() {
        val parsed = RawkoonDiscover.parsePage(page)
        assertEquals(2, parsed.page)
        assertEquals(9, parsed.totalPages)
        assertEquals(listOf(101, 202), parsed.items.map { it.tmdbId })
        val movie = parsed.items[0]
        assertTrue(movie.isMovie)
        assertEquals(2024, movie.year)
        assertEquals(12, movie.libraryId)
        assertEquals(7.25, movie.voteAverage!!, 0.001)
        val show = parsed.items[1]
        assertFalse(show.isMovie)
        assertNull(show.year)
        assertNull(show.posterUrl)
        assertNull(show.libraryId)
    }

    @Test
    fun `a search answer carries the library state and no paging`() {
        val parsed = RawkoonDiscover.parsePage(
            JSONObject("""{"enabled":true,"items":[{"tmdb_id":5,"media_type":"movie","title":"T","library_id":3,"library_status":"wanted"}]}"""),
        )
        assertEquals("wanted", parsed.items.single().libraryStatus)
        assertEquals(1, parsed.page)
        assertEquals(1, parsed.totalPages)
    }

    @Test
    fun `genres without a name are skipped`() {
        val genres = RawkoonDiscover.parseGenres(JSONObject("""{"genres":[{"id":28,"name":"Action"},{"id":1,"name":""},{"id":35,"name":"Comedy"}]}"""))
        assertEquals(listOf(Genre(28, "Action"), Genre(35, "Comedy")), genres)
    }

    @Test
    fun `a show's modal gives its span, counts, best logo, backdrop and cast`() {
        val modal = RawkoonDiscover.parseModal(
            JSONObject(
                """
                {"watchlist_status":false,"trailer":{"key":null,"name":null},
                 "credits":{"cast":[{"id":7,"name":"Actor One","character":"Lead","profile_url":"https://img.example.com/p.jpg"},
                                    {"id":8,"name":"Actor Two","character":null,"profile_url":null}],"directors":["Director"]},
                 "details":{"runtime":null,"overview":"A show.","vote_average":8.4,"number_of_seasons":3,"number_of_episodes":24,
                   "release_date":null,"tagline":"","genres":[{"id":18,"name":"Drama"},{"id":80,"name":"Crime"}],
                   "first_air_date":"2021-05-01","last_air_date":"2024-06-09","status":"Returning Series",
                   "primary_backdrop_url":"https://img.example.com/b.jpg","episode_run_times":[48],
                   "media_stills":{"backdrops":[],"posters":[],"logos":[{"url":"https://img.example.com/t/p/w185/l1.png"},{"url":"https://img.example.com/t/p/w185/l2.png"}]}},
                 "library_episodes":{"in_library":false,"downloaded":[]}}
                """,
            ),
        )
        assertEquals(2021, modal.year)
        assertEquals(2024, modal.endYear)
        assertTrue(modal.continuing)
        assertEquals(3, modal.seasons)
        assertEquals(24, modal.episodes)
        assertEquals(48, modal.runtimeMinutes)
        assertEquals(listOf("Drama", "Crime"), modal.genres)
        assertNull(modal.tagline)
        assertEquals("https://img.example.com/t/p/w185/l1.png", modal.logoUrl)
        assertEquals("https://img.example.com/b.jpg", modal.backdropUrl)
        assertEquals(listOf("Actor One", "Actor Two"), modal.cast.map { it.name })
        assertNull(modal.cast[1].character)
    }

    @Test
    fun `a movie's modal takes its runtime and year from the details`() {
        val modal = RawkoonDiscover.parseModal(JSONObject("""{"details":{"runtime":97,"release_date":"2023-03-04","genres":[],"vote_average":0}}"""))
        assertEquals(97, modal.runtimeMinutes)
        assertEquals(2023, modal.year)
        assertNull(modal.voteAverage)
        assertNull(modal.logoUrl)
        assertTrue(modal.cast.isEmpty())
    }

    @Test
    fun `a status has a library entry, a request or neither`() {
        val both = RawkoonDiscover.parseStatus(
            JSONObject(
                """{"library":{"id":12,"status":"downloading","episode_count":10,"downloaded_episode_count":4},
                    "request":{"id":5,"status":"denied","deny_reason":"Not now"}}""",
            ),
        )
        assertEquals(LibraryEntry("downloading", 10, 4), both.library)
        assertEquals(RequestEntry(5, "denied", "Not now"), both.request)
        val none = RawkoonDiscover.parseStatus(JSONObject("""{"library":null,"request":null}"""))
        assertNull(none.library)
        assertNull(none.request)
        val movie = RawkoonDiscover.parseStatus(JSONObject("""{"library":{"id":1,"status":"downloaded","episode_count":null,"downloaded_episode_count":null},"request":null}"""))
        assertNull(movie.library!!.episodeCount)
    }

    @Test
    fun `the upcoming list is empty when the server has it disabled`() {
        assertTrue(RawkoonDiscover.parseUpcoming(JSONObject("""{"enabled":false,"items":[]}""")).isEmpty())
    }

    @Test
    fun `upcoming entries keep episode ranges and skip other media types`() {
        val entries = RawkoonDiscover.parseUpcoming(
            JSONObject(
                """
                {"enabled":true,"items":[
                  {"id":"tv-9-s2e3","title":"Show","media_type":"tv","release_date":"2026-11-02","poster_url":"https://img.example.com/p.jpg",
                   "backdrop_url":null,"overview":"x","tmdb_url":"https://www.themoviedb.org/tv/9","providers":[],"library_id":4,
                   "season_number":2,"episode_number":3,"episode_number_end":4,"episode_count":2},
                  {"id":"movie-1","title":"Movie","media_type":"movie","release_date":"2026-12-01","library_id":null},
                  {"id":"book-1","title":"Book","media_type":"book","release_date":"2026-12-01"}
                ]}
                """,
            ),
        )
        assertEquals(2, entries.size)
        assertEquals(UpcomingEntry("tv-9-s2e3", "Show", false, "2026-11-02", "https://img.example.com/p.jpg", null, "x", "https://www.themoviedb.org/tv/9", 4, 2, 3, 4, 2), entries[0])
        assertNull(entries[1].libraryId)
        assertTrue(entries[1].isMovie)
    }

    @Test
    fun `requests parse with their requester and dates`() {
        val requests = RawkoonDiscover.parseRequests(
            JSONObject(
                """
                {"requests":[{"id":5,"tmdb_id":101,"type":"movie","title":"Example Movie","author":null,"poster_url":null,"year":2024,
                  "status":"approved","requested_by":{"id":"user-1","name":"Someone"},"quality_profile_id":1,"library_media_id":null,
                  "deny_reason":null,"created_at":"2026-10-01T10:00:00.000Z","decided_at":null},
                 {"id":6,"tmdb_id":null,"type":"book","title":"A Book","status":"pending","requested_by":{"id":"user-1","name":null}}]}
                """,
            ),
        )
        assertEquals(2, requests.size)
        assertEquals(RequestSummary(5, 101, true, "movie", "Example Movie", null, 2024, "approved", "user-1", "2026-10-01T10:00:00.000Z", null), requests[0])
        assertNull(requests[1].tmdbId)
        assertEquals("book", requests[1].type)
    }
}
