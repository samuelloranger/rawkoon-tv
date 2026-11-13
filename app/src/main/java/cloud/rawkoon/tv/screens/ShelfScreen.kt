package cloud.rawkoon.tv.screens

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.itemsByIds
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.rawkoon.ResolvedShelfEntry
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.ShelfCard
import cloud.rawkoon.tv.rawkoon.ShelfDetail
import cloud.rawkoon.tv.rawkoon.ShelfOrder
import cloud.rawkoon.tv.rawkoon.ShelfRows
import cloud.rawkoon.tv.rawkoon.ShelfSummary
import cloud.rawkoon.tv.rawkoon.shelfSource
import cloud.rawkoon.tv.rawkoon.tmdbImage
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.MessagePanel
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.arrive
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One shelf: its name and description, then its titles in display order, one row per group header
 * and numbered when the order is by hand. A title the library has opens like any library card; one
 * it lacks stays in its place, dimmed and marked Request, and opens the page where it can be asked
 * for. Back returns to the Shelves page's cover card.
 */
class ShelfScreen(
    activity: MainActivity,
    session: JellyfinSession,
    private val shelfId: Int,
    private val start: ShelfSummary? = null,
) : RailScreen(activity, session, Destination.SHELVES) {
    private val name = label(context, 32f, Tokens.textPrimary, Fonts.title(context))
    private val description = label(context, 13f, Tokens.textBody, Fonts.body(context), lines = 2).apply { setLineSpacing(0f, 1.2f) }
    private val rows = Rows(context)
    private val message = MessagePanel(context)
    private val text = ResourceText(context)
    private var loadJob: Job? = null
    private var marksJob: Job? = null

    /** The shelf as last shown: what a refresh keeps on screen until its answer lands. */
    private var detail: ShelfDetail? = null

    init {
        backdrop.frameFraction = Tokens.FRAME_W_GRID
        backdrop.strength = 0.5f
        content.addView(name, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(NAME_TOP)
        })
        content.addView(description, FrameLayout.LayoutParams(context.dp(DESCRIPTION_W), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(DESCRIPTION_TOP)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(ROWS_TOP)
        })
        content.addView(message, MessagePanel.params())
        content.clipChildren = true
        start?.let {
            name.text = it.name
            description.text = it.description.orEmpty()
            description.visibility = if (it.description.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        marksJob?.cancel()
        loadJob = scope.launch {
            val profile = app.profiles.get(session.profile.id) ?: session.profile
            val source = profile.shelfSource(context)
            if (source == null) {
                showEmpty(string(R.string.rk_shelf_empty), retry = false)
                return@launch
            }
            val loaded = try {
                source.shelf(shelfId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showEmpty(string(R.string.rk_shelf_error), retry = true)
                return@launch
            }
            if (loaded == null) {
                showEmpty(string(R.string.rk_shelf_error), retry = true)
                return@launch
            }
            // No index and no saved one is a failure to show, not a shelf nobody can watch
            val index = app.tmdbIndex.index(session)
            val cards = try {
                val items = session.itemsByIds(ShelfRows.libraryIds(loaded.entries, index))
                withContext(Dispatchers.Default) { ShelfRows.resolve(loaded.entries, index, items, text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (cards == null) {
                showEmpty(string(R.string.rk_shelf_error), retry = true)
                return@launch
            }
            show(loaded, cards)
        }
    }

    private fun show(loaded: ShelfDetail, cards: List<ResolvedShelfEntry>) {
        detail = loaded
        name.text = loaded.summary.name
        description.text = loaded.summary.description.orEmpty()
        description.visibility = if (loaded.summary.description.isNullOrBlank()) View.GONE else View.VISIBLE
        if (cards.isEmpty()) {
            showEmpty(string(R.string.rk_shelf_empty), retry = false)
            return
        }
        message.hide()
        val numbered = loaded.summary.order == ShelfOrder.MANUAL
        val numbers = cards.withIndex().associate { (i, resolved) -> resolved.entry.id to i + 1 }
        val sections = ShelfRows.groups(cards).mapIndexed { i, (label, entries) ->
            Section(context, "${loaded.summary.id}-$i", label).apply {
                // Groups read as separate bands: more air under a row than the default
                setPadding(0, 0, 0, context.dp(SECTION_GAP))
                signature = entries.joinToString("|") { it.entry.id.toString() }
                row.setCards(entries.map { card(it, numbers.getValue(it.entry.id), numbered) })
                row.onLeftEdge = { rail.activeItem() }
            }
        }
        val focusedRow = rows.focusedSection()?.id
        val focusedKey = (view.findFocus() as? Card)?.key
        rows.column.removeAllViews()
        sections.forEach { rows.column.addView(it) }
        val section = sections.firstOrNull { it.id == focusedRow } ?: sections.first()
        focusedKey?.let(section.row::remember)
        if (view.findFocus() == null) section.row.restoreFocus()
        if (sections.isNotEmpty()) sections.arrive()
    }

    /** One entry's card: a library title, or a dimmed one with the Request badge. */
    private fun card(resolved: ResolvedShelfEntry, number: Int, numbered: Boolean): Card = when (val shelfCard = resolved.card) {
        is ShelfCard.Library -> {
            val caption = caption(shelfCard.item, shelfCard.season, number, numbered)
            Card(context, shelfCard.item, false, app.images, ::preview, { openLibrary(shelfCard) }, caption).withMenu()
        }
        is ShelfCard.Requestable -> {
            val caption = caption(shelfCard.item, resolved.entry.season, number, numbered)
            Card(context, shelfCard.item, false, app.images, ::preview, { openMissing(shelfCard.item) }, caption, dim = true).withMenu()
        }
    }

    /** "12. Title" and the year, a season's number, or nothing when the order needs no numbering. */
    private fun caption(item: MediaItem, season: Int?, number: Int, numbered: Boolean): Pair<String, String?>? {
        val title = if (numbered) "$number. ${item.title}" else item.title
        val subtitle = season?.let(text::season) ?: item.year?.toString()
        if (!numbered && season == null && item.tmdb == null) return null
        return title to subtitle
    }

    /** The focused title's backdrop, once focus rests. */
    private fun preview(card: Card) {
        val item = card.item
        // A title without a backdrop clears the last one: leaving it would show another title's art
        val request = backdropRequest(item.backdropUrl?.let { if (item.tmdb != null) tmdbImage(it) else it })
        mainHandler.removeCallbacks(settleBackdrop)
        backdropUrl = request
        mainHandler.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
    }

    private var backdropUrl: String? = null
    private val settleBackdrop = Runnable { backdrop.show(backdropUrl) }

    /** A library title opens its page; a season placed on its own opens that season. */
    private fun openLibrary(card: ShelfCard.Library) {
        val season = card.season ?: return openItem(card.item)
        scope.launch {
            val seasons = try {
                session.seasons(card.item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch openItem(card.item)
            }
            val found = seasons.firstOrNull { it.seasonNumber == season }
            if (found == null) openItem(card.item) else activity.push(SeasonScreen(activity, session, card.item.id, found.id, null))
        }
    }

    /** A title the library lacks opens the Discover page, where it can be requested. */
    private fun openMissing(item: MediaItem) = activity.push(DiscoverDetailsScreen(activity, session, item))

    /** Replaces the page with [text]; a [retry] offers to read the shelf again. */
    private fun showEmpty(text: String, retry: Boolean) {
        rows.column.removeAllViews()
        message.show(text, messageAction(if (retry) ::load else null), takeFocus = view.findFocus() == null)
    }

    override fun contentFocus(): View? = super.contentFocus() ?: rows.focusedSection()?.row?.remembered()

    override fun focusDefault() {
        rows.sections().firstOrNull()?.row?.restoreFocus()
    }

    override val livePage get() = LivePage.LIBRARY

    /**
     * The library changed: the shelf and its cards are read again. Watched state, progress or a
     * favorite changed: only the library cards' marks are, in place, so focus and scroll stay put.
     */
    override fun onLive(kinds: Set<LiveKind>) {
        if (LiveKind.LIBRARY in kinds) load() else refreshMarks()
    }

    private fun refreshMarks() {
        marksJob?.cancel()
        marksJob = scope.launch {
            val cards = rows.sections().flatMap { it.row.cards() }.filterIsInstance<Card>().filter { it.item.tmdb == null }
            if (cards.isEmpty()) return@launch
            val fresh = try {
                session.itemsByIds(cards.map { it.item.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            cards.forEach { card -> fresh[card.item.id]?.let(card::update) }
        }
    }

    /**
     * The card menu added the title to a shelf or took it off one (possibly this one): read the shelf
     * again so the page agrees with the server instead of showing a card that is no longer on it.
     */
    override fun onCardChanged(rawkoon: Boolean) {
        if (rawkoon) load()
    }

    /** A page above Shelves: the rail highlights Shelves, and another destination must replace both. */
    override val isRailPage get() = true

    /**
     * Shelves sits under this page, so leaving for another rail page drops back to it first and
     * lets the replace take its place; replacing only this page would leave Shelves under the new one.
     */
    override fun navigate(destination: Destination) {
        when (destination) {
            Destination.SHELVES -> activity.popTo { it is ShelvesScreen }
            Destination.SEARCH, Destination.FAVORITES, Destination.DISCOVER, Destination.MOVIES, Destination.SHOWS, Destination.COLLECTIONS -> {
                activity.popTo { it is ShelvesScreen }
                super.navigate(destination)
            }
            else -> super.navigate(destination)
        }
    }

    /** Coming back to the page reads the shelf again, keeping what is on screen meanwhile. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first && detail != null) load()
    }

    override fun destroy() {
        loadJob?.cancel()
        marksJob?.cancel()
        super.destroy()
    }

    private companion object {
        const val NAME_TOP = 26
        const val DESCRIPTION_TOP = 68
        const val DESCRIPTION_W = 620
        const val ROWS_TOP = 108

        /** Room between a group's row and the next group's title. */
        const val SECTION_GAP = 24
    }
}
