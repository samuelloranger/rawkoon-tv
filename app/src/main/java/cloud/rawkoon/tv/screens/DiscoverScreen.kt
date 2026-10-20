package cloud.rawkoon.tv.screens

import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.rawkoon.RawkoonDiscover
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonText
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.TmdbTitle
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.rawkoon.toItem
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.TitleCard
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.rounded
import cloud.rawkoon.tv.ui.shownItem
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
    private val message = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
    }
    private val search = EditText(context).apply {
        hint = context.getString(R.string.discover_search_hint)
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        textSize = 14f
        typeface = Fonts.medium(context)
        setTextColor(Tokens.textPrimary)
        setHintTextColor(Tokens.textMuted)
        isSingleLine = true
        background = rounded(Tokens.surfaceHigh, context.dpf(10f))
        setPadding(context.dp(14), 0, context.dp(14), 0)
        setOnFocusChangeListener { v, focused ->
            v.background = rounded(if (focused) Tokens.surfaceFocused else Tokens.surfaceHigh, context.dpf(10f)).apply {
                if (focused) setStroke(context.dp(2), Tokens.ring)
            }
        }
    }
    private var api: RawkoonDiscover? = null

    /** The token the rows were loaded with: coming back from linking reloads only when it changed. */
    private var loadedWith: String? = null
    private var loading: Job? = null
    private var searchJob: Job? = null
    private var backdropJob: Job? = null

    /** Backdrops fetched for titles already focused, by item id. The lists carry none, only a title's own page does. */
    private val backdrops = HashMap<String, String?>()

    /** The browse rows, kept while a search's results take their place. */
    private var browse = emptyList<Section>()
    private var searching = false

    /** The button of the notice (link the account, retry), while one is shown. */
    private var noticeButton: PillButton? = null

    init {
        content.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(26)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP - 6)
        })
        content.addView(search, FrameLayout.LayoutParams(context.dp(SEARCH_W), context.dp(SEARCH_H), Gravity.END).apply {
            topMargin = context.dp(20)
            marginEnd = context.dp(120)
        })
        content.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        // The rows are clipped to their area, so rows gliding up never pass over the header
        content.clipChildren = true
        search.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }
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
                    FEATURED.map { (category, title) ->
                        async { optional { api.explore(category).items }?.let { section(category, string(title), it) } }
                    }.awaitAll()
                }.filterNotNull()
                if (sections.isEmpty()) return@launch showNotice(string(R.string.discover_error), null, string(R.string.try_again)) { load() }
                showBrowse(sections)
                showBrowse(sections + genreSections(api))
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
        val wanted = movies.await().filter { it.id in MOVIE_GENRES }.map { Triple(true, it, R.string.discover_genre_movies) } +
            shows.await().filter { it.id in SHOW_GENRES }.map { Triple(false, it, R.string.discover_genre_shows) }
        wanted.map { (isMovie, genre, title) ->
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
            row.setCards(titles.map { TitleCard(context, it.toItem(text), app.images, ::onFocused) { card -> openTitle(card.item) } })
            row.onLeftEdge = { rail.activeItem() }
        }
    }

    /** Puts the browse rows on screen (again, with the genre rows added, as they arrive). */
    private fun showBrowse(sections: List<Section>) {
        val refocus = message.hasFocus()
        message.visibility = View.GONE
        search.visibility = View.VISIBLE
        browse = sections
        if (!searching) place(sections)
        if (refocus) focusDefault()
        if (!searching) firstItem()?.let(header::show)
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

    private fun firstItem(): MediaItem? = rows.sections().firstOrNull()?.row?.cards()?.firstOrNull()?.shownItem()

    private fun showNotice(title: String, hint: String?, buttonLabel: String, action: () -> Unit) {
        rows.column.removeAllViews()
        search.visibility = View.GONE
        message.removeAllViews()
        message.addView(label(context, 20f, Tokens.textPrimary, Fonts.title(context)).apply { text = title })
        hint?.let { message.addView(label(context, 15f, Tokens.textSoft, Fonts.medium(context)).apply { text = it }, spaced(8)) }
        val button = PillButton(context, buttonLabel, primary = true).apply { setOnClickListener { action() } }
        message.addView(button, spaced(16))
        noticeButton = button
        message.visibility = View.VISIBLE
        button.requestFocus()
    }

    private fun spaced(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }

    private fun link() = activity.push(RawkoonLinkScreen(activity, session.profile.id))

    private fun runSearch() {
        val query = search.text.toString().trim()
        val api = api ?: return
        if (query.isEmpty()) return clearSearch()
        // Rawkoon answers nothing for fewer than two letters
        if (query.length < 2) return
        searching = true
        // A slow earlier search must not land after a newer one
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                val found = api.search(query)
                val results = section("search", string(R.string.discover_results, query), found)
                if (results == null) {
                    message.removeAllViews()
                    message.addView(label(context, 16f, Tokens.textSoft, Fonts.medium(context)).apply { text = string(R.string.discover_no_results, query) })
                    message.visibility = View.VISIBLE
                    rows.column.removeAllViews()
                } else {
                    message.visibility = View.GONE
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
        search.setText("")
        searching = false
        message.visibility = View.GONE
        place(browse)
        firstItem()?.let(header::show)
    }

    private fun onFocused(card: TitleCard) {
        header.show(card.item)
        backdropJob?.cancel()
        val api = api ?: return
        val item = card.item
        val tmdbId = item.tmdbId ?: return
        backdropJob = scope.launch {
            delay(Tokens.BACKDROP_SETTLE_MS)
            if (item.id !in backdrops) {
                backdrops[item.id] = try {
                    api.modal(item.kind != MediaItem.Kind.SERIES, tmdbId).backdropUrl
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
            }
            backdrop.show(backdrops[item.id])
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

    override fun focusDefault() {
        val notice = noticeButton?.takeIf { it.parent === message && message.visibility == View.VISIBLE }
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

        /** Rawkoon explore category (also the row's id) and the row's title. */
        val FEATURED = listOf(
            "trending" to R.string.discover_trending,
            "popular_movies" to R.string.discover_popular_movies,
            "popular_shows" to R.string.discover_popular_shows,
            "upcoming_movies" to R.string.discover_upcoming_movies,
            "on_the_air" to R.string.discover_on_the_air,
        )

        /** TMDB's own genre ids: action, comedy, science fiction, horror. */
        val MOVIE_GENRES = setOf(28, 35, 878, 27)

        /** TMDB's own genre ids: action & adventure, comedy, sci-fi & fantasy, crime. */
        val SHOW_GENRES = setOf(10759, 35, 10765, 80)
    }
}
