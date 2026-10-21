package cloud.rawkoon.tv.live

import android.os.SystemClock
import android.util.Log
import cloud.rawkoon.tv.rawkoon.RawkoonEndpoint
import cloud.rawkoon.tv.rawkoon.RequestSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What [RawkoonLive] asks of the app for the profile it follows. */
class RawkoonLiveSession(
    val endpoint: RawkoonEndpoint,
    /** The account's requests, for telling its own titles apart. */
    val requests: suspend () -> List<RequestSummary>,
    /** The account's Rawkoon id, or null when there is no telling. */
    val userId: suspend () -> String?,
)

/**
 * Rawkoon's two event streams for the profile on screen: `/api/library/events` (a title landed)
 * and `/api/notifications/stream` (a request was decided or became available). Each is one
 * [SseConnection] in a loop that reconnects with a capped, jittered backoff, and stops when the
 * server refuses the token: the session ended, which the pages already handle by asking to link the
 * account again. It stays stopped until the streams are next opened (another page set, the app
 * coming back to the foreground, or a new token). Events go through a [LiveFeed], so [deliver] gets a burst once, on the main
 * thread; [toast] gets the words for what the user should be told.
 */
class RawkoonLive(
    private val scope: CoroutineScope,
    deliver: (Set<LiveKind>) -> Unit,
    private val toast: (RawkoonToast) -> Unit,
) {
    private val feed = LiveFeed(scope, deliver)
    private val lookups = ToastGate(LOOKUP_GAP_MS)
    private val toasts = ToastGate()
    private var key: RawkoonEndpoint? = null
    private var jobs = emptyList<Job>()

    /** Follows [session]'s account; a no-op while it already does, a switch to the new one otherwise. */
    fun connect(session: RawkoonLiveSession) {
        if (key == session.endpoint) return
        disconnect()
        key = session.endpoint
        jobs = listOf(
            stream(session, "api/library/events", keep = RawkoonSignals::mayMatterOnLibrary) { event -> RawkoonSignals.library(event) },
            stream(session, "api/notifications/stream", keep = { true }) { event -> RawkoonSignals.notification(event, userIdIfKnown(session)) },
        )
    }

    fun disconnect() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        key = null
        feed.clear()
        lookups.clear()
        toasts.clear()
    }

    /**
     * [keep] runs on the reading thread and lets through only what may matter: the library stream
     * also carries frequent download and seeding updates the TV has no use for.
     */
    private fun stream(
        session: RawkoonLiveSession,
        path: String,
        keep: (String) -> Boolean,
        parse: suspend (SseEvent) -> RawkoonSignal?,
    ): Job = scope.launch {
        val url = "${session.endpoint.baseUrl}/$path"
        val backoff = Backoff()
        var connectedBefore = false
        while (isActive) {
            val startedAt = SystemClock.elapsedRealtime()
            val resumed = connectedBefore
            var opened = false
            try {
                SseConnection.read(
                    url, session.endpoint.token,
                    onOpen = { opened = true },
                    // From the IO thread: the main thread takes the events in order, and a closed stream drops them
                    onEvent = { event ->
                        if (keep(event.data)) {
                            launch {
                                try {
                                    parse(event)?.let { handle(session, it, resumed) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // One bad event must neither crash the app nor end the stream
                                    Log.w(TAG, "$path: event dropped", e)
                                }
                            }
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SseRefused) {
                Log.w(TAG, "$path refused (${e.code}): stopping")
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "$path: ${e.message}")
            }
            if (opened) connectedBefore = true
            delay(backoff.afterConnection(if (opened) SystemClock.elapsedRealtime() - startedAt else 0L))
        }
    }

    private suspend fun handle(session: RawkoonLiveSession, signal: RawkoonSignal, resumed: Boolean) {
        when (signal) {
            // A handshake after a drop: whatever happened meanwhile is unknown, so the pages look again
            RawkoonSignal.Handshake -> if (resumed) {
                feed.post(LiveKind.RAWKOON_LANDED)
                feed.post(LiveKind.REQUEST_CHANGED)
            }
            is RawkoonSignal.Landed -> {
                feed.post(LiveKind.RAWKOON_LANDED)
                announceIfOwn(session, signal)
            }
            is RawkoonSignal.RequestChanged -> {
                feed.post(LiveKind.REQUEST_CHANGED)
                RawkoonSignals.toastFor(signal)?.let { toast(RawkoonToast.Server(it)) }
            }
        }
    }

    /** "<title> is ready", only for a title this account asked for. */
    private suspend fun announceIfOwn(session: RawkoonLiveSession, landed: RawkoonSignal.Landed) {
        if (!lookups.allow(landed.mediaId, SystemClock.elapsedRealtime())) return
        val own = try {
            val userId = session.userId() ?: return
            RawkoonSignals.ownRequest(landed, session.requests(), userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No toast without knowing; the pages refresh all the same
            null
        } ?: return
        if (toasts.allow(landed.mediaId, SystemClock.elapsedRealtime())) toast(RawkoonToast.Ready(own.title.ifBlank { landed.title }))
    }

    private suspend fun userIdIfKnown(session: RawkoonLiveSession): String? = try {
        session.userId()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "RawkoonLive"

        /** One look at the requests per title per minute: a show's episodes land in a run. */
        const val LOOKUP_GAP_MS = 60_000L
    }
}

/** A message for the user: the app words [Ready] itself, a [Server] one arrives worded. */
sealed interface RawkoonToast {
    data class Ready(val title: String) : RawkoonToast
    data class Server(val text: String) : RawkoonToast
}
