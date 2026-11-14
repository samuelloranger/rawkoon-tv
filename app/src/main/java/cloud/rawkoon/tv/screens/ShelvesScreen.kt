package cloud.rawkoon.tv.screens

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.rawkoon.ShelfCover
import cloud.rawkoon.tv.rawkoon.ShelfSummary
import cloud.rawkoon.tv.rawkoon.shelfSource
import cloud.rawkoon.tv.rawkoon.tmdbImage
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.MessagePanel
import cloud.rawkoon.tv.ui.ShelfGrid
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Shelves page: every shelf in its window as a cover card, its name and how many titles it
 * holds over a mosaic of its posters (or its chosen backdrop). A cover opens the shelf; the rail's
 * Shelves entry is this page, next to the library's own destinations. Rawkoon unreachable shows the
 * shelves saved last, and a server without shelves shows an empty page rather than an error.
 */
class ShelvesScreen(activity: MainActivity, session: JellyfinSession) :
    RailScreen(activity, session, Destination.SHELVES) {
    private val title = label(context, 32f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.rk_shelves) }
    private val count = label(context, 12f, Tokens.textMuted, Fonts.medium(context))
    private val grid = ShelfGrid(context, app.images)
    private val message = MessagePanel(context)
    private var loadJob: Job? = null

    /** The shelf whose page was opened last: Back comes back to its cover. */
    private var returningTo: Int? = null
    private var backdropUrl: String? = null
    private val settleBackdrop = Runnable { backdrop.show(backdropUrl) }

    init {
        backdrop.frameFraction = Tokens.FRAME_W_GRID
        backdrop.strength = 0.5f
        content.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(TITLE_TOP)
        })
        content.addView(count, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(COUNT_TOP)
        })
        content.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(GRID_TOP)
        })
        content.addView(message, MessagePanel.params())
        content.clipChildren = true
        grid.onOpen = { shelf ->
            returningTo = shelf.id
            activity.push(ShelfScreen(activity, session, shelf.id, shelf))
        }
        grid.onFocused = ::preview
        grid.onLeftEdge = { rail.activeItem() }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val profile = app.profiles.get(session.profile.id) ?: session.profile
            val source = profile.shelfSource(context)
            val shelves = try {
                source?.shelves()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(string(R.string.rk_shelves_error), retry = true)
                return@launch
            } ?: emptyList()
            show(shelves)
        }
    }

    private fun show(shelves: List<ShelfSummary>) {
        message.hide()
        count.text = if (shelves.isEmpty()) "" else context.resources.getQuantityString(R.plurals.rk_shelf_count, shelves.size, shelves.size)
        grid.setShelves(shelves)
        if (shelves.isEmpty()) {
            showMessage(string(R.string.rk_shelves_empty), retry = false)
            return
        }
        if (view.findFocus() == null) grid.post { grid.focusTarget()?.requestFocus() }
    }

    /** The focused shelf's backdrop, or its first poster, fills the page's art once focus rests. */
    private fun preview(shelf: ShelfSummary) {
        val url = when (val cover = shelf.cover) {
            is ShelfCover.Backdrop -> cover.url
            is ShelfCover.Mosaic -> cover.posters.firstOrNull()
        }
        val request = backdropRequest(url?.let { if (it.contains("image.tmdb.org")) tmdbImage(it) else it })
        if (request == backdropUrl) return
        backdropUrl = request
        mainHandler.removeCallbacks(settleBackdrop)
        mainHandler.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
    }

    /** A centered message; a [retry] reads the shelves again. */
    private fun showMessage(text: String, retry: Boolean) {
        count.text = ""
        message.show(text, messageAction(if (retry) ::load else null), takeFocus = view.findFocus() == null)
    }

    override fun contentFocus(): View? = super.contentFocus() ?: grid.focusTarget()

    override fun focusDefault() {
        grid.focusTarget()?.requestFocus() ?: grid.post { grid.focusTarget()?.requestFocus() }
    }

    /** The rail's own page: another rail destination replaces it instead of piling up. */
    override val isRailPage get() = true

    override val livePage get() = LivePage.SHELVES

    /** A library change rewrites covers and marks; a shelf added or edited in Rawkoon shows on the next visit. */
    override fun onLive(kinds: Set<LiveKind>) {
        if (kinds.any { it != LiveKind.USER_DATA }) load()
    }

    /** Coming back to the page reads the shelves again, and lands on the cover that was opened. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (first) return
        returningTo?.let { id ->
            returningTo = null
            grid.focusShelf(id)
        }
        load()
    }

    override fun destroy() {
        loadJob?.cancel()
        mainHandler.removeCallbacks(settleBackdrop)
        super.destroy()
    }

    private companion object {
        const val TITLE_TOP = 26
        const val COUNT_TOP = 68
        const val GRID_TOP = 92
    }
}
