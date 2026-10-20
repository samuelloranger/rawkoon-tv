package cloud.rawkoon.tv.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.FocusParking
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.FocusMotion
import cloud.rawkoon.tv.ui.FocusRow
import cloud.rawkoon.tv.ui.Focusable
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.LogoView
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.chipRow
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Date

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
    private val logo = LogoView(context)
    // Long names shrink and wrap instead of being cut
    private val showName = label(context, 26f, Tokens.textPrimary, Fonts.title(context), lines = 2).apply {
        gravity = Gravity.BOTTOM
        setAutoSizeTextTypeUniformWithConfiguration(16, 26, 2, TypedValue.COMPLEX_UNIT_SP)
    }
    private val number = label(context, 17f, Tokens.accent, Fonts.title(context))
    private val episodeTitle = label(context, 26f, Tokens.textPrimary, Fonts.title(context))
    private val chips = FrameLayout(context)
    private val overview = label(context, 14f, 0xE6ECE3DB.toInt(), Fonts.body(context), lines = 2).apply { setLineSpacing(0f, 1.2f) }
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
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(showName, fill())
        content.addView(box, at(start, 58, context.dp(520), context.dp(48)))
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
        scope.launch {
            try {
                // coroutineScope keeps a failing child here, to be caught below, instead of
                // cancelling the screen's scope and reaching the uncaught handler
                coroutineScope {
                    val details = async { session.details(seriesId) }
                    seasons = session.seasons(seriesId)
                    // A tab only switches the season if focus stays on it; leaving it cancels the switch
                    tabs.set(seasons, onLeave = { mainHandler.removeCallbacks(settleTab) }) { season, focused ->
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
                    backdrop.show(show.backdropUrl?.let { "$it&maxWidth=1920" })
                    val url = show.logoUrl
                    if (url == null) {
                        showName.text = show.title
                    } else {
                        val w = context.dp(240)
                        val h = context.dp(44)
                        app.images.load("$url&maxWidth=$w", w, h, 0f, contain = true, isCurrent = { alive }) { logo.bitmap = it }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Shows [season]'s episodes, focusing [episodeId] (or the first unwatched one) when [focus] is set. */
    private fun select(season: MediaItem, episodeId: String?, focus: Boolean) {
        tabs.select(season.id)
        if (season.id == shownSeason) {
            if (focus) episodes.restoreFocus()
            return
        }
        shownSeason = season.id
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
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
                val target = list.firstOrNull { it.id == episodeId } ?: list.firstOrNull { !it.played } ?: list.firstOrNull() ?: return@launch
                episodes.remember(target.id)
                showEpisode(target, settle = false)
                if (focus) episodes.restoreFocus()
                // Glide to the remembered card once the row is laid out
                episodes.post { episodes.remembered()?.let { episodes.reveal(it) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()
            } finally {
                parking.isFocusable = false
            }
        }
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

    private companion object {
        const val TAB_SETTLE_MS = 300L
    }
}

/** The season tabs. Coming from below, focus lands on the selected one. */
class TabsRow(context: Context) : LinearLayout(context) {
    private val tabs = mutableListOf<Tab>()

    init {
        orientation = HORIZONTAL
        noClip()
    }

    fun set(seasons: List<MediaItem>, onLeave: () -> Unit, onPick: (MediaItem, focused: Boolean) -> Unit) {
        removeAllViews()
        tabs.clear()
        for (season in seasons) {
            val tab = Tab(context, season)
            tab.setOnFocusChangeListener { _, focused ->
                tab.focusChanged(focused)
                if (focused) onPick(season, true) else onLeave()
            }
            tab.setOnClickListener { onPick(season, false) }
            tabs += tab
            addView(tab)
        }
    }

    fun select(id: String) = tabs.forEach { it.current = it.season.id == id }

    fun focusSelected() = (tabs.firstOrNull { it.current } ?: tabs.firstOrNull())?.requestFocus()

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val selected = tabs.firstOrNull { it.current }
        if (!hasFocus() && selected != null && (direction == FOCUS_UP || direction == FOCUS_DOWN)) {
            views.add(selected)
            return
        }
        super.addFocusables(views, direction, focusableMode)
    }
}

/** One season tab: its name, an apricot underline when it is the one shown, a ring when focused. */
@SuppressLint("ViewConstructor")
class Tab(context: Context, val season: MediaItem) : Focusable(context) {
    private val text: TextView = label(context, 14f, Tokens.textMuted, Fonts.bold(context)).apply {
        text = season.title
        gravity = Gravity.CENTER
    }
    private val ring = FlatView(context)
    override val motion = FocusMotion(this, ring)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.accent }
    private val rect = RectF()
    private val focusedBackground = GradientDrawable().apply {
        cornerRadius = context.dpf(15f)
        setColor(Tokens.surfaceFocused)
    }
    private val barInset = context.dpf(Tokens.RING_INSET.toFloat()) + context.dpf(12f)
    private val barBottom = context.dpf(Tokens.RING_INSET.toFloat()) + context.dpf(2f)
    private val barHeight = context.dpf(2.5f)

    var current = false
        set(value) {
            field = value
            paint()
            invalidate()
        }

    init {
        setWillNotDraw(false)
        val inset = context.dp(Tokens.RING_INSET)
        text.setPadding(context.dp(12), 0, context.dp(12), 0)
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30)).apply { setMargins(inset, inset, inset, inset) })
        ring.background = GradientDrawable().apply {
            cornerRadius = context.dpf(15f) + inset
            setStroke(context.dp(2), Tokens.ring)
        }
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun focusChanged(focused: Boolean) {
        motion.focus(focused)
        paint()
    }

    private fun paint() {
        text.setTextColor(if (current || isFocused) Tokens.textPrimary else Tokens.textMuted)
        text.background = if (isFocused) focusedBackground else null
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!current || isFocused) return
        val y = height - barBottom
        rect.set(barInset, y, width - barInset, y + barHeight)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, bar)
    }
}
