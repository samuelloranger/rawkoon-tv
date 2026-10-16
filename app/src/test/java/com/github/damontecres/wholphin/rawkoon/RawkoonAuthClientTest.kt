package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RawkoonAuthClientTest {
    private val testDispatcher = StandardTestDispatcher()
    private val base = "https://rawkoon.example.test"

    @Before
    fun setUp() = WholphinDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = WholphinDispatchers.reset()

    private fun client(fake: FakeRawkoon) = RawkoonAuthClient(RawkoonHttp(OkHttpClient.Builder().addInterceptor(fake).build()))

    private fun route(vararg responses: Pair<Int, String>) = responses.toList()

    @Test
    fun device_code_request_and_formatting() =
        runTest(testDispatcher) {
            val body =
                """{"device_code":"dc","user_code":"ABCD1234","verification_uri":"https://rawkoon.example.test/device",
                |"verification_uri_complete":"https://rawkoon.example.test/device?user_code=ABCD1234","expires_in":600,"interval":5}
                """.trimMargin()
            val fake = FakeRawkoon(mapOf("api/auth/device/code" to route(200 to body)))
            val code = client(fake).requestDeviceCode(" $base\u200B\n")
            assertEquals("""{"client_id":"rawkoon-tv"}""", fake.bodies.single())
            assertEquals(
                "$base/api/auth/device/code",
                fake.requests
                    .single()
                    .url
                    .toString(),
            )
            assertEquals("ABCD-1234", code.displayCode)
            assertEquals(5, code.interval)
            assertEquals("https://rawkoon.example.test/device?user_code=ABCD1234", code.verificationUriComplete)
        }

    @Test
    fun poll_answers() =
        runTest(testDispatcher) {
            suspend fun poll(
                code: Int,
                body: String,
            ) = client(FakeRawkoon(mapOf("api/auth/device/token" to route(code to body)))).pollDeviceToken(base, "dc")
            assertEquals(DevicePoll.Granted("tok"), poll(200, """{"access_token":"tok","token_type":"Bearer","expires_in":1}"""))
            assertEquals(DevicePoll.Pending, poll(400, """{"error":"authorization_pending"}"""))
            assertEquals(DevicePoll.SlowDown, poll(400, """{"error":"slow_down"}"""))
            assertEquals(DevicePoll.Expired, poll(400, """{"error":"expired_token"}"""))
            assertEquals(DevicePoll.Denied, poll(400, """{"error":"access_denied"}"""))
            try {
                poll(400, """{"error":"invalid_grant"}""")
                fail("expected Http")
            } catch (_: RawkoonException.Http) {
            }
            try {
                poll(500, "")
                fail("expected Http")
            } catch (e: RawkoonException.Http) {
                assertEquals(500, e.code)
            }
        }

    @Test
    fun poll_body_names_the_grant() =
        runTest(testDispatcher) {
            val fake = FakeRawkoon(mapOf("api/auth/device/token" to route(400 to """{"error":"authorization_pending"}""")))
            client(fake).pollDeviceToken(base, "dc")
            assertEquals(
                """{"grant_type":"urn:ietf:params:oauth:grant-type:device_code","device_code":"dc","client_id":"rawkoon-tv"}""",
                fake.bodies.single(),
            )
        }

    @Test
    fun email_sign_in_reads_the_token_header() =
        runTest(testDispatcher) {
            val fake = FakeRawkoon(mapOf("api/auth/sign-in/email" to route(200 to "{}")), headers = mapOf("set-auth-token" to "tok"))
            assertEquals("tok", client(fake).signInWithEmail(base, " a@example.test ", "pw"))
            assertEquals("""{"email":"a@example.test","password":"pw"}""", fake.bodies.single())
        }

    @Test
    fun email_sign_in_failures() =
        runTest(testDispatcher) {
            suspend fun expect(
                fake: FakeRawkoon,
                check: (RawkoonException) -> Boolean,
            ) {
                try {
                    client(fake).signInWithEmail(base, "a@example.test", "pw")
                    fail("expected RawkoonException")
                } catch (e: RawkoonException) {
                    assertEquals(e.toString(), true, check(e))
                }
            }
            expect(FakeRawkoon(mapOf("api/auth/sign-in/email" to route(401 to "{}")))) { it is RawkoonException.InvalidCredentials }
            expect(FakeRawkoon(mapOf("api/auth/sign-in/email" to route(429 to "{}")))) { it is RawkoonException.RateLimited }
            expect(FakeRawkoon(mapOf("api/auth/sign-in/email" to route(200 to "{}")))) { it is RawkoonException.InvalidResponse }
        }
}
