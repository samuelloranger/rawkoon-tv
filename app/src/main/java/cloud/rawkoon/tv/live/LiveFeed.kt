package cloud.rawkoon.tv.live

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One source's events, gathered by a [LiveBatch] and handed to [deliver] once per burst, on the scope's (main) thread. */
class LiveFeed(private val scope: CoroutineScope, private val deliver: (Set<LiveKind>) -> Unit) {
    private val batch = LiveBatch()
    private var flushing: Job? = null

    fun post(kind: LiveKind) {
        val wait = batch.add(kind, SystemClock.elapsedRealtime()) ?: return
        flushing = scope.launch {
            delay(wait)
            try {
                deliver(batch.drain(SystemClock.elapsedRealtime()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A page that fails to refresh stays as it was; it must not take the app down
                Log.w(TAG, "live refresh failed", e)
            }
        }
    }

    /** Drops what gathered and what was about to be delivered: nothing may reach the next screen's session. */
    fun clear() {
        flushing?.cancel()
        flushing = null
        batch.clear()
    }

    private companion object {
        const val TAG = "LiveFeed"
    }
}
