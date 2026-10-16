package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RawkoonClientTest {
    private val testDispatcher = StandardTestDispatcher()
    private var config = RawkoonConfig("https://rawkoon.example.test/base/", "TOKEN", "Alex")
    private val session = mockk<RawkoonSession>(relaxed = true)

    @Before
    fun setUp() = WholphinDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = WholphinDispatchers.reset()

    private fun client(fake: FakeRawkoon): RawkoonClient {
        val settings = mockk<RawkoonSettingsRepository>()
        coEvery { settings.current() } answers { config }
        return RawkoonClient(RawkoonHttp(OkHttpClient.Builder().addInterceptor(fake).build()), settings, session)
    }

    private fun route(vararg responses: Pair<Int, String>) = responses.toList()

    @Test
    fun status_sends_the_stored_bearer_token() =
        runTest(testDispatcher) {
            val fake =
                FakeRawkoon(
                    mapOf(
                        "api/requests/status" to
                            route(200 to """{"library":null,"request":{"id":5,"status":"pending","deny_reason":null}}"""),
                    ),
                )
            val status = client(fake).status(603, RawkoonMediaType.SHOW)
            assertEquals("pending", status.request?.status)
            val call = fake.requests.single()
            assertEquals("https://rawkoon.example.test/base/api/requests/status?tmdb_id=603&type=show", call.url.toString())
            assertEquals("Bearer TOKEN", call.header("Authorization"))
        }

    @Test
    fun a_401_ends_the_session_and_is_not_retried() =
        runTest(testDispatcher) {
            val fake = FakeRawkoon(mapOf("api/requests/status" to route(401 to "{}")))
            try {
                client(fake).status(1, RawkoonMediaType.MOVIE)
                fail("expected Unauthorized")
            } catch (_: RawkoonException.Unauthorized) {
            }
            assertEquals(1, fake.requests.size)
            coVerify(exactly = 1) { session.expired() }
        }

    @Test
    fun without_a_token_nothing_is_sent() =
        runTest(testDispatcher) {
            config = config.copy(token = "")
            val fake = FakeRawkoon(emptyMap())
            try {
                client(fake).status(1, RawkoonMediaType.MOVIE)
                fail("expected Unauthorized")
            } catch (_: RawkoonException.Unauthorized) {
            }
            config = RawkoonConfig(url = " ", token = "t")
            try {
                client(fake).status(1, RawkoonMediaType.MOVIE)
                fail("expected NotConfigured")
            } catch (_: RawkoonException.NotConfigured) {
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun errors_are_typed() =
        runTest(testDispatcher) {
            try {
                client(FakeRawkoon(mapOf("api/requests/status" to route(500 to "{}")))).status(1, RawkoonMediaType.MOVIE)
                fail("expected Http")
            } catch (e: RawkoonException.Http) {
                assertEquals(500, e.code)
            }
            try {
                client(FakeRawkoon(emptyMap(), failWith = IOException("down"))).status(1, RawkoonMediaType.MOVIE)
                fail("expected Unreachable")
            } catch (_: RawkoonException.Unreachable) {
            }
            try {
                client(FakeRawkoon(mapOf("api/requests/status" to route(200 to "nope")))).status(1, RawkoonMediaType.MOVIE)
                fail("expected InvalidResponse")
            } catch (_: RawkoonException.InvalidResponse) {
            }
        }

    @Test
    fun invalid_url_sends_nothing() =
        runTest(testDispatcher) {
            config = RawkoonConfig("rawkoon.example.test", "t")
            val fake = FakeRawkoon(emptyMap())
            try {
                client(fake).bootstrap()
                fail("expected InvalidSettings")
            } catch (_: RawkoonException.InvalidSettings) {
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun invisible_characters_from_the_tv_keyboard_are_ignored() =
        runTest(testDispatcher) {
            config = RawkoonConfig(" https://rawkoon.example.test\u200B\n", "t")
            val fake = FakeRawkoon(mapOf("api/tv/bootstrap" to route(200 to "{}")))
            client(fake).bootstrap()
            assertEquals(
                "https://rawkoon.example.test/api/tv/bootstrap",
                fake.requests
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun bootstrap_parses_the_account_setup() =
        runTest(testDispatcher) {
            val body =
                """{"user":{"id":"u1","name":"Alex","is_admin":false},
                |"jellyfin":{"url":"https://jellyfin.example.test","linked":true},"tmdb":{"key":"k"},"extra":1}
                """.trimMargin()
            val b = client(FakeRawkoon(mapOf("api/tv/bootstrap" to route(200 to body)))).bootstrap()
            assertEquals("Alex", b.user.name)
            assertEquals(BootstrapJellyfin("https://jellyfin.example.test", true), b.jellyfin)
            assertEquals("k", b.tmdb?.key)
            val bare =
                client(
                    FakeRawkoon(
                        mapOf("api/tv/bootstrap" to route(200 to """{"user":{"id":"u","name":"A"},"jellyfin":null,"tmdb":null}""")),
                    ),
                ).bootstrap()
            assertEquals(null, bare.jellyfin)
            assertEquals(null, bare.tmdb)
        }

    @Test
    fun quick_connect_approval_posts_the_code_and_types_failures() =
        runTest(testDispatcher) {
            val ok = FakeRawkoon(mapOf("POST api/tv/jellyfin/quick-connect" to route(200 to """{"ok":true}""")))
            client(ok).approveQuickConnect("123456")
            assertEquals("""{"code":"123456"}""", ok.bodies.single())
            assertEquals("Bearer TOKEN", ok.requests.single().header("Authorization"))

            suspend fun expect(
                code: Int,
                check: (RawkoonException) -> Boolean,
            ) {
                try {
                    client(FakeRawkoon(mapOf("api/tv/jellyfin/quick-connect" to route(code to "{}")))).approveQuickConnect("1")
                    fail("expected RawkoonException for $code")
                } catch (e: RawkoonException) {
                    assertTrue("$code $e", check(e))
                }
            }
            expect(403) { it is RawkoonException.NotLinked }
            expect(404) { it is RawkoonException.QuickConnectExpired }
            expect(503) { it is RawkoonException.NoJellyfinIntegration }
            expect(502) { it is RawkoonException.JellyfinUnreachable }
            expect(500) { it is RawkoonException.Http }
        }

    @Test
    fun request_posts_the_title_and_tolerates_already_requested() =
        runTest(testDispatcher) {
            val fake = FakeRawkoon(mapOf("POST api/requests" to route(200 to """{"id":5}""", 409 to "{}", 400 to "{}", 500 to "{}")))
            val c = client(fake)
            c.request(603, RawkoonMediaType.MOVIE, "Title", "https://img.example.test/p.jpg", 1999)
            assertEquals(
                """{"type":"movie","tmdb_id":603,"title":"Title","poster_url":"https://img.example.test/p.jpg","year":1999}""",
                fake.bodies.last(),
            )
            c.request(603, RawkoonMediaType.SHOW, "Title", null, null)
            assertTrue(fake.bodies.last()!!.contains(""""type":"show""""))
            c.request(603, RawkoonMediaType.MOVIE, "Title", null, null)
            try {
                c.request(603, RawkoonMediaType.MOVIE, "Title", null, null)
                fail("expected Http")
            } catch (e: RawkoonException.Http) {
                assertEquals(500, e.code)
            }
        }
}
