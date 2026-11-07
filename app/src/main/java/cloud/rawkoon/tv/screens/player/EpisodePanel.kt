package cloud.rawkoon.tv.screens.player

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.App
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.FocusRow
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.TabsRow
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.clock
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The show's seasons and episodes over the playing video, which plays on dimmed behind: the season
 * tabs, the focused episode described, and its season's episodes in a row, as on the show's
 * season page. OK on an episode asks [play] for it; on the one playing, it closes. Built on the
 * first open; each season's episodes are fetched once per player.
 */
class EpisodePanel(
    private val context: Context,
    private val app: App,
    private val session: JellyfinSession,
    private val scope: CoroutineScope,
    private val main: Handler,
    private val root: FrameLayout,
    private val play: (MediaItem) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val view = FrameLayout(context).noClip()
    private val heading = label(context, 24f, Tokens.textPrimary, Fonts.title(context))
    private val playing = label(context, 14f, Tokens.textSoft, Fonts.medium(context))
    private val tabs = TabsRow(context)
    private val number = label(context, 17f, Tokens.accent, Fonts.title(context))
    private val episodeTitle = label(context, 24f, Tokens.textPrimary, Fonts.title(context))
    private val meta = label(context, 13f, Tokens.textMuted, Fonts.medium(context))
    private val overview = label(context, 14f, Tokens.textBody, Fonts.body(context), lines = 2).apply { setLineSpacing(0f, 1.2f) }
    private val episodes = FocusRow(context)
    private val parking = FocusParking(context)
    private var built = false

    private var current: MediaItem? = null
    private var seriesId: String? = null
    private var seasons: List<MediaItem> = emptyList()
    private val seasonEpisodes = HashMap<String, List<MediaItem>>()
    private var shownSeason: String? = null
    private var loadJob: Job? = null

    /** Whether the season loading now takes focus once its episodes are in. */
    private var focusOnLoad = false
    private var pendingTab: MediaItem? = null
    private val settleTab = Runnable { pendingTab?.let { select(it, null, focus = false) } }
    private var pendingEpisode: MediaItem? = null
    private val settleEpisode = Runnable { describePending() }

    val isOpen: Boolean get() = view.parent != null

    /** Opens on [episode], the one playing, in its season. */
    fun open(episode: MediaItem) {
        val series = episode.seriesId ?: return
        if (!built) build()
        if (series != seriesId) {
            seriesId = series
            seasons = emptyList()
            seasonEpisodes.clear()
        }
        current = episode
        shownSeason = null
        heading.text = episode.headerTitle
        playing.text = context.getString(R.string.episodes_now_playing, listOfNotNull(episode.episodeLabel, episode.title).joinToString(" — "))
        describe(episode, settle = false)
        root.addView(view, fill())
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(Tokens.SCREEN_FADE_MS).start()
        // Focus waits inside the panel until the episodes are in, so the keys don't reach the player
        park()
        loadJob?.cancel()
        loadJob = launchLoading {
            if (seasons.isEmpty()) {
                // Off the main thread: the client reads the response body where it is called
                seasons = withContext(Dispatchers.IO) { session.seasons(series) }
                tabs.set(seasons, onLeave = { main.removeCallbacks(settleTab) }) { season, focused ->
                    main.removeCallbacks(settleTab)
                    if (focused) {
                        // Moving along the tabs only switches once focus rests on one
                        pendingTab = season
                        main.postDelayed(settleTab, TAB_SETTLE_MS)
                    } else {
                        select(season, null, focus = true)
                    }
                }
            }
            val season = seasons.firstOrNull { it.id == episode.seasonId } ?: seasons.firstOrNull() ?: return@launchLoading close()
            select(season, episode.id, focus = true)
        }
    }

    fun close() {
        if (!isOpen) return
        loadJob?.cancel()
        main.removeCallbacks(settleTab)
        main.removeCallbacks(settleEpisode)
        view.animate().cancel()
        root.removeView(view)
        onClosed()
    }

    private fun build() {
        built = true
        view.isClickable = true
        // Light at the top, where the video shows through, and close to opaque behind the row
        val scrim = Tokens.background and 0x00FFFFFF
        view.addView(FlatView(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(scrim or 0x66000000, scrim or 0xD6000000.toInt(), scrim or 0xF7000000.toInt(), scrim or 0xF7000000.toInt()),
            )
        }, fill())
        view.addView(heading, at(48, 36, context.dp(700), ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(playing, at(48, 76, context.dp(700), ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        view.addView(tabs, at(48 - Tokens.RING_INSET, 186, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(number, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(8) })
            addView(episodeTitle, LinearLayout.LayoutParams(context.dp(600), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        view.addView(titleRow, at(48, 236, ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(32)))
        view.addView(meta, at(48, 272, context.dp(600), ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(overview, at(48, 294, context.dp(575), context.dp(42)))
        // The row starts its cards at the pages' content edge; here they line up with the text instead
        view.addView(episodes, at(48 - Tokens.CONTENT_START, 344, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = context.dp(48 - Tokens.CONTENT_START)
        })
        view.addView(parking, FrameLayout.LayoutParams(1, 1))
    }

    private fun park() {
        parking.isFocusable = true
        parking.requestFocus()
    }

    private fun at(left: Int, top: Int, w: Int, h: Int) = FrameLayout.LayoutParams(w, h).apply {
        leftMargin = context.dp(left)
        topMargin = context.dp(top)
    }

    /** Shows [season]'s episodes, focusing [episodeId] (or the first unwatched one) when [focus] is set. */
    private fun select(season: MediaItem, episodeId: String?, focus: Boolean) {
        val series = seriesId ?: return
        tabs.select(season.id)
        if (season.id == shownSeason) {
            if (focus && loadJob?.isActive == true) focusOnLoad = true else if (focus) episodes.restoreFocus()
            return
        }
        shownSeason = season.id
        focusOnLoad = focus
        loadJob?.cancel()
        loadJob = launchLoading {
            val list = seasonEpisodes[season.id] ?: withContext(Dispatchers.IO) { session.episodes(series, season.id) }.also { seasonEpisodes[season.id] = it }
            val playingId = current?.id
            val refocus = focusOnLoad || episodes.hasFocus()
            // Focus on the old cards waits on the panel while they go, rather than falling to a tab
            if (episodes.hasFocus()) park()
            episodes.setCards(
                list.map { e ->
                    val name = listOfNotNull(e.episodeNumber?.let { "E$it" }, e.title).joinToString(" · ")
                    val detail = if (e.id == playingId) context.getString(R.string.now_playing) else e.runtimeMinutes?.let { MediaItem.duration(it) }
                    Card(context, e, true, app.images, { describe(it.item) }, { pick(it.item) }, caption = name to detail)
                },
            )
            val target = list.firstOrNull { it.id == episodeId } ?: list.firstOrNull { !it.played } ?: list.firstOrNull() ?: return@launchLoading
            episodes.remember(target.id)
            describe(target, settle = false)
            if (refocus) episodes.restoreFocus()
            parking.isFocusable = false
            episodes.remembered()?.let(episodes::snapTo)
        }
    }

    private fun pick(episode: MediaItem) {
        if (episode.id == current?.id) close() else play(episode)
    }

    /** The number and title follow focus at once; the rest waits until focus rests on the episode. */
    private fun describe(episode: MediaItem, settle: Boolean = true) {
        number.text = listOfNotNull(episode.seasonNumber?.let { "S$it" }, episode.episodeNumber?.let { "E$it" }).joinToString(":")
        episodeTitle.text = episode.title
        pendingEpisode = episode
        main.removeCallbacks(settleEpisode)
        if (settle) main.postDelayed(settleEpisode, Tokens.HEADER_SETTLE_MS) else describePending()
    }

    private fun describePending() {
        main.removeCallbacks(settleEpisode)
        val episode = pendingEpisode ?: return
        pendingEpisode = null
        meta.text = listOfNotNull(
            episode.runtimeMinutes?.takeIf { it > 0 }?.let { MediaItem.duration(it) },
            if (episode.id == current?.id) context.getString(R.string.now_playing) else null,
        ).joinToString(" · ")
        overview.text = episode.overview
    }

    /** Runs [block] on the player's scope; a failure closes the panel with a toast. */
    private fun launchLoading(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "episodes failed", e)
            Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()
            close()
        }
    }

    private companion object {
        const val TAG = "EpisodePanel"
        const val TAB_SETTLE_MS = 300L
    }
}
