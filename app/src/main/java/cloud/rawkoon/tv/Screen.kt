package cloud.rawkoon.tv

import android.content.Context
import android.view.KeyEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * One page of the app. Pages are plain views on a stack in [MainActivity]: the ones below stay
 * built but hidden, so going back restores them as they were, focus included.
 */
abstract class Screen(val activity: MainActivity) {
    val context: Context get() = activity
    val app: App get() = activity.app

    /** Cancelled when the screen leaves the stack. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        lastFocus = view.findFocus()
    }

    /** True when the screen handled Back itself; otherwise the stack pops it. */
    open fun onBack(): Boolean = false

    /** Keys before the focused view sees them; true when consumed. */
    open fun onKey(event: KeyEvent): Boolean = false

    open fun destroy() = scope.cancel()

    fun string(id: Int, vararg args: Any): String = activity.getString(id, *args)
}
