package cloud.rawkoon.tv.live

import android.util.Log
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage

/**
 * The one Jellyfin WebSocket the app keeps, for the profile on screen. The SDK opens the socket
 * when the first flow is collected, closes it when the last stops, and reconnects by itself while
 * collected, so this only collects and cancels: no reconnect loop of its own. [deliver] gets the
 * kinds of a burst of events, once, on the main thread.
 */
class LiveUpdates(private val scope: CoroutineScope, deliver: (Set<LiveKind>) -> Unit) {
    private val feed = LiveFeed(scope, deliver)
    private var listening: Job? = null
    private var key: List<String>? = null

    /** Listens for [session]'s user; a no-op while it already does, a switch to the new one otherwise. */
    fun connect(session: JellyfinSession) {
        // The token too: signing in again as the same profile must not keep the old socket
        val next = listOf(session.profile.id, session.profile.jellyfinUrl, session.profile.jellyfinToken)
        if (key == next && listening?.isActive == true) return
        disconnect()
        key = next
        val sockets = session.api.webSocket
        listening = scope.launch {
            try {
                merge(
                    sockets.subscribe<UserDataChangedMessage>().filter { it.data.concerns(session.userId) }.map { LiveKind.USER_DATA },
                    sockets.subscribe<LibraryChangedMessage>().filter { it.data.concernsLibrary() }.map { LiveKind.LIBRARY },
                ).collect(feed::post)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Pages still reload when shown; live updates are a bonus
                Log.w(TAG, "live updates stopped", e)
            }
        }
    }

    fun disconnect() {
        listening?.cancel()
        listening = null
        key = null
        feed.clear()
    }

    private companion object {
        const val TAG = "LiveUpdates"
    }
}
