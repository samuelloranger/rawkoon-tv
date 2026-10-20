package cloud.rawkoon.tv.screens

import android.os.SystemClock
import android.view.View
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonHome
import cloud.rawkoon.tv.rawkoon.RawkoonOutcome
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.hasRawkoonSession
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.cardCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * A profile's home: the backdrop, the header for the focused item, and the rows under it, with the
 * nav rail on the left. Focus moves only touch render properties (glides, ring fades); the header
 * and the backdrop follow once focus rests. The last rows shown are kept on disk, so the page
 * opens at once and refreshes in place.
 */
class HomeScreen(activity: MainActivity, profile: Profile) :
    RowsPage(activity, JellyfinSession(activity, profile), Destination.HOME, headerTop = Tokens.PAGE_HEADER_TOP, rowsTop = Tokens.PAGE_ROWS_TOP) {
    private val cache = File(context.cacheDir, "home-${profile.id}.json")

    /** The rows last handed to [showHome]: what a failed Rawkoon refresh falls back to. */
    private var shown = emptyList<HomeRow>()

    private val opener = TitleOpener(this)
    private val text = ResourceText(context)
    private var loadJob: Job? = null

    /** When the Rawkoon rows last loaded for real ([SystemClock.elapsedRealtime]), 0 before the first time. */
    private var rawkoonLoadedAt = 0L

    /** The token those rows were loaded with: linking another account makes them stale at once. */
    private var rawkoonLoadedWith: String? = null

    init {
        load()
    }

    private fun load() {
        hideMessage()
        // A refresh that is still under way is out of date as soon as another starts
        loadJob?.cancel()
        loadJob = scope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { HomeRow.listFromJson(cache.readText()) }.getOrNull() }
            if (cached != null && !hasRows()) showHome(refreshedDates(cached))
            // Rawkoon is asked at the same time but never waited for: Jellyfin's rows show as soon as they arrive
            val rawkoon = if (rawkoonIsFresh()) null else async { rawkoonOutcome() }
            try {
                val jellyfin = session.homeRows()
                val previous = rawkoonRowsOnScreen(cached)
                showHome(RawkoonHome.merge(jellyfin, previous))
                val outcome = rawkoon?.await()
                if (outcome is RawkoonOutcome.Loaded) {
                    rawkoonLoadedAt = SystemClock.elapsedRealtime()
                    rawkoonLoadedWith = profile().rawkoonToken
                }
                val fresh = RawkoonHome.merge(jellyfin, outcome?.let { RawkoonHome.rowsFor(it, previous) } ?: previous)
                showHome(fresh)
                withContext(Dispatchers.IO) { runCatching { cache.writeText(HomeRow.listToJson(fresh)) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rawkoon?.cancel()
                val expired = e is InvalidStatusException && e.status == 401
                // With rows already on screen from the cache, a failed refresh stays silent
                if (expired || !hasRows()) {
                    val text = string(if (expired) R.string.error_session else R.string.home_error)
                    showMessage(text, if (expired) R.string.back_to_profiles to { activity.popTo { it is ProfilesScreen } } else R.string.try_again to ::load, takeFocus = true)
                }
            }
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
            if (!hasRows()) showMessage(string(R.string.home_empty), R.string.try_again to ::load, takeFocus = true)
            return
        }
        shown = list
        show(list)
    }

    /** A Rawkoon row's card is a TMDB title, which opens its own page, or the library item when it's there. */
    override fun card(item: MediaItem, landscape: Boolean) =
        if (item.tmdb != null) {
            Card(context, item, false, app.images, ::onFocused, { opener.open(it.item, preferLibrary = it.item.tmdb?.inLibrary == true) }, cardCaption(item))
        } else {
            Card(context, item, landscape, app.images, ::onFocused, { openItem(it.item) })
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
        if (!first) load()
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
    }
}
