package cloud.rawkoon.tv.screens

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.RowItem
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.File

/**
 * A profile's home: the backdrop, the header for the focused item, and the rows under it, with the
 * nav rail on the left. Focus moves only touch render properties (glides, ring fades); the header
 * and the backdrop follow once focus rests. The last rows shown are kept on disk, so the page
 * opens at once and refreshes in place.
 */
class HomeScreen(activity: MainActivity, profile: Profile) : RailScreen(activity, JellyfinSession(activity, profile), Destination.HOME) {
    private val main = Handler(Looper.getMainLooper())
    private val header = Header(context, app.images)
    private val rows = Rows(context)
    private val message = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
    }
    private val cache = File(context.cacheDir, "home-${profile.id}.json")
    private var backdropUrl: String? = null
    private val settleBackdrop = Runnable { backdrop.show(backdropUrl) }

    init {
        content.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(26)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP - 6)
        })
        content.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        // The rows are clipped to their area, so rows gliding up never pass over the header
        content.clipChildren = true
        load()
    }

    private fun load() {
        message.visibility = View.GONE
        scope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { HomeRow.listFromJson(cache.readText()) }.getOrNull() }
            if (cached != null && rows.column.childCount == 0) show(cached)
            try {
                val fresh = session.homeRows()
                show(fresh)
                withContext(Dispatchers.IO) { runCatching { cache.writeText(HomeRow.listToJson(fresh)) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val expired = e is InvalidStatusException && e.status == 401
                // With rows already on screen from the cache, a failed refresh stays silent
                if (expired || rows.column.childCount == 0) showMessage(string(if (expired) R.string.error_session else R.string.home_error), retry = !expired)
            }
        }
    }

    /**
     * Shows [list], keeping every row whose cards haven't changed. Focus comes back to the same
     * card in the same row within the same main-thread pass, so no frame ever shows it elsewhere.
     */
    private fun show(list: List<HomeRow>) {
        if (list.isEmpty()) {
            if (rows.column.childCount == 0) showMessage(string(R.string.home_empty), retry = true)
            return
        }
        message.visibility = View.GONE
        val current = rows.sections()
        if (current.map { it.id to it.signature } == list.map { it.id to it.signature }) return
        val hadFocus = rows.hasFocus()
        val focusedRow = rows.focusedSection()?.takeIf { hadFocus }?.id
        val focusedKey = (view.findFocus() as? RowItem)?.key
        val keep = current.associateBy { it.id }
        rows.column.removeAllViews()
        for (row in list) rows.column.addView(keep[row.id]?.takeIf { it.signature == row.signature } ?: section(row))
        if (hadFocus || view.findFocus() == null) {
            val section = rows.sections().firstOrNull { it.id == focusedRow } ?: rows.sections().first()
            focusedKey?.let { section.row.remember(it) }
            section.row.restoreFocus()
        }
        // Sections moved: re-anchor once the new layout is in
        rows.post { rows.focusedSection()?.let { rows.reveal(it) } }
    }

    private fun section(row: HomeRow) = Section(context, row.id, row.title).apply {
        signature = row.signature
        this.row.setCards(row.items.map { item -> Card(context, item, row.landscape, app.images, ::onFocused, { openItem(it.item) }) })
        this.row.onLeftEdge = { rail.activeItem() }
    }

    private fun showMessage(text: String, retry: Boolean) {
        message.removeAllViews()
        message.addView(label(context, 16f, Tokens.textSoft, Fonts.medium(context)).apply { this.text = text })
        val button = PillButton(context, string(if (retry) R.string.try_again else R.string.back_to_profiles), primary = true)
        button.setOnClickListener { if (retry) load() else activity.popTo { it is ProfilesScreen } }
        message.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
        message.visibility = View.VISIBLE
        button.requestFocus()
    }

    private fun onFocused(card: Card) {
        header.show(card.item)
        backdropUrl = card.item.backdropUrl?.let { "$it&maxWidth=1920" }
        main.removeCallbacks(settleBackdrop)
        main.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
        prefetchAround(card)
    }

    /** Fetches the logos and backdrops of the cards next to [card], so they are ready when focus gets there. */
    private fun prefetchAround(card: Card) {
        val strip = card.parent as? ViewGroup ?: return
        val index = strip.indexOfChild(card)
        for (i in (index - 1)..(index + 2)) {
            val near = (strip.getChildAt(i) as? Card)?.item ?: continue
            if (near === card.item) continue
            header.prefetch(near)
            near.backdropUrl?.let { app.images.warm("$it&maxWidth=1920") }
        }
    }

    override fun contentFocus(): View? = rows.focusedSection()?.row?.remembered()

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
