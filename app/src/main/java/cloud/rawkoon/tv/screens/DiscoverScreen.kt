package cloud.rawkoon.tv.screens

import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Find
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.rawkoon.DiscoverRows
import cloud.rawkoon.tv.rawkoon.RawkoonDiscover
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonText
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.TmdbTitle
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.rawkoon.tmdbBackdrop
import cloud.rawkoon.tv.rawkoon.toItem
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.MessagePanel
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.SearchField
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.cardCaption
import cloud.rawkoon.tv.ui.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Discover, through Rawkoon: rows of TMDB posters (trending, popular, coming soon, a few genres),
 * the header and backdrop of the focused title, and a search field above the rows. A profile
 * without a Rawkoon session, or whose session ended, is asked to link its account instead.
 * Titles Rawkoon already has carry a badge; OK opens the title's page, where it is requested.
 */
class DiscoverScreen(activity: MainActivity, session: JellyfinSession) : RailScreen(activity, session, Destination.DISCOVER) {
    private val text: RawkoonText = ResourceText(context)
    private val header = Header(context, app.images)
    private val rows = Rows(context)
    private val message = MessagePanel(context)
    private val search = SearchField(context, hint = string(R.string.discover_search_hint), onChanged = ::onTyped, onSearch = ::onSearchAction, textSp = 14f, paddingDp = 14)
    private var api: RawkoonDiscover? = null

    /** The token the rows were loaded with: coming back from linking reloads only when it changed. */
    private var loadedWith: String? = null
    private var loading: Job? = null
    private var searchJob: Job? = null
    private var backdropJob: Job? = null

    /**
     * Backdrops fetched for titles already focused, by item id; "" for a title with none or whose
     * fetch failed. The lists carry none, only a title's own page does.
     */
    private val backdrops = LruCache<String, String>(BACKDROP_CACHE)
    private val opener = TitleOpener(this)

    /** The browse rows, kept while a search's results take their place. */
    private var browse = emptyList<Section>()
    private var searching = false

    init {
        content.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(HEADER_TOP)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP - ROWS_LIFT)
        })
        content.addView(search, FrameLayout.LayoutParams(context.dp(SEARCH_W), context.dp(SEARCH_H), Gravity.END).apply {
            topMargin = context.dp(SEARCH_TOP)
            marginEnd = context.dp(SEARCH_END)
        })
        content.addView(message, MessagePanel.params())
        // The rows are clipped to their area, so rows gliding up never pass over the header
        content.clipChildren = true
        load()
    }

    private fun profile() = app.profiles.get(session.profile.id) ?: session.profile

    private fun load() {
        loading?.cancel()
        searchJob?.cancel()
        // Whatever was on screen is rebuilt as browse rows
        searching = false
        search.setText("")
        val profile = profile()
        loadedWith = profile.rawkoonToken
        api = profile.rawkoonDiscover(context)
        val api = api ?: return showNotice(string(R.string.rk_connect_title), string(R.string.rk_connect_hint), string(R.string.rk_connect_button)) { link() }
        loading = scope.launch {
            try {
                val sections = coroutineScope {
                    DiscoverRows.FEATURED.map { (category, title) ->
                        async { optional { api.explore(category).items }?.let { section(category, string(title), it) } }
                    }.awaitAll()
                }.filterNotNull()
                if (sections.isEmpty()) return@launch showNotice(string(R.string.discover_error), null, string(R.string.try_again)) { load() }
                showFirst(sections)
                appendSections(genreSections(api))
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                showNotice(string(R.string.rk_connect_title), string(R.string.rk_reconnect_hint), string(R.string.rk_reconnect_button)) { link() }
            } catch (e: Exception) {
                showNotice(string(R.string.discover_error), null, string(R.string.try_again)) { load() }
            }
        }
    }

    /** A few popular genres of movies and of shows, once the main rows are on screen. */
    private suspend fun genreSections(api: RawkoonDiscover): List<Section> = coroutineScope {
        val movies = async { optional { api.genres(true) }.orEmpty() }
        val shows = async { optional { api.genres(false) }.orEmpty() }
        DiscoverRows.genreRows(movies.await(), shows.await()).map { (isMovie, genre, title) ->
            async { optional { api.discover(isMovie, genre.id).items }?.let { section("genre:$isMovie:${genre.id}", string(title, genre.name), it) } }
        }.awaitAll().filterNotNull()
    }

    private suspend fun <T> optional(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RawkoonException.Unauthorized) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun section(id: String, title: String, titles: List<TmdbTitle>): Section? {
        if (titles.isEmpty()) return null
        return Section(context, id, title).apply {
            row.setCards(titles.map { title ->
                val item = title.toItem(text)
                Card(context, item, false, app.images, ::onFocused, { card -> opener.open(card.item) }, cardCaption(item))
            })
            row.onLeftEdge = { rail.activeItem() }
        }
    }

    /** Puts the first browse rows on screen, in place of the notice if there was one. */
    private fun showFirst(sections: List<Section>) {
        val refocus = message.hasFocus()
        message.hide()
        search.visibility = View.VISIBLE
        browse = sections
        if (!searching) place(sections)
        if (refocus) focusDefault()
        if (!searching) firstItem()?.let(header::show)
    }

    /** Adds [more] after the browse rows (the genre rows, as they arrive), leaving the rows in place as they are. */
    private fun appendSections(more: List<Section>) {
        if (more.isEmpty()) return
        browse = browse + more
        if (!searching) place(browse)
    }

    private fun place(sections: List<Section>) {
        // Rows already in place stay as they are, so focus and glide survive the genre rows arriving
        if (rows.sections() == sections.take(rows.column.childCount) && rows.column.childCount > 0) {
            sections.drop(rows.column.childCount).forEach { rows.column.addView(it) }
            return
        }
        rows.reset()
        rows.column.removeAllViews()
        sections.forEach { rows.column.addView(it) }
    }

    private fun firstItem(): MediaItem? = (rows.sections().firstOrNull()?.row?.cards()?.firstOrNull() as? Card)?.item

    private fun showNotice(title: String, hint: String?, buttonLabel: String, action: () -> Unit) {
        // Nothing of the rows or a search that was on screen is left to act on
        searchJob?.cancel()
        backdropJob?.cancel()
        searching = false
        browse = emptyList()
        rows.column.removeAllViews()
        search.visibility = View.GONE
        message.showNotice(title, hint, buttonLabel to action)
    }

    private fun link() = activity.push(RawkoonLinkScreen(activity, session.profile.id))

    private fun onTyped(typed: CharSequence) {
        if (typed.isBlank()) {
            searchJob?.cancel()
            if (searching) clearSearch()
            return
        }
        // Too short to ask Rawkoon: the rows on screen stay
        Find.term(typed)?.let { runSearch(it, debounce = true) }
    }

    /** The keyboard's search action searches now, without waiting for typing to pause. */
    private fun onSearchAction() {
        if (search.text.isBlank()) return clearSearch()
        Find.term(search.text)?.let { runSearch(it, debounce = false) }
    }

    private fun runSearch(query: String, debounce: Boolean) {
        val api = api ?: return
        // A slow earlier search must not land after a newer one
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounce) {
                delay(SearchField.DEBOUNCE_MS)
                // Typed on since: a term that is now too short is not searched
                if (Find.term(search.text) != query) return@launch
            }
            searching = true
            try {
                val found = api.search(query)
                val results = section("search", string(R.string.discover_results, query), found)
                if (results == null) {
                    message.show(string(R.string.discover_no_results, query))
                    rows.column.removeAllViews()
                } else {
                    message.hide()
                    place(listOf(results))
                    rows.sections().first().row.restoreFocus()
                    firstItem()?.let(header::show)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RawkoonException.Unauthorized) {
                load()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        // Before the text is cleared: clearing fires onTyped, which must see no search to clear
        searching = false
        search.setText("")
        message.hide()
        place(browse)
        firstItem()?.let(header::show)
    }

    private fun onFocused(card: Card) {
        header.show(card.item)
        backdropJob?.cancel()
        val api = api ?: return
        val item = card.item
        val tmdbId = item.tmdb?.id ?: return
        backdropJob = scope.launch {
            delay(Tokens.BACKDROP_SETTLE_MS)
            var url = backdrops.get(item.id)
            if (url == null) {
                url = try {
                    api.modal(item.kind != MediaItem.Kind.SERIES, tmdbId).backdropUrl.let(::tmdbBackdrop).orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ""
                }
                backdrops.put(item.id, url)
            }
            backdrop.show(url.ifEmpty { null })
        }
    }

    override fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || search.visibility != View.VISIBLE) return false
        val first = rows.sections().firstOrNull() ?: return false
        return when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP && first.hasFocus() -> {
                rows.reveal(first)
                search.requestFocus()
                true
            }
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && search.hasFocus() -> first.row.restoreFocus()
            else -> false
        }
    }

    override val isRailPage get() = true

    override fun focusDefault() {
        val notice = message.button?.takeIf { message.visibility == View.VISIBLE }
        (notice ?: search).requestFocus()
    }

    /** Coming back from linking the account: load again with the new session. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first && profile().rawkoonToken != loadedWith) load()
    }

    /** Back on a search's results returns to the browse rows. */
    override fun onBack(): Boolean {
        if (!rail.isOpen && searching) {
            clearSearch()
            search.requestFocus()
            return true
        }
        return super.onBack()
    }

    override fun destroy() {
        backdropJob?.cancel()
        super.destroy()
    }

    private companion object {
        const val SEARCH_W = 270
        const val SEARCH_H = 36
        const val SEARCH_TOP = 20
        const val SEARCH_END = 120
        const val HEADER_TOP = 26

        /** How far the rows start above [Tokens.ROWS_TOP]. */
        const val ROWS_LIFT = 6
        const val BACKDROP_CACHE = 100
    }
}
