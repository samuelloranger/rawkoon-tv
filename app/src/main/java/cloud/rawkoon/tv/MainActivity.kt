package cloud.rawkoon.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import cloud.rawkoon.tv.jellyfin.MediaWords
import cloud.rawkoon.tv.screens.ProfilesScreen
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.fill

/**
 * The only activity. It holds a stack of [Screen]s: a new screen fades in over the one below,
 * which stays built (and hidden) until you come back to it.
 */
class MainActivity : Activity() {
    val app: App get() = application as App
    private lateinit var root: FrameLayout
    private val stack = ArrayList<Screen>()

    override fun attachBaseContext(base: Context) {
        val wrapped = Language.wrap(base)
        // Recreating after a language change wraps again: the media words follow it
        MediaWords.source = wrapped.resources
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        // "Who's watching?" on every launch
        push(ProfilesScreen(this))
    }

    fun push(screen: Screen) {
        stack.lastOrNull()?.let { it.onHidden(); hide(it.view) }
        stack += screen
        show(screen.view)
        screen.onShown(first = true)
    }

    /** Replaces the top screen, e.g. sign-in once it succeeded. */
    fun replace(screen: Screen) {
        stack.removeLastOrNull()?.let { remove(it) }
        push(screen)
    }

    /** Clears the stack down to [screen], e.g. a new profile's home on top of "Who's watching?". */
    fun resetTo(vararg screens: Screen) {
        stack.toList().forEach { remove(it) }
        stack.clear()
        screens.forEachIndexed { i, s ->
            stack += s
            if (i == screens.lastIndex) {
                show(s.view)
                s.onShown(first = true)
            } else {
                root.addView(s.view, fill())
                s.view.visibility = View.GONE
            }
        }
    }

    /** Goes back down the stack to the first screen (from the top) that matches, e.g. home from a details page. */
    fun popTo(match: (Screen) -> Boolean) {
        val target = stack.lastOrNull(match) ?: return
        if (stack.last() === target) return
        // Only the target is shown again: the screens above it are dropped without a visit, so none
        // of them reloads on the way out
        val dropped = ArrayList<Screen>()
        while (stack.last() !== target) dropped += stack.removeAt(stack.lastIndex)
        target.view.visibility = View.VISIBLE
        target.view.alpha = 1f
        target.onShown(first = false)
        dropped.forEach { remove(it) }
    }

    fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        // removeAt, not removeLast: the Java 21 list method only exists on Android 15+
        val top = stack.removeAt(stack.lastIndex)
        val below = stack.last()
        // The page below takes focus back before the top one goes: removing a focused view first
        // would hand focus to whatever Android finds, and that can start things (a season tab loads)
        below.view.visibility = View.VISIBLE
        below.view.alpha = 1f
        below.onShown(first = false)
        remove(top)
    }

    private fun show(view: View) {
        if (view.parent == null) root.addView(view, fill())
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(Tokens.SCREEN_FADE_MS).setInterpolator(Tokens.ease).start()
    }

    private fun hide(view: View) {
        view.animate().cancel()
        view.visibility = View.GONE
    }

    private fun remove(screen: Screen) {
        screen.destroy()
        root.removeView(screen.view)
    }

    /**
     * Back is the remote's key: handled here on release, so it works the same on every Android
     * version without the back-callback APIs. TVs have no back gesture to preview.
     */
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (stack.lastOrNull()?.onKey(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && stack.lastOrNull()?.onBack() != true) pop()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        stack.lastOrNull()?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        stack.lastOrNull()?.onResume()
    }

    override fun onDestroy() {
        stack.forEach { it.destroy() }
        super.onDestroy()
    }
}
