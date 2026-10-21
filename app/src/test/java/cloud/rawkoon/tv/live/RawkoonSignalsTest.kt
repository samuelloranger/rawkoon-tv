package cloud.rawkoon.tv.live

import cloud.rawkoon.tv.rawkoon.RequestSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonSignalsTest {
    private fun event(data: String, name: String? = null) = SseEvent(name, data, null)

    private val landed = """{"kind":"landed","mediaId":12,"mediaType":"movie","title":"Example Movie","posterUrl":null,"season":null,"episode":null,"episodeCount":null,"ts":1}"""

    @Test
    fun `a landed event is read by its kind`() {
        assertEquals(RawkoonSignal.Landed(12, "movie", "Example Movie"), RawkoonSignals.library(event(landed)))
    }

    @Test
    fun `the library handshake is a handshake`() {
        assertEquals(RawkoonSignal.Handshake, RawkoonSignals.library(event("""{"connected":true,"ts":5}""")))
    }

    @Test
    fun `other library kinds and junk are not ours`() {
        for (data in listOf(
            """{"kind":"media","mediaId":1,"ts":1}""",
            """{"kind":"book","bookId":1,"ts":1}""",
            """{"kind":"download-progress","mediaId":1,"downloads":[],"ts":1}""",
            """{"kind":"seed-state","torrents":[],"ts":1}""",
            """{"kind":"search_result","media_id":1,"outcome":"x","ts":1}""",
            """{"mediaId":12,"title":"No kind"}""",
            "not json",
            "",
            "[1,2]",
        )) {
            assertNull(data, RawkoonSignals.library(event(data)))
        }
    }

    @Test
    fun `a landed event missing its id or title is dropped`() {
        assertNull(RawkoonSignals.library(event("""{"kind":"landed","title":"T","mediaType":"movie"}""")))
        assertNull(RawkoonSignals.library(event("""{"kind":"landed","mediaId":1,"mediaType":"movie"}""")))
        assertNull(RawkoonSignals.library(event("""{"kind":"landed","mediaId":1,"mediaType":"movie","title":null}""")))
    }

    @Test
    fun `a named event is not the server's message`() {
        assertNull(RawkoonSignals.library(event(landed, name = "other")))
        assertEquals(RawkoonSignal.Landed(12, "movie", "Example Movie"), RawkoonSignals.library(event(landed, name = "message")))
    }

    private fun note(type: String, user: String = "u1") =
        """{"id":4,"userId":"$user","title":"Request approved","body":"Example Movie was approved","type":"$type","url":null,"ts":1}"""

    @Test
    fun `a request decision and an availability are read from a notification`() {
        assertEquals(RawkoonSignal.RequestChanged(true, "Request approved", "Example Movie was approved"), RawkoonSignals.notification(event(note("request_decided")), "u1"))
        assertEquals(false, (RawkoonSignals.notification(event(note("request_available")), "u1") as RawkoonSignal.RequestChanged).decided)
    }

    @Test
    fun `other notifications are not ours`() {
        for (type in listOf("request_pending", "new_release", "update", "")) {
            assertNull(type, RawkoonSignals.notification(event(note(type)), "u1"))
        }
    }

    @Test
    fun `a notification for another user is dropped, an unknown user is taken as it comes`() {
        assertNull(RawkoonSignals.notification(event(note("request_decided", user = "someone")), "u1"))
        assertTrue(RawkoonSignals.notification(event(note("request_decided", user = "someone")), null) is RawkoonSignal.RequestChanged)
    }

    @Test
    fun `a notification needs the contract's id and userId`() {
        assertNull(RawkoonSignals.notification(event("""{"userId":"u1","type":"request_decided"}"""), "u1"))
        assertNull(RawkoonSignals.notification(event("""{"id":1,"type":"request_decided"}"""), "u1"))
    }

    @Test
    fun `the notification handshake is a handshake`() {
        assertEquals(RawkoonSignal.Handshake, RawkoonSignals.notification(event("""{"connected":true}"""), "u1"))
    }

    @Test
    fun `only decisions are announced, in the server's words`() {
        assertEquals("Example Movie was approved", RawkoonSignals.toastFor(RawkoonSignal.RequestChanged(true, "Request approved", "Example Movie was approved")))
        assertEquals("Request approved", RawkoonSignals.toastFor(RawkoonSignal.RequestChanged(true, "Request approved", " ")))
        assertNull(RawkoonSignals.toastFor(RawkoonSignal.RequestChanged(true, "", "")))
        assertNull(RawkoonSignals.toastFor(RawkoonSignal.RequestChanged(false, "Available", "Example Movie is available")))
    }

    @Test
    fun `signals map to the kinds pages react to`() {
        assertEquals(LiveKind.RAWKOON_LANDED, RawkoonSignal.Landed(1, "movie", "T").kind)
        assertEquals(LiveKind.REQUEST_CHANGED, RawkoonSignal.RequestChanged(true, "", "").kind)
        assertNull(RawkoonSignal.Handshake.kind)
    }

    private fun request(id: Int, by: String?, type: String, media: Int?) =
        RequestSummary(id, 100 + id, type == "movie", type, "T$id", null, 2024, "approved", by, null, null, libraryMediaId = media)

    @Test
    fun `a landed title is mine when one of my requests of that type points at its library entry`() {
        val movie = RawkoonSignal.Landed(12, "movie", "T")
        assertEquals(1, RawkoonSignals.ownRequest(movie, listOf(request(1, "u1", "movie", 12)), "u1")?.id)
        assertNull(RawkoonSignals.ownRequest(movie, listOf(request(1, "other", "movie", 12)), "u1"))
        assertNull(RawkoonSignals.ownRequest(movie, listOf(request(1, "u1", "movie", 13)), "u1"))
        assertNull(RawkoonSignals.ownRequest(movie, listOf(request(1, "u1", "movie", null)), "u1"))
        assertNull(RawkoonSignals.ownRequest(movie, listOf(request(1, "u1", "show", 12)), "u1"))
        assertNull(RawkoonSignals.ownRequest(movie, emptyList(), "u1"))
    }

    @Test
    fun `a show lands as tv and matches a show request`() {
        val show = RawkoonSignal.Landed(7, "tv", "S")
        assertEquals(2, RawkoonSignals.ownRequest(show, listOf(request(2, "u1", "show", 7)), "u1")?.id)
        assertNull(RawkoonSignals.ownRequest(show, listOf(request(2, "u1", "movie", 7)), "u1"))
    }

    @Test
    fun `a title announces itself once per window`() {
        val gate = ToastGate(windowMs = 1_000)
        assertTrue(gate.allow(1, now = 0))
        assertFalse(gate.allow(1, now = 999))
        assertTrue(gate.allow(2, now = 500))
        assertTrue(gate.allow(1, now = 1_000))
        gate.clear()
        assertTrue(gate.allow(2, now = 1_001))
    }
}
