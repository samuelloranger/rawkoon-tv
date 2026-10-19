package cloud.rawkoon.tv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
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

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Language.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Tokens.background) }
        setContentView(root)
        // "Who's watching?" on every launch
        push(ProfilesScreen(this))
    }

    fun push(screen: Screen) {
        stack.lastOrNull()?.let { it.onHidden(); hide(it.view) }
        stack += screen
        show(screen.view, fresh = true)
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
                show(s.view, fresh = true)
                s.onShown(first = true)
            } else {
                root.addView(s.view, fill())
                s.view.visibility = View.GONE
            }
        }
    }

    fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        stack.removeLast().let { remove(it) }
        val below = stack.last()
        below.view.visibility = View.VISIBLE
        below.view.alpha = 1f
        below.onShown(first = false)
    }

    private fun show(view: View, fresh: Boolean) {
        if (view.parent == null) root.addView(view, fill())
        view.visibility = View.VISIBLE
        if (fresh) {
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(Tokens.SCREEN_FADE_MS).setInterpolator(Tokens.ease).start()
        }
    }

    private fun hide(view: View) {
        view.animate().cancel()
        view.visibility = View.GONE
    }

    private fun remove(screen: Screen) {
        screen.destroy()
        root.removeView(screen.view)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (stack.lastOrNull()?.onKey(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Back is handled per screen; the platform callback API isn't needed on TV")
    override fun onBackPressed() {
        if (stack.lastOrNull()?.onBack() == true) return
        pop()
    }

    override fun onDestroy() {
        stack.forEach { it.destroy() }
        super.onDestroy()
    }
}
