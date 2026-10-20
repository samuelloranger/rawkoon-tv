package cloud.rawkoon.tv.screens

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.rawkoon.RawkoonException
import cloud.rawkoon.tv.rawkoon.RawkoonHome
import cloud.rawkoon.tv.rawkoon.RawkoonOutcome
import cloud.rawkoon.tv.rawkoon.ResourceText
import cloud.rawkoon.tv.rawkoon.hasRawkoonSession
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Header
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.RowItem
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.TitleCard
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.shownItem
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
    private val parking = FocusParking(context)
    private var backdropUrl: String? = null

    /** The rows last handed to [show]: what a failed Rawkoon refresh falls back to. */
    private var shown = emptyList<HomeRow>()

    /** The focused item and its card, whose neighbours are prefetched once focus rests. */
    private var prefetchFrom: Pair<MediaItem, View>? = null
    private val settleBackdrop = Runnable {
        backdrop.show(backdropUrl)
        prefetchFrom?.let { (item, card) -> prefetchAround(item, card) }
        prefetchFrom = null
    }
    private val opener = TitleOpener(this)
    private val text = ResourceText(context)
    private var loadJob: Job? = null

    /** When the Rawkoon rows last loaded for real ([SystemClock.elapsedRealtime]), 0 before the first time. */
    private var rawkoonLoadedAt = 0L

    /** The token those rows were loaded with: linking another account makes them stale at once. */
    private var rawkoonLoadedWith: String? = null

    init {
        content.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(26)
        })
        content.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP - 6)
        })
        content.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        content.addView(parking, FrameLayout.LayoutParams(1, 1))
        // The rows are clipped to their area, so rows gliding up never pass over the header
        content.clipChildren = true
        load()
    }

    private fun load() {
        message.visibility = View.GONE
        // A refresh that is still under way is out of date as soon as another starts
        loadJob?.cancel()
        loadJob = scope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { HomeRow.listFromJson(cache.readText()) }.getOrNull() }
            if (cached != null && rows.column.childCount == 0) show(refreshedDates(cached))
            // Rawkoon is asked at the same time but never waited for: Jellyfin's rows show as soon as they arrive
            val rawkoon = if (rawkoonIsFresh()) null else async { rawkoonOutcome() }
            try {
                val jellyfin = session.homeRows()
                val previous = rawkoonRowsOnScreen(cached)
                show(RawkoonHome.merge(jellyfin, previous))
                val outcome = rawkoon?.await()
                if (outcome is RawkoonOutcome.Loaded) {
                    rawkoonLoadedAt = SystemClock.elapsedRealtime()
                    rawkoonLoadedWith = profile().rawkoonToken
                }
                val fresh = RawkoonHome.merge(jellyfin, outcome?.let { RawkoonHome.rowsFor(it, previous) } ?: previous)
                show(fresh)
                withContext(Dispatchers.IO) { runCatching { cache.writeText(HomeRow.listToJson(fresh)) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rawkoon?.cancel()
                val expired = e is InvalidStatusException && e.status == 401
                // With rows already on screen from the cache, a failed refresh stays silent
                if (expired || rows.column.childCount == 0) showMessage(string(if (expired) R.string.error_session else R.string.home_error), retry = !expired)
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
        shown = list
        val current = rows.sections()
        if (current.map { it.id to it.signature } == list.map { it.id to it.signature }) return
        val hadFocus = rows.hasFocus()
        val focusedRow = rows.focusedSection()?.takeIf { hadFocus }?.id
        val focusedKey = (view.findFocus() as? RowItem)?.key
        val keep = current.associateBy { it.id }
        val restore = {
            val section = rows.sections().firstOrNull { it.id == focusedRow } ?: rows.sections().first()
            focusedKey?.let { section.row.remember(it) }
            section.row.restoreFocus()
        }
        parking.around(hadFocus, rebuild = {
            rows.column.removeAllViews()
            for (row in list) rows.column.addView(keep[row.id]?.takeIf { it.signature == row.signature } ?: section(row))
        }, restore = { restore() })
        if (!hadFocus && view.findFocus() == null) restore()
        // Sections moved: re-anchor once the new layout is in
        rows.post { rows.focusedSection()?.let { rows.reveal(it) } }
    }

    private fun section(row: HomeRow) = Section(context, row.id, row.title).apply {
        signature = row.signature
        this.row.setCards(row.items.map { item ->
            // A Rawkoon row's card is a TMDB title, which opens its own page, or the library item when it's there
            if (item.tmdb != null) {
                TitleCard(context, item, app.images, { onFocused(it.item, it) }, { opener.open(it.item, preferLibrary = it.item.tmdb?.inLibrary == true) })
            } else {
                Card(context, item, row.landscape, app.images, { onFocused(it.item, it) }, { openItem(it.item) })
            }
        })
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

    private fun onFocused(item: MediaItem, card: View) {
        header.show(item)
        backdropUrl = item.backdropUrl?.let { "$it&maxWidth=1920" }
        main.removeCallbacks(settleBackdrop)
        main.postDelayed(settleBackdrop, Tokens.BACKDROP_SETTLE_MS)
        prefetchFrom = item to card
    }

    /** Fetches, behind visible loads, the logos and backdrops of the cards next to [card], so they are ready when focus gets there. */
    private fun prefetchAround(item: MediaItem, card: View) {
        val strip = card.parent as? ViewGroup ?: return
        val index = strip.indexOfChild(card)
        for (i in (index - 1)..(index + 2)) {
            val near = strip.getChildAt(i)?.shownItem() ?: continue
            if (near === item) continue
            header.prefetch(near)
            near.backdropUrl?.let { app.images.warm("$it&maxWidth=1920") }
        }
    }

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

    override fun destroy() {
        main.removeCallbacks(settleBackdrop)
        super.destroy()
    }

    private companion object {
        /** The most a home refresh waits for Rawkoon (its requests row looks up each approved request's state). */
        const val RAWKOON_TIMEOUT_MS = 8_000L

        /** How long loaded Rawkoon rows are reused before a refresh asks again. */
        const val RAWKOON_FRESH_MS = 60_000L
    }
}
