package cloud.rawkoon.tv.screens

import android.os.Handler
import android.os.Looper
import android.util.Log
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
import cloud.rawkoon.tv.jellyfin.libraryLetterIndex
import cloud.rawkoon.tv.jellyfin.libraryPage
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.LetterColumn
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.PosterCell
import cloud.rawkoon.tv.ui.PosterGrid
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.isWithin
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
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
    private val main = Handler(Looper.getMainLooper())
    private val paged = PagedItems()
    private val grid = PosterGrid(context, app.images, paged)
    private val letters = LetterColumn(context, ::jumpToLetter)
    private val count = label(context, 12f, Tokens.textMuted, Fonts.medium(context))
    private val sortButton = PillButton(context, "")
    private val filterButton = PillButton(context, "")
    private val message = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
    }
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
    private var query = LibraryQuery()
    private var loading: Job? = null
    private var jumping: Job? = null
    private var refreshing: Job? = null

    /** The page fetches on their way, by page; a restarted grid has no use for them. */
    private val pageJobs = HashMap<Int, Job>()
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

    init {
        backdrop.strength = 0.5f
        // Half the image shows behind the grid: composed at half resolution, its fades cost the GPU a quarter
        backdrop.resolution = 0.5f
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
        content.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        grid.onFocused = ::showBackdropOf
        grid.onOpen = ::open
        grid.onPages = ::fetch
        grid.onLeftEdge = { rail.activeItem() }
        grid.onTopEdge = { sortButton }
        grid.onRightEdge = { letters }
        letters.onLeft = { grid.focusTarget() }
        sortButton.setOnClickListener { showSortChoices() }
        filterButton.setOnClickListener { showFilterChoices() }
        paintButtons()
        reload()
    }

    private fun paintButtons() {
        sortButton.label.text = string(R.string.library_sort_button, string(sortLabel(query.sort)))
        filterButton.label.text = string(R.string.library_filter_button, string(filterLabel(query.filter)))
    }

    private fun sortLabel(sort: LibrarySort) = when (sort) {
        LibrarySort.ADDED -> R.string.library_sort_added
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.YEAR -> R.string.library_sort_year
        LibrarySort.RATING -> R.string.library_sort_rating
    }

    private fun filterLabel(filter: LibraryFilter) = when (filter) {
        LibraryFilter.ALL -> R.string.library_filter_all
        LibraryFilter.UNWATCHED -> R.string.library_filter_unwatched
        LibraryFilter.FAVORITES -> R.string.library_filter_favorites
    }

    private fun showSortChoices() {
        val choices = LibrarySort.entries.map { sort -> string(sortLabel(sort)) to { apply(query.copy(sort = sort)) } } +
            (string(if (query.reverse) R.string.library_reverse_on else R.string.library_reverse_off) to { apply(query.copy(reverse = !query.reverse)) })
        present(ChoiceDialog(context, string(R.string.library_sort_dialog), choices))
    }

    private fun showFilterChoices() {
        val choices = LibraryFilter.entries.map { filter -> string(filterLabel(filter)) to { apply(query.copy(filter = filter)) } }
        present(ChoiceDialog(context, string(R.string.library_filter_dialog), choices))
    }

    private fun present(choices: ChoiceDialog) {
        dialog = choices
        choices.open(view)
    }

    /** Applies a new sort or filter: the grid starts over from the first title. */
    private fun apply(next: LibraryQuery) {
        if (next == query) return
        query = next
        paintButtons()
        reload()
    }

    /**
     * Fetches the first page and starts the grid over. With [jump], also asks where that letter
     * is and scrolls there; the pages around it are fetched as its posters are shown.
     */
    private fun reload(jump: Char? = null) {
        loading?.cancel()
        refreshing?.cancel()
        // The pages on their way belong to the grid being replaced
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        if (message.hasFocus()) focusWhenLoaded = true
        jumping?.cancel()
        // A restarted grid is no longer at a letter
        if (jump == null) letters.active = null
        message.visibility = View.GONE
        val gen = paged.begin()
        grid.reset()
        count.text = ""
        loading = scope.launch {
            try {
                val (first, index) = coroutineScope {
                    val page = async { session.libraryPage(types, parentId, query, 0, paged.pageSize) }
                    val at = jump?.let { letter -> async { session.libraryLetterIndex(types, parentId, query, letter) } }
                    page.await() to (at?.await() ?: 0)
                }
                if (!paged.put(gen, 0, first.items, first.total)) return@launch
                grid.pageLoaded(0, firstLoad = true)
                count.text = context.resources.getQuantityString(R.plurals.library_count, first.total, first.total)
                if (first.total == 0) {
                    showMessage(string(if (query.filter == LibraryFilter.ALL) R.string.library_empty else R.string.library_empty_filtered), retry = true)
                    return@launch
                }
                if (jump != null) grid.jumpTo(index.coerceAtMost(first.total - 1))
                if (focusWhenLoaded) focusGridWhenLaidOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val expired = e is InvalidStatusException && e.status == 401
                showMessage(string(if (expired) R.string.error_session else R.string.library_error), retry = !expired)
            }
        }
    }

    /** Fetches [pages] of the current grid; a failed page is asked for again the next time focus nears it. */
    private fun fetch(pages: List<Int>) {
        val gen = paged.generation
        for (page in pages) {
            val job = scope.launch {
                try {
                    val result = session.libraryPage(types, parentId, query, page * paged.pageSize, paged.pageSize)
                    if (paged.put(gen, page, result.items, result.total)) {
                        grid.pageLoaded(page, firstLoad = false)
                        if (grid.hasFocus()) showBackdropOf(grid.lastPosition)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    paged.release(gen, page)
                }
            }
            pageJobs[page] = job
            job.invokeOnCompletion { pageJobs.remove(page, job) }
        }
    }

    /** The A to Z column picked [letter]: the grid is sorted by title first if it was not. */
    private fun jumpToLetter(letter: Char) {
        letters.active = letter
        if (query.sort != LibrarySort.TITLE) {
            query = query.copy(sort = LibrarySort.TITLE)
            paintButtons()
            reload(jump = letter)
            return
        }
        val gen = paged.generation
        jumping?.cancel()
        jumping = scope.launch {
            try {
                val index = session.libraryLetterIndex(types, parentId, query, letter)
                if (gen == paged.generation && paged.total > 0) grid.jumpTo(index.coerceAtMost(paged.total - 1))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The grid stays where it is; picking the letter again retries
                Log.w(TAG, "letter jump failed", e)
            }
        }
    }

    private fun showMessage(text: String, retry: Boolean) {
        message.removeAllViews()
        message.addView(label(context, 16f, Tokens.textSoft, Fonts.medium(context)).apply { this.text = text })
        val button = PillButton(context, string(if (retry) R.string.try_again else R.string.back_to_profiles), primary = true)
        button.setOnClickListener { if (retry) reload() else activity.popTo { it is ProfilesScreen } }
        message.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(12) })
        message.visibility = View.VISIBLE
        if (focusWhenLoaded) {
            focusWhenLoaded = false
            button.requestFocus()
        }
    }

    private fun showBackdropOf(position: Int) {
        val url = paged[position]?.backdropUrl?.let { "$it&maxWidth=1920" }
        if (url == backdropUrl) return
        backdropUrl = url
        main.removeCallbacks(settleBackdrop)
        main.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
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

    /**
     * Coming back to the page: the posters around focus are fetched again so watched marks are
     * current, without moving anything. The rest are dropped and refetched if scrolled to. If the
     * library's size changed meanwhile, the grid starts over.
     */
    private fun refresh() {
        if (message.visibility == View.VISIBLE) return reload()
        if (loading?.isActive == true || paged.total == 0) return
        val gen = paged.generation
        val first = (grid.lastPosition - PosterGrid.PREFETCH).coerceAtLeast(0) / paged.pageSize
        val last = (grid.lastPosition + PosterGrid.PREFETCH).coerceAtMost(paged.total - 1) / paged.pageSize
        paged.keepOnly((first..last).toSet())
        refreshing?.cancel()
        refreshing = scope.launch {
            for (page in first..last) {
                launch {
                    try {
                        val result = session.libraryPage(types, parentId, query, page * paged.pageSize, paged.pageSize)
                        if (gen != paged.generation) return@launch
                        if (result.total != paged.total) return@launch reload()
                        if (paged.put(gen, page, result.items, result.total)) grid.pageLoaded(page, firstLoad = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The posters on screen stay as they were
                    }
                }
            }
        }
    }

    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) refresh()
    }

    override fun contentFocus(): View? = grid.focusTarget()

    /** The first page may still be on its way: focus goes to the grid as soon as it has posters. */
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
        main.removeCallbacks(settleBackdrop)
        stopWaitingForLayout()
        super.destroy()
    }

    private companion object {
        const val TAG = "LibraryScreen"
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
