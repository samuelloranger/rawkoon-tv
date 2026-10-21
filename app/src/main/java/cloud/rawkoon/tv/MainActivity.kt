package cloud.rawkoon.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.MediaWords
import cloud.rawkoon.tv.launcher.LauncherLink
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.live.LiveUpdates
import cloud.rawkoon.tv.live.RawkoonLive
import cloud.rawkoon.tv.live.RawkoonLiveSession
import cloud.rawkoon.tv.live.RawkoonToast
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.rawkoon.rawkoonEndpoint
import cloud.rawkoon.tv.screens.HomeScreen
import cloud.rawkoon.tv.screens.PinScreen
import cloud.rawkoon.tv.screens.ProfilesScreen
import cloud.rawkoon.tv.screens.RailScreen
import cloud.rawkoon.tv.screens.linkPage
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.fill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The only activity. It holds a stack of [Screen]s: a new screen fades in over the one below,
 * which stays built (and hidden) until you come back to it.
 */
class MainActivity : Activity() {
    val app: App get() = application as App
    private lateinit var root: FrameLayout
    private val stack = ArrayList<Screen>()
    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val live = LiveUpdates(liveScope, ::deliverLive)
    private val rawkoonLive = RawkoonLive(liveScope, ::deliverLive, ::toastLive)
    private var foreground = false

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
        // A recreation (language change) keeps the intent: only a real launch opens its card
        val link = if (savedInstanceState == null) LauncherLink.from(intent) else null
        // "Who's watching?" on every launch; a plain launch (no card, not a recreation) may skip it
        push(ProfilesScreen(this, autoOpen = savedInstanceState == null && link == null))
        openLink(link)
    }

    /** A launcher card tapped while the app runs; any other intent leaves the screens as they are. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val link = LauncherLink.from(intent) ?: return
        // Start from "Who's watching?" again, so a PIN or the home never lands on a stale stack
        resetTo(ProfilesScreen(this))
        openLink(link)
    }

    /**
     * Opens a launcher card's page on top of its profile's home: straight away, or after the PIN
     * for a profile that has one. "Who's watching?" stays below it, and a profile that is gone
     * leaves the normal launch.
     */
    private fun openLink(link: LauncherLink?) {
        val profile = app.profiles.get(link?.profileId ?: return) ?: return
        if (profile.hasPin) {
            push(PinScreen(this, PinScreen.Mode.Verify(profile)) { enter(profile, link) })
        } else {
            enter(profile, link)
        }
    }

    private fun enter(profile: Profile, link: LauncherLink) {
        val link = link.trusted(profile)
        app.profiles.markUsed(profile.id)
        val home = HomeScreen(this, app.profiles.get(profile.id) ?: profile)
        resetTo(ProfilesScreen(this), home)
        push(linkPage(this, home.session, link))
    }

    fun push(screen: Screen) {
        stack.lastOrNull()?.let { it.onHidden(); hide(it.view) }
        stack += screen
        show(screen.view)
        screen.onShown(first = true)
        syncLive()
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
        syncLive()
    }

    /** Goes back down the stack to the first screen (from the top) that matches, e.g. home from a details page. */
    fun popTo(match: (Screen) -> Boolean) {
        val target = stack.lastOrNull(match) ?: return
        if (stack.last() === target) return
        // Only the target is shown again: the screens above it are dropped without a visit, so none
        // of them reloads on the way out
        val dropped = ArrayList<Screen>()
        while (stack.last() !== target) dropped += stack.removeAt(stack.lastIndex)
        hideKeyboard()
        target.view.visibility = View.VISIBLE
        target.view.alpha = 1f
        target.onShown(first = false)
        dropped.forEach { remove(it) }
        syncLive()
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
        hideKeyboard()
        below.view.visibility = View.VISIBLE
        below.view.alpha = 1f
        below.onShown(first = false)
        remove(top)
        syncLive()
    }

    /**
     * The live connections follow the page on top: Jellyfin's socket while that page reacts to its
     * events, Rawkoon's two streams (when the profile has a Rawkoon sign-in) on every page of a
     * profile, so a "ready" toast reaches any of them. All close on profiles, settings, the player and
     * in the background, so a profile switch or a sign-out never leaves one behind.
     */
    private fun syncLive() {
        val page = (stack.lastOrNull() as? RailScreen)?.takeIf { foreground && it.livePage != LivePage.OTHER }
        val jellyfin = page?.takeIf { it.livePage.followsJellyfin }?.session
        if (jellyfin != null) live.connect(jellyfin) else live.disconnect()
        val rawkoon = page?.takeIf { it.livePage.followsRawkoon }?.let { rawkoonSession(it.session.profile) }
        if (rawkoon != null) rawkoonLive.connect(rawkoon) else rawkoonLive.disconnect()
    }

    /** Read from the store: linking the account changes the saved profile, not the page's copy. */
    private fun rawkoonSession(shown: Profile): RawkoonLiveSession? {
        val profile = app.profiles.get(shown.id) ?: shown
        val endpoint = profile.rawkoonEndpoint(this) ?: return null
        val api = profile.rawkoonDiscover(this) ?: return null
        return RawkoonLiveSession(
            endpoint,
            requests = { api.requests() },
            userId = {
                profile.rawkoonUserId ?: api.userId().ifBlank { null }?.also { id ->
                    app.profiles.get(profile.id)?.let { app.profiles.save(it.copy(rawkoonUserId = id)) }
                }
            },
        )
    }

    private fun toastLive(toast: RawkoonToast) {
        if (!foreground) return
        val text = when (toast) {
            is RawkoonToast.Ready -> getString(R.string.rk_title_ready, toast.title)
            is RawkoonToast.Server -> toast.text
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun deliverLive(kinds: Set<LiveKind>) {
        val top = stack.lastOrNull() ?: return
        if (foreground && top.livePage.reactsToAny(kinds)) top.onLive(kinds)
    }

    /** A keyboard opened on the screen being left would otherwise stay up over the next one. */
    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun show(view: View) {
        hideKeyboard()
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
        foreground = false
        syncLive()
        stack.lastOrNull()?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        syncLive()
        stack.lastOrNull()?.onResume()
    }

    override fun onDestroy() {
        live.disconnect()
        rawkoonLive.disconnect()
        liveScope.cancel()
        stack.forEach { it.destroy() }
        super.onDestroy()
    }
}
