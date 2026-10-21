package cloud.rawkoon.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class SseParserTest {
    /** The reader's own line splitting (CR, LF, CRLF), then the parser, as [SseConnection] does it. */
    private fun parse(stream: String): List<SseEvent> {
        val parser = SseParser()
        return BufferedReader(StringReader(stream)).lineSequence().mapNotNull(parser::line).toList()
    }

    @Test
    fun `a data line and a blank line make one event`() {
        assertEquals(listOf(SseEvent(null, "{\"a\":1}", null)), parse("data: {\"a\":1}\n\n"))
    }

    @Test
    fun `data over several lines is joined with newlines`() {
        assertEquals(listOf(SseEvent(null, "one\ntwo\n\nfour", null)), parse("data: one\ndata: two\ndata:\ndata: four\n\n"))
    }

    @Test
    fun `only one leading space of the value is dropped`() {
        assertEquals("  two", parse("data:   two\n\n").single().data)
        assertEquals("x", parse("data:x\n\n").single().data)
    }

    @Test
    fun `a field with no colon has an empty value`() {
        assertEquals("", parse("data\n\n").single().data)
    }

    @Test
    fun `comments and heartbeats are ignored, even between fields`() {
        assertEquals(listOf(SseEvent(null, "x", null)), parse(": ping\n\ndata: x\n: ping\n\n: ping\n\n"))
    }

    @Test
    fun `a heartbeat alone dispatches nothing`() {
        assertEquals(emptyList<SseEvent>(), parse(": ping\n\n: ping\n\n"))
    }

    @Test
    fun `the retry hint and the handshake line before the data are not events`() {
        assertEquals(listOf(SseEvent(null, "{\"connected\":true}", null)), parse("retry: 3000\n\ndata: {\"connected\":true}\n\n"))
    }

    @Test
    fun `CRLF, CR and LF all end lines`() {
        assertEquals(listOf("a", "b", "c"), parse("data: a\r\n\r\ndata: b\r\rdata: c\n\n").map { it.data })
    }

    @Test
    fun `event and id fields ride along, and the id outlives its event`() {
        val events = parse("event: landed\nid: 7\ndata: x\n\ndata: y\n\n")
        assertEquals(SseEvent("landed", "x", "7"), events[0])
        // The event name is per event; the id stays
        assertEquals(SseEvent(null, "y", "7"), events[1])
    }

    @Test
    fun `an id with a NUL is ignored`() {
        assertNull(parse("id: a\u0000b\ndata: x\n\n").single().id)
    }

    @Test
    fun `an unknown field is ignored and the event still dispatches`() {
        assertEquals(listOf(SseEvent(null, "x", null)), parse("foo: bar\ndata: x\n\n"))
    }

    @Test
    fun `an event name with no data dispatches nothing and does not leak into the next`() {
        assertEquals(listOf(SseEvent(null, "x", null)), parse("event: ghost\n\ndata: x\n\n"))
    }

    @Test
    fun `a partial final event is dropped`() {
        assertEquals(listOf(SseEvent(null, "whole", null)), parse("data: whole\n\ndata: half"))
        assertEquals(listOf(SseEvent(null, "whole", null)), parse("data: whole\n\ndata: half\n"))
    }

    @Test
    fun `an empty data line still dispatches an empty event`() {
        assertEquals(listOf(SseEvent(null, "", null)), parse("data:\n\n"))
    }

    @Test
    fun `non-ASCII text survives`() {
        assertEquals("Amélie — prêt", parse("data: Amélie — prêt\n\n").single().data)
    }

    @Test
    fun `lines are taken one by one the way the reader hands them over`() {
        val parser = SseParser()
        assertNull(parser.line("data: a"))
        assertNull(parser.line("data: b"))
        assertEquals("a\nb", parser.line("")?.data)
        assertNull(parser.line(""))
    }
}
