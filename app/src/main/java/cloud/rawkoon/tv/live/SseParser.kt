package cloud.rawkoon.tv.live

/** One dispatched event: [event] is the `event:` name (null when the server sent none), [data] its `data:` lines joined by newlines. */
data class SseEvent(val event: String?, val data: String, val id: String?)

/**
 * The line rules of Server-Sent Events: feed it one line at a time (terminators already removed,
 * CR, LF or CRLF), and a blank line hands back the event the lines before it built. Comment lines
 * (the server's `: ping`), unknown fields and `retry:` are ignored; a blank line with no `data:
 * ` before it dispatches nothing; an event still unfinished when the stream ends is never fed a
 * blank line, so it is dropped, as the spec says.
 */
class SseParser {
    private val data = StringBuilder()
    private var hasData = false
    private var event: String? = null

    /** The last `id:` seen; it stays for the events after it, as the spec has it. */
    var lastId: String? = null
        private set

    fun line(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(':')) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "event" -> event = value
            "id" -> if ('\u0000' !in value) lastId = value
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val out = if (hasData) SseEvent(event?.ifEmpty { null }, data.toString(), lastId) else null
        data.setLength(0)
        hasData = false
        event = null
        return out
    }
}
