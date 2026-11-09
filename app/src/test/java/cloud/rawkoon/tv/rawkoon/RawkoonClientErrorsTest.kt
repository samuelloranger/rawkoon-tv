package cloud.rawkoon.tv.rawkoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonClientErrorsTest {
    private fun failure(code: Int, body: String = "") = RawkoonClient.quickConnectFailure(code, body)

    @Test
    fun `an approved code is no failure`() = assertNull(failure(200, """{"ok":true}"""))

    @Test
    fun `a refused session is unauthorized`() = assertTrue(failure(401) is RawkoonException.Unauthorized)

    @Test
    fun `Jellyfin being unreachable has its own error`() =
        assertTrue(failure(502, """{"error":"Failed to reach Jellyfin"}""") is RawkoonException.JellyfinUnreachable)

    @Test
    fun `the route's own 404 is an expired code`() =
        assertTrue(failure(404, """{"error":"Jellyfin does not know this Quick Connect code"}""") is RawkoonException.QuickConnectExpired)

    @Test
    fun `a bare 404 means the server predates the route`() {
        assertTrue(failure(404, "404 Not Found") is RawkoonException.ServerTooOld)
        assertTrue(failure(404, "") is RawkoonException.ServerTooOld)
        assertTrue(failure(404, """{"error":"Not found"}""") is RawkoonException.ServerTooOld)
    }

    @Test
    fun `other statuses keep their meaning`() {
        assertTrue(failure(403) is RawkoonException.NotLinked)
        assertTrue(failure(503) is RawkoonException.NoJellyfin)
        assertEquals(500, (failure(500) as RawkoonException.Http).code)
    }

    @Test
    fun `an invalid grant asks for a fresh code like an expired one`() {
        assertEquals(DevicePoll.Expired, RawkoonClient.devicePollOf("""{"error":"invalid_grant","error_description":"Invalid device code"}"""))
        assertNull(RawkoonClient.devicePollOf("""{"error":"invalid_grant","error_description":"Invalid client ID"}"""))
        assertNull(RawkoonClient.devicePollOf("""{"error":"invalid_grant","error_description":"Client ID mismatch"}"""))
        assertEquals(DevicePoll.Expired, RawkoonClient.devicePollOf("""{"error":"expired_token"}"""))
        assertEquals(DevicePoll.Pending, RawkoonClient.devicePollOf("""{"error":"authorization_pending"}"""))
        assertNull(RawkoonClient.devicePollOf("""{"error":"invalid_request"}"""))
        assertNull(RawkoonClient.devicePollOf("not json"))
    }

    @Test
    fun `an http address redirected to https is told to use https`() {
        assertTrue(RawkoonClient.redirectsToHttps("http", 301, "https://example.test/"))
        assertTrue(RawkoonClient.redirectsToHttps("http", 308, "HTTPS://example.test/"))
        assertTrue(RawkoonClient.redirectsToHttps("http", 303, "https://example.test/"))
        assertFalse(RawkoonClient.redirectsToHttps("http", 301, "http://example.test/other"))
        assertFalse(RawkoonClient.redirectsToHttps("http", 200, "https://example.test/"))
        assertFalse(RawkoonClient.redirectsToHttps("https", 301, "https://example.test/"))
        assertFalse(RawkoonClient.redirectsToHttps("http", 302, null))
    }
}
