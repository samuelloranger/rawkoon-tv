package cloud.rawkoon.tv.screens

import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.FocusRow
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.TabsRow
import cloud.rawkoon.tv.ui.TitleLogo
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.chipRow
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.label
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

/**
 * A show's seasons and episodes: tabs for the seasons, the focused episode described at the top,
 * the episodes in a row and the actions under them. Everything fits one screen, so nothing scrolls
 * but the row.
 */
class SeasonScreen(
    activity: MainActivity,
    session: JellyfinSession,
    private val seriesId: String,
    private val startSeason: String?,
    private val startEpisode: String?,
) : RailScreen(activity, session, Destination.SHOWS) {
    private val tabs = TabsRow(context)
    private val showName = TitleLogo(context, app.images, context.dp(240), context.dp(44), 26f, 16) { alive }
    private val number = label(context, 17f, Tokens.accent, Fonts.title(context))
    private val episodeTitle = label(context, 26f, Tokens.textPrimary, Fonts.title(context))
    private val chips = FrameLayout(context)
    private val overview = label(context, 14f, Tokens.textBody, Fonts.body(context), lines = 2).apply { setLineSpacing(0f, 1.2f) }
    private val episodes = FocusRow(context)
    private val actions = ActionBar(this)
    private val parking = FocusParking(context)
    private var seasons: List<MediaItem> = emptyList()
    private var shownSeason: String? = null
    private var loadJob: Job? = null
    private var pendingTab: MediaItem? = null
    private val settleTab = Runnable { pendingTab?.let { select(it, null, focus = false) } }
    private var pendingEpisode: MediaItem? = null
    private val settleEpisode = Runnable { describePending() }
    private val timeFormat by lazy { DateFormat.getTimeFormat(context) }
    private val endTime = Date()

    init {
        val start = Tokens.CONTENT_START
        content.addView(tabs, at(start - Tokens.RING_INSET, 18, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(showName, at(start, 58, context.dp(520), context.dp(48)))
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(number, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(8) })
            addView(episodeTitle, LinearLayout.LayoutParams(context.dp(560), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(titleRow, at(start, 112, ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(34)))
        content.addView(chips, at(start, 152, context.dp(700), context.dp(22)))
        content.addView(overview, at(start, 180, context.dp(575), context.dp(42)))
        content.addView(episodes, at(0, 228, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(actions.view, at(start - Tokens.RING_INSET, 412, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        actions.beforeUse = ::describePending
        episodes.onLeftEdge = { rail.activeItem() }
        content.addView(parking, FrameLayout.LayoutParams(1, 1))
        load()
    }

    private fun at(left: Int, top: Int, w: Int, h: Int) = FrameLayout.LayoutParams(w, h).apply {
        leftMargin = context.dp(left)
        topMargin = context.dp(top)
    }

    private fun load() {
        launchLoading {
            val details = async { session.details(seriesId) }
            seasons = session.seasons(seriesId)
            // A tab only switches the season if focus stays on it; leaving it cancels the switch
            tabs.set(seasons, onLeave = { mainHandler.removeCallbacks(settleTab); mainHandler.post(::catchUpLive) }) { season, focused ->
                mainHandler.removeCallbacks(settleTab)
                if (focused) {
                    // Moving along the tabs only switches once focus rests on one
                    pendingTab = season
                    mainHandler.postDelayed(settleTab, TAB_SETTLE_MS)
                } else {
                    select(season, null, focus = true)
                }
            }
            val first = seasons.firstOrNull { it.id == startSeason } ?: seasons.firstOrNull()
            if (first != null) select(first, startEpisode, focus = true)
            val show = details.await().item
            backdrop.show(backdropRequest(show.backdropUrl))
            showName.bind(show.logoUrl, show.title)
        }
    }

    /**
     * Shows [season]'s episodes, focusing [episodeId] (or the first unwatched one) when [focus] is
     * set. [quiet] for a refresh nobody asked for: a failure leaves the page as it was, with no toast.
     */
    private fun select(season: MediaItem, episodeId: String?, focus: Boolean, quiet: Boolean = false) {
        tabs.select(season.id)
        if (season.id == shownSeason) {
            if (focus) episodes.restoreFocus()
            return
        }
        shownSeason = season.id
        loadJob?.cancel()
        val job = launchLoading(onError = { if (!quiet) toastPageError() }) {
            try {
                app.awaitStop()
                val list = session.episodes(seriesId, season.id)
                val refocus = focus && episodes.hasFocus()
                parking.isFocusable = refocus
                if (refocus) parking.requestFocus()
                episodes.setCards(
                    list.map { e ->
                        val name = listOfNotNull(episodeTag(e), e.title).joinToString(" · ")
                        Card(context, e, true, app.images, { showEpisode(it.item) }, { describePending(); actions.primary.performClick() }, caption = name to e.runtimeMinutes?.let { MediaItem.duration(it) })
                    },
                )
                val target = list.firstOrNull { it.id == episodeId } ?: list.firstOrNull { !it.played } ?: list.firstOrNull() ?: return@launchLoading
                episodes.remember(target.id)
                showEpisode(target, settle = false)
                if (focus) episodes.restoreFocus()
                // Glide to the remembered card once the row is laid out
                episodes.post { episodes.remembered()?.let { episodes.reveal(it) } }
            } finally {
                parking.isFocusable = false
            }
        }
        loadJob = job
        job.invokeOnCompletion { cause -> if (cause == null) mainHandler.post(::catchUpLive) }
    }

    private fun episodeTag(e: MediaItem) = e.episodeNumber?.let { "E$it" }

    /**
     * The number and title follow focus at once; the rest (chips, overview, actions) waits until
     * focus rests on the episode, so moving along the row doesn't rebuild them for each card.
     */
    private fun showEpisode(episode: MediaItem, settle: Boolean = true) {
        number.text = episodeTag(episode).orEmpty()
        episodeTitle.text = episode.title
        pendingEpisode = episode
        mainHandler.removeCallbacks(settleEpisode)
        if (settle) mainHandler.postDelayed(settleEpisode, Tokens.HEADER_SETTLE_MS) else describePending()
    }

    private fun describePending() {
        mainHandler.removeCallbacks(settleEpisode)
        val episode = pendingEpisode ?: return
        pendingEpisode = null
        overview.text = episode.overview
        chips.removeAllViews()
        chips.addView(chipRow(context, episodeChips(episode)))
        actions.bind(episode)
    }

    private fun episodeChips(e: MediaItem): List<Chip> = buildList {
        add(Chip.Kind(string(R.string.kind_episode)))
        e.episodeLabel?.let { add(Chip.Text(it)) }
        e.runtimeMinutes?.takeIf { it > 0 }?.let { add(Chip.Text(MediaItem.duration(it))) }
        e.minutesLeft()?.let { left ->
            endTime.time = System.currentTimeMillis() + left * 60_000L
            val end = timeFormat.format(endTime)
            add(Chip.Text(string(R.string.ends_at, end)))
        }
        e.media?.chips()?.forEach { add(Chip.Outline(it)) }
    }

    override fun focusDefault() {
        if (!episodes.restoreFocus()) tabs.focusSelected()
    }

    /** Back from the player: reload the season for the new progress and checks, on the same episode. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (first) return
        // The player may have moved on to the next episode, even into the next season: focus that one
        val played = app.lastPlayed?.takeIf { it.seriesId == seriesId }
        val season = seasons.firstOrNull { it.id == played?.seasonId } ?: seasons.firstOrNull { it.id == shownSeason } ?: return
        val key = played?.id ?: (episodes.remembered() as? Card)?.item?.id
        shownSeason = null
        select(season, key, focus = episodes.hasFocus() || view.findFocus() == null || played != null)
    }

    override val livePage get() = LivePage.SEASON

    /**
     * Reloads the shown season in place: the same episode stays selected, and focus only returns to
     * the row when it was there (the actions, the tabs and the rail keep theirs).
     */
    override fun onLive(kinds: Set<LiveKind>) {
        // A load still running (the first one, or a tab switch) or a tab being chosen would lose its
        // target and focus: the event waits for it to end, or for focus to leave the tabs
        if (loadJob?.isActive == true || tabs.hasFocus()) {
            liveMissed = true
            return
        }
        liveMissed = false
        val season = seasons.firstOrNull { it.id == shownSeason } ?: return
        val key = (episodes.remembered() as? Card)?.item?.id
        shownSeason = null
        select(season, key, focus = episodes.hasFocus(), quiet = true)
    }

    private var liveMissed = false

    private fun catchUpLive() {
        if (liveMissed) onLive(emptySet())
    }

    private companion object {
        const val TAB_SETTLE_MS = 300L
    }
}
