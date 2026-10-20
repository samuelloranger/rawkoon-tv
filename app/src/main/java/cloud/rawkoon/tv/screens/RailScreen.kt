package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.jellyfin.ImageUrls
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.Backdrop
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.NavRail
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.isWithin
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.whileAttachedOnFocusChange

/**
 * A page of a profile's library: the backdrop, the page's own [content], and the nav rail over
 * it, which dims the page while open. Home, details and seasons all build on it.
 */
abstract class RailScreen(activity: MainActivity, val session: JellyfinSession, val active: Destination) : Screen(activity) {
    final override val view = FrameLayout(context)
    protected val backdrop = Backdrop(context, app.images.disk)
    protected val content = FrameLayout(context).noClip()
    private val dim = FlatView(context).apply {
        setBackgroundColor(0x99000000.toInt())
        alpha = 0f
    }
    protected val rail = NavRail(
        context,
        session.profile.name,
        session.profile.avatarTag?.let { ImageUrls(session.profile.jellyfinUrl).user(session.profile.jellyfinUserId, it) },
        app.images,
        active,
        ::navigate,
    )

    private var lastContent: View? = null

    init {
        view.addView(backdrop, fill())
        view.addView(content, fill())
        view.addView(dim, fill())
        view.addView(rail, FrameLayout.LayoutParams(context.dp(Tokens.RAIL_OPEN_W), ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        rail.onExit = { contentFocus() }
        content.whileAttachedOnFocusChange { now -> if (now != null && now.isWithin(content)) lastContent = now }
        rail.onOpenChanged = { open -> dim.animate().alpha(if (open) 1f else 0f).setDuration(Tokens.DRAWER_MS).start() }
    }

    /**
     * A page left through the rail (another rail page opened) comes back to its content, not to
     * the rail entry that led away.
     */
    override fun focusToRemember(focused: View): View =
        if (focused.isWithin(rail)) contentFocus() ?: focused else focused

    /** Where focus goes when it leaves the rail: what the page had focused last. */
    open fun contentFocus(): View? = lastContent?.takeIf { it.isAttachedToWindow && it.isShown }

    protected open fun navigate(destination: Destination) {
        when (destination) {
            Destination.PROFILE -> activity.popTo { it is ProfilesScreen }
            Destination.HOME -> if (this is HomeScreen) contentFocus()?.requestFocus() else activity.popTo { it is HomeScreen }
            Destination.SETTINGS -> activity.push(SettingsScreen(activity))
            Destination.SEARCH -> open(destination) { SearchScreen(activity, session) }
            Destination.FAVORITES -> open(destination) { FavoritesScreen(activity, session) }
            Destination.DISCOVER -> open(destination) { DiscoverScreen(activity, session) }
            Destination.MOVIES, Destination.SHOWS, Destination.COLLECTIONS -> open(destination) { LibraryScreen(activity, session, destination) }
        }
    }

    /**
     * Opens a rail page. From home or a title page it goes on top; from another rail page it takes
     * that page's place, so moving between Movies, Search and the rest never piles up screens.
     */
    private fun open(destination: Destination, make: () -> RailScreen) {
        if (isRailPage && active == destination) {
            contentFocus()?.requestFocus() ?: rail.activeItem()?.requestFocus()
            return
        }
        if (isRailPage) activity.replace(make()) else activity.push(make())
    }

    /** True for the pages the rail itself opens (library, search, favorites, discover): each declares it. */
    protected open val isRailPage: Boolean get() = false

    /** A message's button: "Try again" with a [retry], else "Back to profiles" (retrying can't help when the session ended). */
    protected fun messageAction(retry: (() -> Unit)?): Pair<String, () -> Unit> =
        if (retry != null) string(R.string.try_again) to retry else string(R.string.back_to_profiles) to { activity.popTo { it is ProfilesScreen } }

    protected fun comingSoon() = Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()

    /** Back from the rail returns to the page; from the page it goes back a screen. */
    override fun onBack(): Boolean {
        if (rail.isOpen) {
            contentFocus()?.requestFocus()
            return true
        }
        return false
    }

    /** Opens what a card shows: a movie's or show's page, or an episode's season. */
    protected fun openItem(item: MediaItem) {
        when (item.kind) {
            MediaItem.Kind.MOVIE, MediaItem.Kind.SERIES ->
                activity.push(DetailsScreen(activity, session, item))
            MediaItem.Kind.EPISODE, MediaItem.Kind.SEASON -> {
                val series = item.seriesId ?: return comingSoon()
                activity.push(SeasonScreen(activity, session, series, item.seasonId, item.takeIf { it.kind == MediaItem.Kind.EPISODE }?.id))
            }
            else -> comingSoon()
        }
    }
}
