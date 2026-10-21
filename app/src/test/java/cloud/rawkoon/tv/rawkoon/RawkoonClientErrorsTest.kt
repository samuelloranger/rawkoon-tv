package cloud.rawkoon.tv.rawkoon

import org.junit.Assert.assertEquals
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
        assertEquals(DevicePoll.Expired, RawkoonClient.devicePollOf("""{"error":"invalid_grant"}"""))
        assertEquals(DevicePoll.Expired, RawkoonClient.devicePollOf("""{"error":"expired_token"}"""))
        assertEquals(DevicePoll.Pending, RawkoonClient.devicePollOf("""{"error":"authorization_pending"}"""))
        assertNull(RawkoonClient.devicePollOf("""{"error":"invalid_request"}"""))
        assertNull(RawkoonClient.devicePollOf("not json"))
    }
}
