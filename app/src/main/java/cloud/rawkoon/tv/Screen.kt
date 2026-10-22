package cloud.rawkoon.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
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

    /** False for a page that only fades in, without sliding: the player, whose video surface should not move. */
    open val slides: Boolean get() = true

    /**
     * The part of [view] under chrome every page of its kind shares at the same spot (the rail and
     * the clock). Between two such pages only this part comes in, so the chrome stays still; null
     * when the page has none.
     */
    open val stage: View? get() = null

    /**
     * True once the page is on top and done coming in. Content landing before then appears as it
     * is: it arrives with the page, and motion of its own would redraw the page's layer every frame.
     */
    protected val isFullyShown: Boolean
        get() = view.isShown && listOfNotNull(view, stage).all { it.alpha == 1f && it.translationX == 0f }

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

    /** The app is no longer visible: Home pressed, another app in front, or the TV gone to sleep. Follows [onPause]. */
    open fun onStop() {}

    /** Which live events reload this page while it is on top; [LivePage.OTHER] opens no socket for it. */
    open val livePage: LivePage get() = LivePage.OTHER

    /** The server pushed [kinds] while this page is on top: refresh in place, without moving focus. */
    open fun onLive(kinds: Set<LiveKind>) {}

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
