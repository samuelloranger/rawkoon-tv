package cloud.rawkoon.tv.screens

import android.view.Gravity
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Details
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.CircleButton
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.LogoView
import cloud.rawkoon.tv.ui.PersonCard
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.chipRow
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import cloud.rawkoon.tv.ui.rounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * A movie's or a show's page, as in Wholphin: the poster, the logo, the meta line, genres and
 * overview, the actions, then the seasons (for a show), the cast and similar titles. The page
 * glides up as focus moves down into the rows.
 */
class DetailsScreen(activity: MainActivity, session: JellyfinSession, private val start: MediaItem) :
    RailScreen(activity, session, if (start.kind == MediaItem.Kind.SERIES) Destination.SHOWS else Destination.MOVIES) {
    private val page = Rows(context)
    private val block = FrameLayout(context).noClip()
    private val poster = ImageView(context)
    private val logo = LogoView(context)
    private val title = label(context, 34f, Tokens.textPrimary, Fonts.title(context), lines = 2).apply { gravity = Gravity.BOTTOM }
    private val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val actions = ActionBar(this)
    private var item = start

    init {
        page.column.setPadding(0, 0, 0, context.dp(200))
        page.anchor = context.dp(34)
        content.addView(page, fill())
        buildBlock()
        page.column.addView(block, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(BLOCK_H)))
        showItem(start, null)
        backdrop.show(start.backdropUrl?.let { "$it&maxWidth=1920" })
        load()
    }

    private fun buildBlock() {
        val radius = context.dpf(8f)
        poster.background = rounded(Tokens.surfaceHigh, radius)
        block.addView(poster, FrameLayout.LayoutParams(context.dp(POSTER_W), context.dp(POSTER_H)).apply {
            leftMargin = context.dp(Tokens.CONTENT_START)
            topMargin = context.dp(TOP)
        })
        val box = FrameLayout(context)
        box.addView(logo, fill())
        box.addView(title, fill())
        info.addView(box, LinearLayout.LayoutParams(context.dp(LOGO_W), context.dp(LOGO_H)))
        block.addView(info, FrameLayout.LayoutParams(context.dp(540), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START + POSTER_W + 25)
            topMargin = context.dp(TOP)
        })
        block.addView(actions.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.CONTENT_START - Tokens.RING_INSET)
            topMargin = context.dp(TOP + POSTER_H + 14)
        })
        title.setAutoSizeTextTypeUniformWithConfiguration(20, 34, 2, TypedValue.COMPLEX_UNIT_SP)
    }

    /** Fills the top block; [details] adds the genres, the studio and a show's span when it arrives. */
    private fun showItem(item: MediaItem, details: Details?) {
        this.item = item
        val radius = context.dpf(8f)
        val w = context.dp(POSTER_W)
        val h = context.dp(POSTER_H)
        item.posterUrl?.let { url -> app.images.load("$url&fillWidth=$w", w, h, radius, isCurrent = { true }) { poster.setImageBitmap(it) } }
        val logoUrl = item.logoUrl
        if (logoUrl == null) {
            title.text = item.title
        } else {
            title.text = ""
            val lw = context.dp(LOGO_W)
            val lh = context.dp(LOGO_H)
            app.images.load("$logoUrl&maxWidth=$lw", lw, lh, 0f, contain = true, isCurrent = { true }) { logo.bitmap = it }
        }
        while (info.childCount > 1) info.removeViewAt(1)
        info.addView(chipRow(context, metaChips(item, details)), wrapRow(10))
        val tags = details?.genres.orEmpty().map { Chip.Outline(it) } + listOfNotNull(details?.studio?.let { Chip.Text(it) })
        if (tags.isNotEmpty()) info.addView(chipRow(context, tags, 12.5f), wrapRow(8))
        info.addView(label(context, 14f, 0xE6ECE3DB.toInt(), Fonts.body(context), lines = 3).apply {
            text = item.overview
            setLineSpacing(0f, 1.2f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
        actions.bind(item)
    }

    private fun wrapRow(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(top) }

    private fun metaChips(item: MediaItem, details: Details?): List<Chip> = buildList {
        val show = item.kind == MediaItem.Kind.SERIES
        add(Chip.Kind(string(if (show) R.string.kind_show else R.string.kind_movie)))
        item.year?.let { year ->
            add(
                Chip.Text(
                    when {
                        !show -> year.toString()
                        details?.continuing == true -> "$year –"
                        details?.endYear != null && details.endYear != year -> "$year – ${details.endYear}"
                        else -> year.toString()
                    },
                ),
            )
        }
        if (show) {
            details?.seasonCount?.let { add(Chip.Text(context.resources.getQuantityString(R.plurals.seasons_count, it, it))) }
            details?.episodeCount?.let { add(Chip.Text(context.resources.getQuantityString(R.plurals.episodes_count, it, it))) }
        } else {
            item.runtimeMinutes?.takeIf { it > 0 }?.let { add(Chip.Text(MediaItem.duration(it))) }
        }
        item.officialRating?.let { add(Chip.Text(it)) }
        item.score()?.let { add(Chip.Score(it)) }
    }

    private fun load() {
        scope.launch {
            try {
                val details = async { session.details(start.id) }
                val seasons = async { if (start.kind == MediaItem.Kind.SERIES) session.seasons(start.id) else emptyList() }
                val next = async { if (start.kind == MediaItem.Kind.SERIES) runCatching { session.nextEpisode(start.id) }.getOrNull() else null }
                val similar = async { runCatching { session.similar(start.id) }.getOrDefault(emptyList()) }
                val d = details.await()
                showItem(d.item, d)
                actions.next = next.await()
                actions.bind(d.item)
                backdrop.show(d.item.backdropUrl?.let { "$it&maxWidth=1920" })
                addRows(d, seasons.await(), similar.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, R.string.page_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addRows(details: Details, seasons: List<MediaItem>, similar: List<MediaItem>) {
        if (seasons.isNotEmpty()) {
            val section = Section(context, "seasons", string(R.string.row_seasons, seasons.size))
            section.row.setCards(
                seasons.map { s ->
                    val count = s.childCount?.let { context.resources.getQuantityString(R.plurals.episodes_count, it, it) }
                    Card(context, s, false, app.images, {}, { openItem(it.item) }, caption = s.title to count)
                },
            )
            addSection(section)
        }
        if (details.people.isNotEmpty()) {
            val section = Section(context, "cast", string(R.string.row_cast))
            section.row.setCards(details.people.map { PersonCard(context, it, app.images) })
            addSection(section)
        }
        if (similar.isNotEmpty()) {
            val section = Section(context, "similar", string(R.string.row_similar))
            section.row.setCards(similar.map { s -> Card(context, s, false, app.images, {}, { openItem(it.item) }) })
            addSection(section)
        }
    }

    private fun addSection(section: Section) {
        section.row.onLeftEdge = { rail.activeItem() }
        page.column.addView(section)
    }

    override fun focusDefault() {
        actions.primary.requestFocus()
    }

    private companion object {
        const val TOP = 50
        const val POSTER_W = 135
        const val POSTER_H = 202
        const val LOGO_W = 330
        const val LOGO_H = 66
        const val BLOCK_H = TOP + POSTER_H + 14 + 56
    }
}

/**
 * The actions under a title: play or resume (the player comes in the next milestone), watched and
 * favorite. The toggles change on screen at once and go back if the server refuses.
 */
class ActionBar(private val screen: RailScreen) {
    private val context = screen.context
    val primary = PillButton(context, context.getString(R.string.play), Icon.PLAY, primary = true, height = 40)

    /** Coming from above or below, focus lands on the main action rather than the nearest button. */
    val view: LinearLayout = object : LinearLayout(context) {
        override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
            if (!hasFocus() && (direction == FOCUS_UP || direction == FOCUS_DOWN)) {
                views.add(primary)
                return
            }
            super.addFocusables(views, direction, focusableMode)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }.noClip()
    private val watched = CircleButton(context, Icon.CHECK, context.getString(R.string.watched))
    private val favorite = CircleButton(context, Icon.HEART, context.getString(R.string.favorite))
    private var item: MediaItem? = null

    /** For a show: the episode the play button resumes or starts. */
    var next: MediaItem? = null

    init {
        view.addView(primary)
        view.addView(watched, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(6) })
        view.addView(favorite, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(2) })
        primary.setOnClickListener { Toast.makeText(context, R.string.player_soon, Toast.LENGTH_SHORT).show() }
        watched.setOnClickListener { toggle(watched = true) }
        favorite.setOnClickListener { toggle(watched = false) }
    }

    fun bind(item: MediaItem) {
        this.item = item
        primary.label.text = playLabel(item)
        watched.setOn(item.played)
        favorite.setOn(item.favorite)
    }

    private fun playLabel(item: MediaItem): String {
        val episode = next?.takeIf { item.kind == MediaItem.Kind.SERIES } ?: item.takeIf { it.kind == MediaItem.Kind.EPISODE }
        val started = (episode ?: item).progress != null
        val label = episode?.episodeLabel
        return when {
            label != null -> context.getString(if (started) R.string.resume_episode else R.string.play_episode, label)
            started -> context.getString(R.string.resume)
            else -> context.getString(R.string.play)
        }
    }

    private fun toggle(watched: Boolean) {
        val current = item ?: return
        val updated = if (watched) current.copy(played = !current.played) else current.copy(favorite = !current.favorite)
        bind(updated)
        screen.scope.launch {
            try {
                if (watched) screen.session.setPlayed(current.id, updated.played) else screen.session.setFavorite(current.id, updated.favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (item?.id == current.id) bind(current)
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
