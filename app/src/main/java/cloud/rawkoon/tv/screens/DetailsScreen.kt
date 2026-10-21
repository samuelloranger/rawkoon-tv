package cloud.rawkoon.tv.screens

import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Details
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.ui.Card
import cloud.rawkoon.tv.ui.Chip
import cloud.rawkoon.tv.ui.Destination
import cloud.rawkoon.tv.ui.PersonCard
import cloud.rawkoon.tv.ui.Rows
import cloud.rawkoon.tv.ui.Section
import cloud.rawkoon.tv.ui.TitleBlock
import cloud.rawkoon.tv.ui.backdropRequest
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.kindChip
import cloud.rawkoon.tv.ui.showCountChips
import cloud.rawkoon.tv.ui.yearChip
import kotlinx.coroutines.Job
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
    private val block = TitleBlock(context, app.images) { alive }
    private val actions = ActionBar(this)
    private var item = start
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        page.column.setPadding(0, 0, 0, context.dp(200))
        page.anchor = context.dp(34)
        content.addView(page, fill())
        block.setActions(actions.view)
        page.column.addView(block, TitleBlock.params(context))
        showItem(start, null)
        backdrop.show(backdropRequest(start.backdropUrl))
        load()
    }

    /** Fills the top block; [details] adds the genres, the studio and a show's span when it arrives. */
    private fun showItem(item: MediaItem, details: Details?) {
        this.item = item
        block.setPoster(item.posterUrl, fillWidth = true)
        val tags = details?.genres.orEmpty().map { Chip.Outline(it) } + listOfNotNull(details?.studio?.let { Chip.Text(it) })
        block.bind(item.title, item.logoUrl, metaChips(item, details), tags, item.overview)
        actions.bind(item)
    }

    private fun metaChips(item: MediaItem, details: Details?): List<Chip> = buildList {
        val show = item.kind == MediaItem.Kind.SERIES
        add(kindChip(context, show))
        item.year?.let { add(yearChip(it, show, details?.continuing == true, details?.endYear)) }
        if (show) {
            addAll(showCountChips(context, details?.seasonCount, details?.episodeCount))
        } else {
            item.runtimeMinutes?.takeIf { it > 0 }?.let { add(Chip.Text(MediaItem.duration(it))) }
        }
        item.officialRating?.let { add(Chip.Text(it)) }
        item.score()?.let { add(Chip.Score(it)) }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = launchLoading {
            val details = async { session.details(start.id) }
            val seasons = async { if (start.kind == MediaItem.Kind.SERIES) session.seasons(start.id) else emptyList() }
            val next = async { if (start.kind == MediaItem.Kind.SERIES) runCatching { session.nextEpisode(start.id) }.getOrNull() else null }
            val similar = async { runCatching { session.similar(start.id) }.getOrDefault(emptyList()) }
            val d = details.await()
            showItem(d.item, d)
            actions.next = next.await()
            actions.bind(d.item)
            backdrop.show(backdropRequest(d.item.backdropUrl))
            addRows(d, seasons.await(), similar.await())
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

    /** Back from the player: the progress and the episode to play next have changed. */
    override fun onShown(first: Boolean) {
        super.onShown(first)
        if (!first) refresh()
    }

    override val livePage get() = LivePage.DETAILS

    override fun onLive(kinds: Set<LiveKind>) = refresh()

    /** Reads the title again for the progress, the checks and the episode to play next; the rows under it stay. */
    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            runCatching {
                app.awaitStop()
                val d = session.details(start.id)
                actions.next = if (start.kind == MediaItem.Kind.SERIES) session.nextEpisode(start.id) else null
                actions.bind(d.item)
            }
        }
    }
}
