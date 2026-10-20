package cloud.rawkoon.tv.screens

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.FindGroup
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.isSessionEnded
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.MessagePanel
import cloud.rawkoon.tv.ui.RowItem
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.cardCaption
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException

/**
 * A rail page made of titled rows under a header, like home: home itself, search results and
 * favorites. The header and the backdrop follow focus once it rests; [show] swaps the rows without
 * ever moving focus off the card it is on. Subclasses decide where the rows come from.
 */
abstract class RowsPage(
    activity: MainActivity,
    session: JellyfinSession,
    active: Destination,
    headerTop: Int,
    rowsTop: Int,
    /** The id of the view above the first row, when the page has one: Up from the first row goes to it. */
    private val aboveId: Int = View.NO_ID,
) : RailScreen(activity, session, active) {
    private val header = Header(context, app.images)
    protected val rows = Rows(context)
    private val message = MessagePanel(context)
    private val parking = FocusParking(context)
    private var backdropUrl: String? = null

    /** The focused card, whose neighbours are prefetched once focus rests. */
    private var prefetchFrom: Card? = null
    private val settleBackdrop = Runnable {
        backdrop.show(backdropUrl)
        prefetchFrom?.let(::prefetchAround)
        prefetchFrom = null
    }

    init {
        content.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(headerTop)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(rowsTop)
        })
        content.addView(message, MessagePanel.params())
        content.addView(parking, FrameLayout.LayoutParams(1, 1))
        // The rows are clipped to their area, so rows gliding up never pass over the header
        content.clipChildren = true
        header.visibility = View.INVISIBLE
    }

    /** True when the first item is previewed if rows arrive while focus is off the cards (on the rail, say). */
    protected open val previewsFirstItem: Boolean get() = true

    protected fun hasRows() = rows.column.childCount > 0

    /** Where a Down from above lands: the first row's card, or the message's button when there is no row. */
    protected fun firstFocus(): View? =
        rows.sections().firstOrNull()?.row?.remembered() ?: message.button?.takeIf { it.isShown }

    /**
     * Shows [list], keeping every row whose cards haven't changed. Focus comes back to the same
     * card in the same row within the same main-thread pass, so no frame ever shows it elsewhere.
     */
    protected fun show(list: List<HomeRow>) {
        hideMessage()
        header.visibility = View.VISIBLE
        val current = rows.sections()
        if (current.map { it.id to it.signature } == list.map { it.id to it.signature }) return
        val hadFocus = rows.hasFocus()
        val focusedRow = (rows.column.focusedChild as? Section)?.id?.takeIf { hadFocus }
        val focusedKey = (view.findFocus() as? RowItem)?.key
        val keep = current.associateBy { it.id }
        val next = list.map { row -> keep[row.id]?.takeIf { it.signature == row.signature } ?: section(row) }
        val restore = {
            val section = next.firstOrNull { it.id == focusedRow } ?: next.first()
            focusedKey?.let { section.row.remember(it) }
            section.row.restoreFocus()
        }
        parking.around(hadFocus, rebuild = { replaceRows(next) }, restore = { restore() })
        if (!hadFocus && view.findFocus() == null) restore()
        rewireUp(current.firstOrNull(), next.first())
        if (previewsFirstItem && !hadFocus && view.findFocus() !is Card) list.first().items.firstOrNull()?.let(::preview)
        rows.post { ((rows.column.focusedChild as? Section) ?: next.first()).let { rows.reveal(it) } }
    }

    private fun replaceRows(sections: List<Section>) {
        rows.column.removeAllViews()
        sections.forEach { rows.column.addView(it) }
    }

    /** The first row may have changed or moved: Up from its cards leads to what is above, and from no other. */
    private fun rewireUp(oldFirst: Section?, newFirst: Section) {
        oldFirst?.row?.cards()?.forEach { it.nextFocusUpId = View.NO_ID }
        newFirst.row.cards().forEach { it.nextFocusUpId = aboveId }
    }

    /**
     * Removes every row, and the header and backdrop with them. Focus held in the rows goes to the
     * rail. Does nothing when nothing is shown.
     */
    protected fun clear() {
        if (!hasRows() && backdropUrl == null) return
        header.visibility = View.INVISIBLE
        backdropUrl = null
        mainHandler.removeCallbacks(settleBackdrop)
        mainHandler.post(settleBackdrop)
        if (!hasRows()) return
        parking.around(rows.hasFocus(), rebuild = {
            rows.column.removeAllViews()
            rows.reset()
        }, restore = { rail.activeItem()?.requestFocus() })
    }

    /** Replaces everything with [text]; when that leaves nothing focused, the rail takes focus. */
    protected fun showEmpty(text: String) {
        hideMessage()
        clear()
        showMessage(text)
        if (view.findFocus() == null) rail.activeItem()?.requestFocus()
    }

    protected fun hideMessage() = message.hide()

    /**
     * A centered message; with an [action] (the button's label and what it does) it carries a
     * button, which takes focus only if [takeFocus].
     */
    protected fun showMessage(text: String, action: Pair<String, () -> Unit>? = null, takeFocus: Boolean = false) =
        message.show(text, action, takeFocus, aboveId)

    /**
     * Reports a failed load: an ended session says so (retrying can't help), anything else offers
     * [retry]. With [silentIfRows] a failed refresh of rows already on screen stays silent;
     * otherwise the rows give way to the error, so stale results are never passed off as current.
     */
    protected fun fail(e: Exception, errorText: Int, retry: () -> Unit, takeFocus: Boolean, silentIfRows: Boolean = false) {
        if (e is CancellationException) throw e
        val expired = e.isSessionEnded()
        if (!expired && silentIfRows && hasRows()) return
        clear()
        showMessage(string(if (expired) R.string.error_session else errorText), if (expired) null else messageAction(retry), takeFocus)
    }

    /** One row per group in [found], in its order. */
    protected fun rowsFor(found: Map<FindGroup, List<MediaItem>>) =
        found.map { (group, items) -> HomeRow(group.name, groupTitle(group), items, group.landscape) }

    private fun groupTitle(group: FindGroup) = string(
        when (group) {
            FindGroup.MOVIES -> R.string.find_row_movies
            FindGroup.SERIES -> R.string.find_row_shows
            FindGroup.EPISODES -> R.string.find_row_episodes
            FindGroup.COLLECTIONS -> R.string.find_row_collections
        },
    )

    /** The card for [item] in a row; a page whose cards look or open differently overrides it. */
    protected open fun card(item: MediaItem, landscape: Boolean) =
        Card(context, item, landscape, app.images, ::onFocused, { openItem(it.item) }, cardCaption(item))

    private fun section(row: HomeRow) = Section(context, row.id, row.title).apply {
        signature = row.signature
        this.row.setCards(row.items.map { card(it, row.landscape) })
        this.row.onLeftEdge = { rail.activeItem() }
    }

    private fun preview(item: MediaItem) {
        header.show(item)
        backdropUrl = backdropRequest(item.backdropUrl)
        mainHandler.removeCallbacks(settleBackdrop)
        mainHandler.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
    }

    protected fun onFocused(card: Card) {
        preview(card.item)
        prefetchFrom = card
    }

    /** Fetches, behind visible loads, the logos and backdrops of the cards next to [card], so they are ready when focus gets there. */
    private fun prefetchAround(card: Card) {
        val strip = card.parent as? ViewGroup ?: return
        val index = strip.indexOfChild(card)
        for (i in (index - 1)..(index + 2)) {
            val near = (strip.getChildAt(i) as? Card)?.item ?: continue
            if (near === card.item) continue
            header.prefetch(near)
            backdropRequest(near.backdropUrl)?.let(app.images::warm)
        }
    }

    override fun contentFocus(): View? = super.contentFocus() ?: firstFocus()

}
