package cloud.rawkoon.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * One page of the app. Pages are plain views on a stack in [MainActivity]: the ones below stay
 * built but hidden, so going back restores them as they were, focus included.
 */
abstract class Screen(val activity: MainActivity) {
    val context: Context get() = activity
    val app: App get() = activity.app

    /** Cancelled when the screen leaves the stack. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Delayed work for the screen; dropped in [destroy]. */
    protected val mainHandler = Handler(Looper.getMainLooper())

    /** False once the screen is destroyed: image loads use it to skip work for a page that is gone. */
    @Volatile
    protected var alive = true
        private set

    abstract val view: View

    private var lastFocus: View? = null

    /** Called when the screen comes to the top, the first time and after going back to it. */
    open fun onShown(first: Boolean) {
        val previous = lastFocus
        if (!first && previous != null && previous.isAttachedToWindow && previous.requestFocus()) return
        focusDefault()
    }

    /** Puts focus where a fresh visit starts. */
    open fun focusDefault() {}

    fun onHidden() {
        // A screen already hidden (replaced while below another) has no focus left to remember
        view.findFocus()?.let { lastFocus = focusToRemember(it) }
    }

    /** What to give focus back to when the screen returns, given what had it as the screen left. */
    protected open fun focusToRemember(focused: View): View = focused

    /** True when the screen handled Back itself; otherwise the stack pops it. */
    open fun onBack(): Boolean = false

    /** Keys before the focused view sees them; true when consumed. */
    open fun onKey(event: KeyEvent): Boolean = false

    /** The app went to the background (Home pressed, another app opened), or came back. */
    open fun onPause() {}

    open fun onResume() {}

    open fun destroy() {
        alive = false
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    /**
     * Launches a page load in [scope]. Its children run in a scope of their own, so one that fails
     * lands in [onError] (a toast by default) instead of cancelling the screen's scope and reaching
     * the uncaught handler. Cancellation is never an error.
     */
    protected fun launchLoading(onError: (Exception) -> Unit = { toastPageError() }, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            coroutineScope(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    protected fun toastPageError() = Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()

    fun string(id: Int, vararg args: Any): String = activity.getString(id, *args)
}
