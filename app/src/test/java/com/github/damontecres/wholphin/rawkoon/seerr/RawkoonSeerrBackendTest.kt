package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.rawkoon.FakeRawkoon
import com.github.damontecres.wholphin.rawkoon.RawkoonClient
import com.github.damontecres.wholphin.rawkoon.RawkoonConfig
import com.github.damontecres.wholphin.rawkoon.RawkoonHttp
import com.github.damontecres.wholphin.rawkoon.RawkoonSession
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

/**
 * Runs the real generated Seerr client against the interceptor, with a fake Rawkoon behind it
 */
class RawkoonSeerrBackendTest {
    private val session = mockk<RawkoonSession>(relaxed = true)
    private val lookup = mockk<JellyfinTmdbLookup>()

    private class Harness(
        val fake: FakeRawkoon,
        val api: SeerrApiClient,
    )

    private fun harness(
        routes: Map<String, List<Pair<Int, String>>>,
        token: String = "TOKEN",
    ): Harness {
        val fake = FakeRawkoon(routes)
        val settings = mockk<RawkoonSettingsRepository>()
        coEvery { settings.current() } returns RawkoonConfig("https://rawkoon.example.test", token, "Alex")
        val client = RawkoonClient(RawkoonHttp(OkHttpClient.Builder().addInterceptor(fake).build()), settings, session)
        val backend = RawkoonSeerrBackend(client, settings, lookup).also { it.languageTag = { "en-US" } }
        val seerr =
            SeerrApiClient(
                "${RawkoonSeerrInterceptor.BASE_URL}/api/v1",
                null,
                OkHttpClient.Builder().addInterceptor(RawkoonSeerrInterceptor(backend)).build(),
            )
        return Harness(fake, seerr)
    }

    private fun route(vararg responses: Pair<Int, String>) = responses.toList()

    private fun item(
        id: Int,
        type: String = "movie",
        exists: Boolean = false,
    ) = """{"id":"$type-$id","tmdb_id":$id,"media_type":"$type","title":"Title $id","release_year":2001,"poster_url":"https://image.tmdb.org/t/p/w342/p$id.jpg","overview":"o","vote_average":7.5,"already_exists":$exists,"library_id":${if (exists) "9" else "null"}}"""

    private fun items(
        range: IntRange,
        type: String = "movie",
    ) = range.joinToString(",") { item(it, type) }

    @Test
    fun the_signed_in_user_may_request_and_nothing_more() =
        runBlocking {
            val h = harness(emptyMap())
            val user = h.api.usersApi.authMeGet()
            assertEquals("Alex", user.username)
            assertTrue(user.hasPermission(SeerrPermission.REQUEST))
            assertTrue(!user.hasPermission(SeerrPermission.MANAGE_REQUESTS))
            assertTrue(!user.hasPermission(SeerrPermission.REQUEST_ADVANCED))
            assertEquals(false, h.api.settingsApi.settingsPublicGet().cacheImages)
            assertTrue(h.fake.requests.isEmpty())
        }

    @Test
    fun the_first_discover_page_is_cut_from_the_first_rawkoon_page() =
        runBlocking {
            val h = harness(mapOf("api/medias/discover" to route(200 to """{"items":[${items(1..48)}],"page":1,"total_pages":3,"total_results":120}""")))
            val page = h.api.searchApi.discoverMoviesGet(page = 1)
            assertEquals((1..20).toList(), page.results?.map { it.id })
            assertEquals(120, page.totalResults)
            assertEquals(6, page.totalPages)
            val url = h.fake.requests.single().url
            assertEquals("movie", url.queryParameter("type"))
            assertEquals("popularity.desc", url.queryParameter("sort_by"))
            assertEquals("1", url.queryParameter("page"))
            assertEquals("Bearer TOKEN", h.fake.requests.single().header("Authorization"))
        }

    @Test
    fun a_page_that_straddles_two_rawkoon_pages_joins_them() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/discover" to
                            route(
                                200 to """{"items":[${items(1..48)}],"page":1,"total_pages":3,"total_results":120}""",
                                200 to """{"items":[${items(49..96)}],"page":2,"total_pages":3,"total_results":120}""",
                            ),
                    ),
                )
            // Seerr page 3 is titles 41..60: the last 8 of Rawkoon's first page and the first 12 of its second
            val page = h.api.searchApi.discoverMoviesGet(page = 3)
            assertEquals((41..60).toList(), page.results?.map { it.id })
            assertEquals(listOf("1", "2"), h.fake.requests.map { it.url.queryParameter("page") })
        }

    @Test
    fun genre_and_sort_filters_reach_rawkoon() =
        runBlocking {
            val h = harness(mapOf("api/medias/discover" to route(200 to """{"items":[${items(1..5, "tv")}],"page":1,"total_pages":1,"total_results":5}""")))
            val page = h.api.searchApi.discoverTvGet(page = 1, genre = "18,35", sortBy = "vote_average.desc", watchProviders = "8")
            assertEquals(5, page.results?.size)
            val url = h.fake.requests.single().url
            assertEquals("tv", url.queryParameter("type"))
            assertEquals("18", url.queryParameter("genre_id"))
            assertEquals("vote_average.desc", url.queryParameter("sort_by"))
            assertEquals("8", url.queryParameter("provider_id"))
        }

    @Test
    fun sorts_without_a_rawkoon_equivalent_fall_back_to_popularity() {
        assertEquals("popularity.desc", RawkoonSeerrBackend.rawkoonSort(null, true))
        assertEquals("popularity.desc", RawkoonSeerrBackend.rawkoonSort("original_title.asc", true))
        assertEquals("primary_release_date.desc", RawkoonSeerrBackend.rawkoonSort("release_date.desc", true))
        assertEquals("first_air_date.desc", RawkoonSeerrBackend.rawkoonSort("first_air_date.desc", false))
        assertEquals("popularity.desc", RawkoonSeerrBackend.rawkoonSort("release_date.desc", false))
        assertEquals("vote_average.asc", RawkoonSeerrBackend.rawkoonSort("vote_average.asc", false))
    }

    @Test
    fun trending_and_upcoming_use_the_explore_categories() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/explore/trending" to route(200 to """{"items":[${item(1)},${item(2, "tv")}],"page":2,"total_pages":9}"""),
                        "api/medias/explore/upcoming_movies" to route(200 to """{"items":[${item(3, exists = true)}],"page":1,"total_pages":1}"""),
                        "api/medias/explore/on_the_air" to route(200 to """{"items":[${item(4, "tv")}],"page":1,"total_pages":1}"""),
                    ),
                )
            val trending = h.api.searchApi.discoverTrendingGet(page = 2)
            assertEquals(listOf("movie", "tv"), trending.results?.map { it.mediaType })
            assertEquals(9, trending.totalPages)
            assertEquals("2", h.fake.requests.single().url.queryParameter("page"))
            val upcoming = h.api.searchApi.discoverMoviesUpcomingGet(page = 1)
            assertEquals(SeerrAvailability.PROCESSING.status, upcoming.results?.single()?.mediaInfo?.status)
            assertEquals(listOf(4), h.api.searchApi.discoverTvUpcomingGet(page = 1).results?.map { it.id })
        }

    @Test
    fun search_asks_rawkoon_once_and_skips_short_queries_and_later_pages() =
        runBlocking {
            val h = harness(mapOf("api/medias/tmdb-search" to route(200 to """{"enabled":true,"items":[${item(1)},${item(2, "tv")}]}""")))
            val result = h.api.searchApi.searchGet(query = "matrix", page = 1)
            assertEquals(listOf(1, 2), result.results?.map { it.id })
            assertEquals("matrix", h.fake.requests.single().url.queryParameter("q"))
            assertEquals(0, h.api.searchApi.searchGet(query = "m", page = 1).results?.size)
            assertEquals(0, h.api.searchApi.searchGet(query = "matrix", page = 2).results?.size)
            assertEquals(1, h.fake.requests.size)
        }

    @Test
    fun genres_become_a_slider_without_images() =
        runBlocking {
            val h = harness(mapOf("api/medias/genres" to route(200 to """{"genres":[{"id":28,"name":"Action"},{"id":35,"name":"Comedy"}]}""")))
            val genres = h.api.searchApi.discoverGenresliderMovieGet()
            assertEquals(listOf("Action", "Comedy"), genres.map { it.name })
            assertEquals(listOf(28, 35), genres.map { it.id })
            assertEquals("movie", h.fake.requests.single().url.queryParameter("type"))
        }

    @Test
    fun streaming_providers_become_watch_providers() =
        runBlocking {
            val h = harness(mapOf("api/medias/streaming-providers" to route(200 to """{"providers":[{"id":8,"name":"Netflix","logo_url":"https://image.tmdb.org/t/p/w92/n.jpg"}],"region":"CA"}""")))
            val providers = h.api.otherApi.watchprovidersTvGet("US")
            assertEquals(listOf("Netflix"), providers.map { it.name })
            assertEquals("tv", h.fake.requests.single().url.queryParameter("type"))
        }

    private val matrixModal =
        """{"trailer":{"key":"k","name":"T"},"ratings":{"imdb_rating":"8.7","rotten_tomatoes":"83%"},
            "credits":{"cast":[],"directors":[]},
            "details":{"overview":"o","genres":[],"title_translations":[{"language_code":"en","title":"The Matrix"}],"seasons":[],"release_date":"1999-03-30"},
            "library_episodes":null}"""

    @Test
    fun a_downloaded_movie_links_to_its_jellyfin_item() =
        runBlocking {
            val jellyfinId = UUID.randomUUID()
            coEvery { lookup.find(603, "The Matrix", 1999, false) } returns jellyfinId
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/movie/603" to route(200 to matrixModal),
                        "api/requests/status" to route(200 to """{"library":{"id":1,"status":"downloaded","episode_count":null,"downloaded_episode_count":null},"request":null}"""),
                    ),
                )
            val movie = h.api.moviesApi.movieMovieIdGet(603)
            assertEquals("The Matrix", movie.title)
            assertEquals(SeerrAvailability.AVAILABLE.status, movie.mediaInfo?.status)
            assertEquals(jellyfinId.toString(), movie.mediaInfo?.jellyfinMediaId)
            val status = h.fake.requests.first { it.url.encodedPath.endsWith("api/requests/status") }
            assertEquals("603", status.url.queryParameter("tmdb_id"))
            assertEquals("movie", status.url.queryParameter("type"))
        }

    @Test
    fun a_pending_request_shows_as_pending_without_a_jellyfin_lookup() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/movie/603" to route(200 to matrixModal),
                        "api/requests/status" to route(200 to """{"library":null,"request":{"id":5,"status":"pending","deny_reason":null}}"""),
                    ),
                )
            val movie = h.api.moviesApi.movieMovieIdGet(603)
            assertEquals(SeerrAvailability.PENDING.status, movie.mediaInfo?.status)
            coVerify(exactly = 0) { lookup.find(any(), any(), any(), any()) }
        }

    @Test
    fun a_title_page_still_opens_when_the_status_call_fails() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/movie/603" to route(200 to matrixModal),
                        "api/requests/status" to route(500 to "{}"),
                    ),
                )
            assertEquals(SeerrAvailability.UNKNOWN.status, h.api.moviesApi.movieMovieIdGet(603).mediaInfo?.status)
        }

    @Test
    fun a_show_reads_its_seasons_from_the_modal() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/tv/1399" to
                            route(
                                200 to """{"details":{"overview":"o","genres":[],"title_translations":[{"language_code":"en","title":"Thrones"}],"seasons":[{"season_number":1,"name":"S1","episode_count":10}]},"library_episodes":{"in_library":false,"downloaded":[]}}""",
                            ),
                        "api/requests/status" to route(200 to """{"library":null,"request":null}"""),
                    ),
                )
            val tv = h.api.tvApi.tvTvIdGet(1399)
            assertEquals("Thrones", tv.name)
            assertEquals(listOf(1), tv.seasons?.map { it.seasonNumber })
            assertEquals("show", h.fake.requests.first { it.url.encodedPath.endsWith("status") }.url.queryParameter("type"))
        }

    @Test
    fun similar_and_recommended_titles_come_from_the_same_rawkoon_call() =
        runBlocking {
            val h = harness(mapOf("api/medias/similar/603" to route(200 to """{"items":[${items(1..3)}]}""")))
            assertEquals(listOf(1, 2, 3), h.api.moviesApi.movieMovieIdSimilarGet(603, page = 1).results?.map { it.id })
            assertEquals(3, h.api.moviesApi.movieMovieIdRecommendationsGet(603, page = 1).results?.size)
            assertEquals("movie", h.fake.requests.first().url.queryParameter("type"))
        }

    @Test
    fun a_movie_rating_comes_from_a_title_already_seen_and_a_show_without_one_is_a_404() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/explore/trending" to route(200 to """{"items":[${item(1)},${item(2, "tv")}],"page":1,"total_pages":1}"""),
                    ),
                )
            h.api.searchApi.discoverTrendingGet(page = 1)
            val rating = h.api.moviesApi.movieMovieIdRatingsGet(1)
            assertEquals(75, rating.audienceScore)
            try {
                h.api.tvApi.tvTvIdRatingsGet(2)
                fail("expected a 404")
            } catch (e: ClientException) {
                assertEquals(404, e.statusCode)
            }
            assertEquals(1, h.fake.requests.size)
        }

    @Test
    fun person_credits_are_empty() =
        runBlocking {
            val h = harness(emptyMap())
            val credits = h.api.personApi.personPersonIdCombinedCreditsGet(6384)
            assertEquals(0, credits.cast?.size)
            assertEquals(0, credits.crew?.size)
            assertTrue(h.fake.requests.isEmpty())
        }

    @Test
    fun a_request_for_a_listed_show_asks_for_the_whole_show() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/explore/trending" to route(200 to """{"items":[${item(2, "tv")}],"page":1,"total_pages":1}"""),
                        "POST api/requests" to route(200 to """{"id":77}"""),
                    ),
                )
            h.api.searchApi.discoverTrendingGet(page = 1)
            val created =
                h.api.requestApi.requestPost(
                    RequestPostRequest(mediaType = RequestPostRequest.MediaType.TV, mediaId = 2, seasons = null),
                )
            assertEquals(77, created.id)
            val body = h.fake.bodies.last()
            assertEquals("""{"type":"show","tmdb_id":2,"title":"Title 2","poster_url":"https://image.tmdb.org/t/p/w342/p2.jpg","year":2001}""", body)
        }

    @Test
    fun a_request_for_an_unlisted_title_reads_the_title_from_the_modal() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/movie/603" to route(200 to matrixModal),
                        "POST api/requests" to route(200 to """{"id":5}"""),
                    ),
                )
            h.api.requestApi.requestPost(RequestPostRequest(mediaType = RequestPostRequest.MediaType.MOVIE, mediaId = 603))
            assertEquals("""{"type":"movie","tmdb_id":603,"title":"The Matrix","poster_url":null,"year":1999}""", h.fake.bodies.last())
        }

    @Test
    fun an_already_requested_title_is_a_conflict() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/medias/modal/movie/603" to route(200 to matrixModal),
                        "POST api/requests" to route(409 to """{"error":"Already requested"}"""),
                    ),
                )
            try {
                h.api.requestApi.requestPost(RequestPostRequest(mediaType = RequestPostRequest.MediaType.MOVIE, mediaId = 603))
                fail("expected a conflict")
            } catch (e: ClientException) {
                assertEquals(409, e.statusCode)
            }
        }

    @Test
    fun the_requests_list_keeps_movies_and_shows_only() =
        runBlocking {
            val h =
                harness(
                    mapOf(
                        "api/requests" to
                            route(
                                200 to
                                    """{"requests":[
                                    {"id":1,"tmdb_id":603,"type":"movie","title":"The Matrix","poster_url":null,"year":1999,"status":"pending","requested_by":{"id":"u1","name":"Alex"},"created_at":"2026-01-02T03:04:05.000Z","decided_at":null},
                                    {"id":2,"tmdb_id":1399,"type":"show","title":"Thrones","poster_url":null,"year":2011,"status":"approved","requested_by":{"id":"u1","name":"Alex"},"created_at":"2026-02-02T03:04:05.000Z","decided_at":"2026-02-03T03:04:05.000Z"},
                                    {"id":3,"tmdb_id":null,"type":"book","title":"A book","poster_url":null,"year":null,"status":"pending","requested_by":{"id":"u1","name":"Alex"},"created_at":"2026-03-02T03:04:05.000Z","decided_at":null}]}""",
                            ),
                    ),
                )
            val results = h.api.requestApi.requestGet().results.orEmpty()
            assertEquals(listOf(1, 2), results.map { it.id })
            assertEquals(listOf("movie", "tv"), results.map { it.type })
            assertEquals(listOf(603, 1399), results.map { it.media?.tmdbId })
            assertEquals(listOf(1, 2), results.map { it.status })
            assertEquals("2026-02-03T03:04:05.000Z", results[1].updatedAt)
        }

    @Test
    fun changing_or_cancelling_a_request_is_refused() =
        runBlocking {
            val h = harness(emptyMap())
            try {
                h.api.requestApi.requestRequestIdDelete("5")
                fail("expected a refusal")
            } catch (e: ClientException) {
                assertEquals(403, e.statusCode)
            }
            assertTrue(h.fake.requests.isEmpty())
        }

    @Test
    fun an_ended_rawkoon_session_answers_401_and_signs_out() =
        runBlocking {
            val h = harness(mapOf("api/medias/discover" to route(401 to "{}")))
            try {
                h.api.searchApi.discoverMoviesGet(page = 1)
                fail("expected a 401")
            } catch (e: ClientException) {
                assertEquals(401, e.statusCode)
            }
            coVerify { session.expired() }
        }

    @Test
    fun an_unreachable_rawkoon_is_a_server_error_for_the_screen_not_a_crash() =
        runBlocking {
            val settings = mockk<RawkoonSettingsRepository>()
            coEvery { settings.current() } returns RawkoonConfig("https://rawkoon.example.test", "TOKEN", "Alex")
            val down = FakeRawkoon(emptyMap(), failWith = java.io.IOException("down"))
            val client = RawkoonClient(RawkoonHttp(OkHttpClient.Builder().addInterceptor(down).build()), settings, session)
            val reply = RawkoonSeerrBackend(client, settings, lookup).handle("GET", "/discover/movies", emptyMap(), null)
            assertEquals(502, reply.code)
        }

    @Test
    fun unknown_routes_are_a_404_and_other_hosts_pass_through() =
        runBlocking {
            val backend = mockk<RawkoonSeerrBackend>()
            val reachable = FakeRawkoon(mapOf("anything" to route(200 to """{"ok":true}""")))
            val client = OkHttpClient.Builder().addInterceptor(RawkoonSeerrInterceptor(backend)).addInterceptor(reachable).build()
            val passed = client.newCall(okhttp3.Request.Builder().url("https://other.example.test/anything").build()).execute()
            assertEquals(200, passed.code)
            assertNotNull(reachable.requests.singleOrNull())
            assertNull(reachable.requests.single().header("Authorization"))
        }
}
