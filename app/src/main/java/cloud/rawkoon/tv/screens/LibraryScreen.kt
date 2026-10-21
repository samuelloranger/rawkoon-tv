package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.LibraryFilter
import cloud.rawkoon.tv.jellyfin.LibraryQuery
import cloud.rawkoon.tv.jellyfin.LibrarySort
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.PagedItems
import cloud.rawkoon.tv.jellyfin.isSessionEnded
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.LetterColumn
import cloud.rawkoon.tv.ui.MessagePanel
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.PosterCell
import cloud.rawkoon.tv.ui.PosterGrid
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.isWithin
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * The Movies, Shows or Collections grid ([kind]), or, given a [parentId], the titles of one
 * collection. The grid pages the server 100 titles at a time and recycles its posters, so a
 * library of thousands scrolls as smoothly as a short one; the A to Z column jumps by asking the
 * server how many titles come before a letter. The backdrop follows the focused poster once
 * focus rests.
 */
class LibraryScreen(
    activity: MainActivity,
    session: JellyfinSession,
    val kind: Destination,
    private val parentId: String? = null,
    private val collectionTitle: String? = null,
) : RailScreen(activity, session, kind) {
    private val paged = PagedItems()
    private val grid = PosterGrid(context, app.images, paged)
    private val letters = LetterColumn(context, ::jumpToLetter)
    private val count = label(context, 12f, Tokens.textMuted, Fonts.medium(context))
    private val sortButton = PillButton(context, "")
    private val filterButton = PillButton(context, "")
    private val message = MessagePanel(context)
    private val bar = object : LinearLayout(context) {
        /** Down returns to the grid, Left past the first button to the rail; nothing else leaves the bar. */
        override fun focusSearch(focused: View?, direction: Int): View? {
            val next = super.focusSearch(focused, direction)
            return when {
                direction == View.FOCUS_DOWN -> grid.focusTarget() ?: focused
                next != null && next.isWithin(this) -> next
                direction == View.FOCUS_LEFT -> rail.activeItem() ?: focused
                else -> focused
            }
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }.noClip()
    private var dialog: ChoiceDialog? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var focusWhenLoaded = false
    private var backdropUrl: String? = null
    private val settleBackdrop = Runnable { backdrop.show(backdropUrl) }

    /** What this page lists: collections, or movies and shows inside one. */
    private val types = when {
        kind == Destination.MOVIES -> listOf(BaseItemKind.MOVIE)
        kind == Destination.SHOWS -> listOf(BaseItemKind.SERIES)
        parentId == null -> listOf(BaseItemKind.BOX_SET)
        else -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
    }

    private val host = object : LibraryLoader.Host {
        override fun restarted(jumping: Boolean) {
            if (message.hasFocus()) focusWhenLoaded = true
            // A restarted grid is no longer at a letter
            if (!jumping) letters.active = null
            message.hide()
            grid.reset()
            count.text = ""
        }

        override fun firstPageLoaded(total: Int, jumpIndex: Int?) {
            grid.pageLoaded(0, firstLoad = true, arriving = isFullyShown && total > 0)
            count.text = context.resources.getQuantityString(R.plurals.library_count, total, total)
            if (total == 0) {
                showMessage(string(if (loader.query.filter == LibraryFilter.ALL) R.string.library_empty else R.string.library_empty_filtered), retry = true)
                return
            }
            if (jumpIndex != null) grid.jumpTo(jumpIndex.coerceAtMost(total - 1))
            if (focusWhenLoaded) focusGridWhenLaidOut()
        }

        override fun pageFetched(page: Int) {
            grid.pageLoaded(page, firstLoad = false)
            if (grid.hasFocus()) showBackdropOf(grid.lastPosition)
        }

        override fun pageRefreshed(page: Int) = grid.pageLoaded(page, firstLoad = false)

        override fun loadFailed(e: Exception) {
            val expired = e.isSessionEnded()
            showMessage(string(if (expired) R.string.error_session else R.string.library_error), retry = !expired)
        }

        override fun jumpTo(index: Int) = grid.jumpTo(index)
    }

    private val loader: LibraryLoader = LibraryLoader(scope, session, paged, types, parentId, host)

    init {
        backdrop.strength = 0.5f
        bar.addView(label(context, 32f, Tokens.textPrimary, Fonts.title(context)).apply {
            text = collectionTitle ?: string(kind.label)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(TITLE_GAP) })
        bar.addView(sortButton)
        bar.addView(filterButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(BUTTON_GAP) })
        bar.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(COUNT_GAP) })
        content.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START - Tokens.RING_INSET - BAR_BLEED)
            topMargin = context.dp(BAR_TOP)
        })

        // The grid is clipped to its own box, so rows gliding up never pass over the bar
        val gridBox = FrameLayout(context)
        gridBox.addView(grid, FrameLayout.LayoutParams(context.dp(Tokens.CONTENT_START - Tokens.RING_INSET + PosterGrid.SPAN * PosterCell.CELL_W), ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(gridBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = context.dp(GRID_TOP) })
        content.addView(letters, FrameLayout.LayoutParams(context.dp(LETTERS_W), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply {
            topMargin = context.dp(GRID_TOP + LETTERS_TOP)
            bottomMargin = context.dp(LETTERS_BOTTOM)
            marginEnd = context.dp(LETTERS_END)
        })
        content.addView(message, MessagePanel.params())

        grid.onFocused = ::showBackdropOf
        grid.onOpen = ::open
        grid.onPages = loader::fetch
        grid.onLeftEdge = { rail.activeItem() }
        grid.onTopEdge = { sortButton }
        grid.onRightEdge = { letters }
        letters.onLeft = { grid.focusTarget() }
        sortButton.setOnClickListener { showSortChoices() }
        filterButton.setOnClickListener { showFilterChoices() }
        paintButtons()
        loader.reload()
    }

    private fun paintButtons() {
        sortButton.label.text = string(R.string.library_sort_button, string(sortLabel(loader.query.sort)))
        filterButton.label.text = string(R.string.library_filter_button, string(filterLabel(loader.query.filter)))
    }

    private fun showSortChoices() = present(sortDialog(context, loader.query, ::apply))

    private fun showFilterChoices() = present(filterDialog(context, loader.query, ::apply))

    private fun present(choices: ChoiceDialog) {
        dialog = choices
        choices.open(view)
    }

    /** Applies a new sort or filter: the grid starts over from the first title. */
    private fun apply(next: LibraryQuery) {
        if (next == loader.query) return
        loader.query = next
        paintButtons()
        loader.reload()
    }

    /** The A to Z column picked [letter]: the grid is sorted by title first if it was not. */
    private fun jumpToLetter(letter: Char) {
        letters.active = letter
        if (loader.query.sort != LibrarySort.TITLE) {
            loader.query = loader.query.copy(sort = LibrarySort.TITLE)
            paintButtons()
            loader.reload(jump = letter)
            return
        }
        loader.jumpTo(letter)
    }

    private fun showMessage(text: String, retry: Boolean) {
        message.show(text, messageAction(if (retry) ({ loader.reload() }) else null), takeFocus = focusWhenLoaded)
        focusWhenLoaded = false
    }

    private fun showBackdropOf(position: Int) {
        val url = backdropRequest(paged[position]?.backdropUrl)
        if (url == backdropUrl) return
        backdropUrl = url
        mainHandler.removeCallbacks(settleBackdrop)
        mainHandler.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
    }

    /** A collection opens its own grid; a movie or show opens its page. */
    private fun open(item: MediaItem) {
        if (kind == Destination.COLLECTIONS && parentId == null) {
            activity.push(LibraryScreen(activity, session, kind, parentId = item.id, collectionTitle = item.title))
        } else {
            openItem(item)
        }
    }

    /**
     * Hands focus to the grid once the posters just loaded are laid out: before that there is no
     * view to take it. Wanted until a poster has it, or someone moved on to something other than
     * the bar and keeps their place.
     */
    private fun focusGridWhenLaidOut() {
        stopWaitingForLayout()
        val listener = ViewTreeObserver.OnGlobalLayoutListener { if (focusGrid()) stopWaitingForLayout() }
        layoutListener = listener
        grid.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun stopWaitingForLayout() {
        val listener = layoutListener ?: return
        layoutListener = null
        val observer = grid.viewTreeObserver
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
    }

    /** True when the grid does not need focus any more. */
    private fun focusGrid(): Boolean {
        val focus = view.findFocus()
        if (focus != null && !focus.isWithin(bar)) {
            focusWhenLoaded = false
            return true
        }
        val target = grid.focusTarget() ?: return false
        if (!target.requestFocus()) return false
        focusWhenLoaded = false
        return true
    }

    private fun refresh() = loader.refresh(grid.lastPosition, PosterGrid.PREFETCH, message.visibility == View.VISIBLE)

    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) refresh()
    }

    override val livePage get() = LivePage.LIBRARY

    /**
     * Fetches the posters around the focused one again for their marks. A library whose size changed
     * starts over only when focus is off the grid: a grid under the user's hands never jumps.
     */
    override fun onLive(kinds: Set<LiveKind>) {
        loader.refresh(grid.lastPosition, PosterGrid.PREFETCH, message.visibility == View.VISIBLE, restartOnResize = !grid.hasFocus())
    }

    override fun contentFocus(): View? = grid.focusTarget()

    /** The first page may still be on its way: focus goes to the grid as soon as it has posters. */
    override val isRailPage get() = true

    override fun focusDefault() {
        val target = grid.focusTarget()
        if (target != null) target.requestFocus() else focusWhenLoaded = true
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        return super.onBack()
    }

    override fun destroy() {
        stopWaitingForLayout()
        super.destroy()
    }

    private companion object {
        const val BAR_TOP = 26
        const val GRID_TOP = 84

        /** Pulls the bar left of the first poster's art, to offset the first button's own padding. */
        const val BAR_BLEED = 4
        const val TITLE_GAP = 18
        const val BUTTON_GAP = 4
        const val COUNT_GAP = 14
        const val LETTERS_W = 18
        const val LETTERS_TOP = 8
        const val LETTERS_BOTTOM = 26
        const val LETTERS_END = 4
    }
}
