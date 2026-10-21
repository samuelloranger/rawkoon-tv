package cloud.rawkoon.tv.screens

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.arrangeRows
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.jellyfin.playsRightAway
import cloud.rawkoon.tv.jellyfin.isSessionEnded
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonHome
import cloud.rawkoon.tv.rawkoon.RawkoonOutcome
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.hasRawkoonSession
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.rawkoon.tmdbBackdrop
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.cardCaption
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A profile's home: the backdrop, the header for the focused item, and the rows under it, with the
 * nav rail on the left. Focus moves only touch render properties (glides, ring fades); the header
 * and the backdrop follow once focus rests. The last rows shown are kept on disk, so the page
 * opens at once and refreshes in place.
 */
class HomeScreen(activity: MainActivity, profile: Profile) :
    RowsPage(activity, JellyfinSession(activity, profile), Destination.HOME, headerTop = Tokens.PAGE_HEADER_TOP, rowsTop = Tokens.PAGE_ROWS_TOP) {
    private val cache = homeCacheFile(context, profile.id)

    /**
     * The rows last handed to [showHome], before the user's arrangement hides or moves any: what a
     * failed Rawkoon refresh falls back to, and what is saved for the home rows setting to list.
     */
    private var shown = emptyList<HomeRow>()

    private val opener = TitleOpener(this)
    private val text = ResourceText(context)
    private var loadJob: Job? = null
    private var rawkoonJob: Job? = null
    private var backdropJob: Job? = null

    /**
     * Backdrops looked up for TMDB cards focused before, by item id; "" for a title with none or
     * whose lookup failed. Rawkoon's request list carries no backdrop, only a title's own page does.
     */
    private val backdrops = LruCache<String, String>(BACKDROP_CACHE)

    /** When the Rawkoon rows last loaded for real ([SystemClock.elapsedRealtime]), 0 before the first time. */
    private var rawkoonLoadedAt = 0L

    /** The token those rows were loaded with: linking another account makes them stale at once. */
    private var rawkoonLoadedWith: String? = null

    init {
        backdrop.drifts = true
        load()
    }

    private fun load() = refresh(forceRawkoon = false)

    /** The full home refresh; [forceRawkoon]: the server just said something about the requests, so the minute's reuse of the Rawkoon rows doesn't apply. */
    private fun refresh(forceRawkoon: Boolean) {
        hideMessage()
        liveMissed = false
        missedRawkoon = false
        // A refresh that is still under way is out of date as soon as another starts
        loadJob?.cancel()
        rawkoonJob?.cancel()
        loadJob = scope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { HomeRow.listFromJson(cache.readText()) }.getOrNull() }
            if (cached != null && !hasRows()) showHome(refreshedDates(cached))
            // Rawkoon is asked at the same time but never waited for: Jellyfin's rows show as soon as they arrive
            val rawkoon = if (!forceRawkoon && rawkoonIsFresh()) null else async { rawkoonOutcome() }
            try {
                app.awaitStop()
                val jellyfin = session.homeRows(Prefs.homeFetch(context))
                app.launcher.syncAfterHome(profile(), jellyfin)
                val previous = rawkoonRowsOnScreen(cached)
                showHome(RawkoonHome.merge(jellyfin, previous))
                val outcome = rawkoon?.await()
                if (outcome is RawkoonOutcome.Loaded) {
                    rawkoonLoadedAt = SystemClock.elapsedRealtime()
                    rawkoonLoadedWith = profile().rawkoonToken
                }
                val fresh = RawkoonHome.merge(jellyfin, outcome?.let { RawkoonHome.rowsFor(it, previous) } ?: previous)
                showHome(fresh)
                saveCache(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rawkoon?.cancel()
                val expired = e.isSessionEnded()
                // With rows already on screen from the cache, a failed refresh stays silent
                if (expired || !hasRows()) {
                    val text = string(if (expired) R.string.error_session else R.string.home_error)
                    showMessage(text, messageAction(if (expired) null else ::load), takeFocus = true)
                }
            }
        }
    }

    /** Written aside then swapped in, so the home rows setting never reads half a file. */
    private suspend fun saveCache(rows: List<HomeRow>) = withContext(Dispatchers.IO) {
        runCatching {
            val partial = File(cache.path + ".tmp")
            partial.writeText(HomeRow.listToJson(rows))
            partial.renameTo(cache)
        }
    }

    /** Rows kept from before with their dates worded for today (a cached "Tomorrow" may be a day old). */
    private fun refreshedDates(list: List<HomeRow>) = RawkoonHome.refreshed(list, text, LocalDate.now(), Locale.getDefault())

    /** Coming back to home within a minute reuses the Rawkoon rows on screen instead of asking again. */
    private fun rawkoonIsFresh() = rawkoonLoadedAt != 0L && rawkoonLoadedWith == profile().rawkoonToken &&
        SystemClock.elapsedRealtime() - rawkoonLoadedAt < RAWKOON_FRESH_MS

    /** The Rawkoon rows to show until a refresh answers: those on screen, else the cached ones; none without a session. */
    private fun rawkoonRowsOnScreen(cached: List<HomeRow>?): List<HomeRow> {
        if (!profile().hasRawkoonSession(context)) return emptyList()
        return refreshedDates((shown.ifEmpty { cached.orEmpty() }).filter(RawkoonHome::isRawkoonRow))
    }

    /** Read again each time: linking the account from Discover changes the saved profile, not this screen's copy. */
    private fun profile() = app.profiles.get(session.profile.id) ?: session.profile

    /** Coming soon and My requests, capped so a slow Rawkoon can't hold the home refresh. */
    private suspend fun rawkoonOutcome(): RawkoonOutcome {
        val profile = profile()
        val api = profile.rawkoonDiscover(context) ?: return RawkoonOutcome.NoSession
        return try {
            withTimeoutOrNull(RAWKOON_TIMEOUT_MS) {
                val userId = suspend {
                    profile.rawkoonUserId ?: api.userId().ifBlank { null }?.also { app.profiles.save(profile.copy(rawkoonUserId = it)) }
                }
                RawkoonOutcome.Loaded(RawkoonHome.load(api, userId, text, LocalDate.now(), Instant.now(), Locale.getDefault()))
            } ?: RawkoonOutcome.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: RawkoonException.Unauthorized) {
            // Home never nags about it: Discover is where the account is linked again
            RawkoonOutcome.Refused
        } catch (e: Exception) {
            RawkoonOutcome.Unavailable
        }
    }

    /** Shows [list] as home's rows; an empty answer leaves the rows already there and only fills an empty page. */
    private fun showHome(list: List<HomeRow>) {
        if (list.isEmpty()) {
            if (!hasRows()) showMessage(string(R.string.home_empty), messageAction(::load), takeFocus = true)
            return
        }
        shown = list
        val visible = arrangeRows(list, Prefs.homeOrder(context, session.profile.id), Prefs.homeHidden(context, session.profile.id))
        if (visible.isEmpty()) showEmpty(string(R.string.home_all_hidden)) else show(visible)
    }

    /** A Rawkoon row's card is a TMDB title, which opens its own page, or the library item when it's there. */
    override fun card(item: MediaItem, landscape: Boolean, rowId: String) =
        if (item.tmdb != null) {
            Card(context, item, false, app.images, ::onFocused, { opener.open(it.item, preferLibrary = it.item.tmdb?.inLibrary == true) }, cardCaption(item))
        } else {
            Card(context, item, landscape, app.images, ::onFocused, { openFromRow(it.item, rowId) })
        }

    /** Opens a library card, or plays it at once when the setting asks for it in this row. */
    private fun openFromRow(item: MediaItem, rowId: String) {
        if (playsRightAway(Prefs.continueOpen(context), rowId, item)) activity.push(PlayerScreen(activity, session, item)) else openItem(item)
    }

    /** A TMDB card without a backdrop (a request) looks its title's up once focus rests, so it doesn't cost a call per card. */
    override fun onFocused(card: Card) {
        super.onFocused(card)
        backdropJob?.cancel()
        val item = card.item
        val tmdbId = item.tmdb?.id ?: return
        if (item.backdropUrl != null) return
        backdrops.get(item.id)?.let { return useBackdrop(it.ifEmpty { null }, now = false) }
        val api = profile().rawkoonDiscover(context) ?: return
        backdropJob = scope.launch {
            delay(Tokens.BACKDROP_SETTLE_MS)
            val url = try {
                api.modal(item.kind != MediaItem.Kind.SERIES, tmdbId).backdropUrl.let(::tmdbBackdrop).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed lookup is not "no backdrop": leave it uncached so the next focus asks again
                return@launch useBackdrop(null, now = true)
            }
            backdrops.put(item.id, url)
            // Only the card still focused takes it: focus may have moved on or the rows been rebuilt
            if (rows.hasFocus() && (rows.findFocus() as? Card)?.item?.id == item.id) useBackdrop(url.ifEmpty { null }, now = true)
        }
    }

    override fun onRowsReplaced() {
        backdropJob?.cancel()
    }

    override fun destroy() {
        backdropJob?.cancel()
        rawkoonJob?.cancel()
        super.destroy()
    }

    /** Every key holds the backdrop's zoom, so it never runs during a glide. */
    override fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) backdrop.holdDrift()
        return false
    }

    /** Home keeps the header where it was when rows change under the rail. */
    override val previewsFirstItem get() = false

    override fun contentFocus(): View? = rows.focusedSection()?.row?.remembered()

    override fun focusDefault() {
        rows.sections().firstOrNull()?.row?.restoreFocus()
    }

    /** Coming back to home (from a page or the player): refresh in place, keeping focus. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) {
            // Row settings changed meanwhile show at once, from the rows already loaded
            if (shown.isNotEmpty()) showHome(shown)
            load()
        }
    }

    override val livePage get() = LivePage.HOME

    /**
     * Watched state, progress or new items changed on Jellyfin: the same in-place refresh as coming
     * back. Rawkoon news alone (a title landed, a request changed) asks Rawkoon again and leaves
     * Jellyfin's rows as they are. Restarting a running refresh on every event could keep a slow
     * Home from ever finishing, so an event during one is kept and answered once it is done.
     */
    override fun onLive(kinds: Set<LiveKind>) {
        val rawkoon = kinds.any { it.fromRawkoon }
        if (loadJob?.isActive == true) {
            liveMissed = true
            missedRawkoon = missedRawkoon || rawkoon
            loadJob?.invokeOnCompletion { cause -> if (cause == null) mainHandler.post { if (liveMissed) refresh(forceRawkoon = missedRawkoon) } }
        } else if (kinds.any { !it.fromRawkoon } || shown.isEmpty()) {
            refresh(forceRawkoon = rawkoon)
        } else {
            refreshRawkoon()
        }
    }

    private var liveMissed = false
    private var missedRawkoon = false

    /** Coming soon and My requests again, merged into the rows on screen. */
    private fun refreshRawkoon() {
        rawkoonJob?.cancel()
        rawkoonJob = scope.launch {
            val outcome = rawkoonOutcome()
            if (outcome is RawkoonOutcome.Loaded) {
                rawkoonLoadedAt = SystemClock.elapsedRealtime()
                rawkoonLoadedWith = profile().rawkoonToken
            }
            val jellyfin = shown.filterNot(RawkoonHome::isRawkoonRow)
            val fresh = RawkoonHome.merge(jellyfin, RawkoonHome.rowsFor(outcome, shown.filter(RawkoonHome::isRawkoonRow)))
            showHome(fresh)
            saveCache(fresh)
        }
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

    private companion object {
        /** The most a home refresh waits for Rawkoon (its requests row looks up each approved request's state). */
        const val RAWKOON_TIMEOUT_MS = 8_000L

        /** How long loaded Rawkoon rows are reused before a refresh asks again. */
        const val RAWKOON_FRESH_MS = 60_000L
        const val BACKDROP_CACHE = 100
    }
}

/** Where a profile's last home rows are kept: the home reads them first, and the home rows setting lists them. */
fun homeCacheFile(context: Context, profileId: String) = File(context.cacheDir, "home-$profileId.json")
