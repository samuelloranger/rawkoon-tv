package cloud.rawkoon.tv.screens

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.ImageUrls
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.ui.Backdrop
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.NavRail
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException

/**
 * A profile's home: the backdrop, the header for the focused item, and the rows under it, with the
 * nav rail on the left. Focus moves only touch render properties (glides, ring fades); the header
 * and the backdrop follow once focus rests.
 */
class HomeScreen(activity: MainActivity, private val profile: Profile) : Screen(activity) {
    override val view = FrameLayout(context)
    private val session = JellyfinSession(activity, profile)
    private val main = Handler(Looper.getMainLooper())
    private val backdrop = Backdrop(context, app.images.disk)
    private val header = Header(context, app.images)
    private val rows = Rows(context)
    private val dim = FlatView(context).apply {
        setBackgroundColor(0x99000000.toInt())
        alpha = 0f
    }
    private val rail = NavRail(
        context,
        profile.name,
        profile.avatarTag?.let { ImageUrls(profile.jellyfinUrl).user(profile.jellyfinUserId, it) },
        app.images,
        Destination.HOME,
        ::navigate,
    )
    private val message = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
    }
    private var backdropUrl: String? = null
    private val settleBackdrop = Runnable { backdrop.show(backdropUrl) }

    init {
        view.addView(backdrop, fill())
        view.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(26)
        })
        view.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP - 6)
        })
        view.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.addView(dim, fill())
        view.addView(rail, FrameLayout.LayoutParams(context.dp(Tokens.RAIL_OPEN_W), ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        rail.onExit = { rows.focusedSection()?.row?.remembered() }
        rail.onOpenChanged = { open -> dim.animate().alpha(if (open) 1f else 0f).setDuration(Tokens.DRAWER_MS).start() }
        load()
    }

    private fun load() {
        message.visibility = View.GONE
        scope.launch {
            try {
                show(session.homeRows())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val expired = e is InvalidStatusException && e.status == 401
                showMessage(string(if (expired) R.string.error_session else R.string.home_error), retry = !expired)
            }
        }
    }

    private fun show(list: List<HomeRow>) {
        rows.column.removeAllViews()
        rows.reset()
        if (list.isEmpty()) {
            showMessage(string(R.string.home_empty), retry = true)
            return
        }
        for (row in list) {
            val section = Section(context, row.id, row.title)
            section.row.setCards(row.items.map { item -> Card(context, item, row.landscape, app.images, ::onFocused, ::open) })
            section.row.onLeftEdge = { rail.activeItem() }
            rows.column.addView(section)
        }
        if (view.findFocus() == null || view.findFocus() === rail.activeItem()) focusDefault()
    }

    private fun showMessage(text: String, retry: Boolean) {
        message.removeAllViews()
        message.addView(label(context, 16f, Tokens.textSoft, Fonts.medium(context)).apply { this.text = text })
        if (retry) {
            val button = PillButton(context, string(R.string.try_again), primary = true)
            button.setOnClickListener { load() }
            message.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
            button.requestFocus()
        } else {
            val button = PillButton(context, string(R.string.back_to_profiles), primary = true)
            button.setOnClickListener { activity.pop() }
            message.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
            button.requestFocus()
        }
        message.visibility = View.VISIBLE
    }

    private fun onFocused(card: Card) {
        header.show(card.item)
        backdropUrl = card.item.backdropUrl?.let { "$it&maxWidth=1920" }
        main.removeCallbacks(settleBackdrop)
        main.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
    }

    private fun open(card: Card) = comingSoon()

    private fun navigate(destination: Destination) {
        when (destination) {
            Destination.PROFILE -> activity.pop()
            Destination.HOME -> rows.focusedSection()?.row?.restoreFocus()
            Destination.SETTINGS -> activity.push(SettingsScreen(activity))
            else -> comingSoon()
        }
    }

    private fun comingSoon() = Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()

    override fun focusDefault() {
        rows.sections().firstOrNull()?.row?.restoreFocus()
    }

    /** Back on the page goes to the rail, as in Wholphin; Back on the rail leaves the app. */
    override fun onBack(): Boolean {
        if (rail.isOpen) {
            activity.finish()
        } else {
            rail.activeItem()?.requestFocus()
        }
        return true
    }

    override fun destroy() {
        main.removeCallbacks(settleBackdrop)
        super.destroy()
    }
}
