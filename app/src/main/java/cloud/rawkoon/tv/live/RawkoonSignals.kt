package cloud.rawkoon.tv.live

import cloud.rawkoon.tv.rawkoon.RequestSummary
import org.json.JSONObject

/** What Rawkoon's two streams say that the app acts on. */
sealed interface RawkoonSignal {
    /** The stream is up; after a drop it means events may have been missed. */
    data object Handshake : RawkoonSignal

    /** A movie or episodes finished importing: [mediaId] is the library's id, not TMDB's. [mediaType] is "movie" or "tv". */
    data class Landed(val mediaId: Int, val mediaType: String, val title: String) : RawkoonSignal

    /** The server notified the user of a request decision (approved, declined) or of one becoming available. */
    data class RequestChanged(val decided: Boolean, val title: String, val body: String) : RawkoonSignal

    val kind: LiveKind? get() = when (this) {
        is Landed -> LiveKind.RAWKOON_LANDED
        is RequestChanged -> LiveKind.REQUEST_CHANGED
        Handshake -> null
    }
}

/** Reads the events of `/api/library/events` and `/api/notifications/stream` (field names from sse-contract.v1.json). */
object RawkoonSignals {
    private const val DECIDED = "request_decided"
    private const val AVAILABLE = "request_available"

    /** Unnamed events are the only ones the server sends; a named one is something else. */
    private fun json(event: SseEvent): JSONObject? {
        if (event.event != null && event.event != "message") return null
        return runCatching { JSONObject(event.data) }.getOrNull()
    }

    /** `landed` by its `kind`; the handshake has `connected` and `ts`; every other kind (progress, media updates) is not ours. */
    fun library(event: SseEvent): RawkoonSignal? {
        val json = json(event) ?: return null
        if (json.optBoolean("connected") && json.has("ts")) return RawkoonSignal.Handshake
        if (json.optString("kind") != "landed") return null
        val mediaId = json.optInt("mediaId", -1).takeIf { it >= 0 && json.has("mediaId") } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() && !json.isNull("title") } ?: return null
        return RawkoonSignal.Landed(mediaId, json.optString("mediaType"), title)
    }

    /**
     * A notification (it has `id` and `userId`) of a request decision or availability. The server
     * already sends a user their own only; [userId], when known, is checked again.
     */
    fun notification(event: SseEvent, userId: String?): RawkoonSignal? {
        val json = json(event) ?: return null
        if (json.optBoolean("connected") && !json.has("id")) return RawkoonSignal.Handshake
        if (!json.has("id") || !json.has("userId")) return null
        if (userId != null && json.optString("userId") != userId) return null
        val decided = when (json.optString("type")) {
            DECIDED -> true
            AVAILABLE -> false
            else -> return null
        }
        return RawkoonSignal.RequestChanged(decided, json.optString("title"), json.optString("body"))
    }

    /** The account's own request for a landed title, or null when the title is nobody's request (or someone else's). */
    fun ownRequest(landed: RawkoonSignal.Landed, requests: List<RequestSummary>, userId: String): RequestSummary? {
        val type = if (landed.mediaType == "movie") "movie" else "show"
        return requests.firstOrNull { it.requestedBy == userId && it.type == type && it.libraryMediaId == landed.mediaId }
    }

    /**
     * What to tell the user about a notification: the server's own words, in the language it
     * knows the user by. An availability is left to the "ready" toast of the landing that caused it.
     */
    fun toastFor(signal: RawkoonSignal.RequestChanged): String? =
        signal.takeIf { it.decided }?.let { it.body.ifBlank { it.title } }?.ifBlank { null }
}

/** Lets a title announce itself once per [windowMs]: a show's episodes land one by one. */
class ToastGate(private val windowMs: Long = WINDOW_MS) {
    private val last = HashMap<Int, Long>()

    fun clear() = last.clear()

    fun allow(mediaId: Int, now: Long): Boolean {
        last.values.removeAll { now - it >= windowMs }
        if (last.containsKey(mediaId)) return false
        last[mediaId] = now
        return true
    }

    companion object {
        const val WINDOW_MS = 30 * 60_000L
    }
}
