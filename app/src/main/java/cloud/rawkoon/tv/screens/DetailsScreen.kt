package cloud.rawkoon.tv.screens

import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.Details
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaInfo
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.live.LiveKind
import cloud.rawkoon.tv.live.LivePage
import cloud.rawkoon.tv.rawkoon.RawkoonHome
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
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
import kotlinx.coroutines.CancellationException
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
    /** A show's files across its episodes; a movie's file comes with the item. */
    private var showMedia: MediaInfo? = null
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    /** Whether Rawkoon's watchlist holds the title; null until known, or when the page has no way to ask. */
    private var inList: Boolean? = null
    private var listBusy = false

    init {
        page.column.setPadding(0, 0, 0, context.dp(200))
        page.anchor = context.dp(34)
        content.addView(page, fill())
        block.setActions(actions.view)
        actions.onWatchlist = ::toggleWatchlist
        // New artwork from "…" shows on the page at once; the toggles leave the image URLs alone
        actions.onChanged = { id, change ->
            if (id == item.id) {
                val updated = change(item)
                if (updated.posterUrl != item.posterUrl) block.setPoster(updated.posterUrl, fillWidth = true)
                if (updated.backdropUrl != item.backdropUrl) backdrop.show(backdropRequest(updated.backdropUrl))
                item = updated
            }
        }
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
        (if (show) showMedia else item.media)?.chips()?.forEach { add(Chip.Outline(it)) }
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
            loadWatchlist(d)
            backdrop.show(backdropRequest(d.item.backdropUrl))
            addRows(d, seasons.await(), similar.await())
            // Every episode's streams are the heaviest read, so they wait for the page: their chips join the meta line after
            if (start.kind != MediaItem.Kind.SERIES) return@launchLoading
            showMedia = runCatching { session.showMedia(start.id) }.getOrNull() ?: return@launchLoading
            block.setMeta(metaChips(d.item, d))
        }
    }

    private fun rawkoon() = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context)

    private var tmdbId: Int? = null

    /**
     * The button needs a Rawkoon session and the title's TMDB id. The state is one read of the
     * watchlist, lighter than the title modal (which pulls TMDB's details and credits) for one flag.
     */
    private fun loadWatchlist(d: Details) {
        tmdbId = d.tmdbId
        val id = d.tmdbId ?: return
        val api = rawkoon() ?: return
        scope.launch {
            val list = runCatching { api.watchlist() }.getOrNull() ?: return@launch
            inList = list.any { it.tmdbId == id && it.isMovie == (start.kind != MediaItem.Kind.SERIES) }
            actions.showWatchlist(inList)
        }
    }

    /** Flips at once and goes back, with a message, if Rawkoon refuses. */
    private fun toggleWatchlist() {
        val id = tmdbId ?: return
        val api = rawkoon() ?: return
        if (listBusy) return
        val wanted = inList != true
        val isMovie = item.kind != MediaItem.Kind.SERIES
        inList = wanted
        actions.showWatchlist(wanted)
        listBusy = true
        // Set before the call: leaving the page cancels it after the server may already have the change
        RawkoonHome.watchlistChanged = true
        scope.launch {
            try {
                if (wanted) api.addToWatchlist(isMovie, id, item.title, null, item.overview.ifEmpty { null }, item.year, item.communityRating?.toDouble())
                else api.removeFromWatchlist(isMovie, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inList = !wanted
                actions.showWatchlist(inList)
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
            } finally {
                listBusy = false
            }
        }
    }

    private fun addRows(details: Details, seasons: List<MediaItem>, similar: List<MediaItem>) {
        if (seasons.isNotEmpty()) {
            val section = Section(context, "seasons", string(R.string.row_seasons, seasons.size))
            section.row.setCards(
                seasons.map { s ->
                    val count = s.childCount?.let { context.resources.getQuantityString(R.plurals.episodes_count, it, it) }
                    Card(context, s, false, app.images, {}, { openItem(it.item) }, caption = s.title to count).withMenu()
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
            section.row.setCards(similar.map { s -> Card(context, s, false, app.images, {}, { openItem(it.item) }).withMenu() })
            addSection(section)
        }
    }

    private fun addSection(section: Section) {
        section.row.onLeftEdge = { rail.activeItem() }
        page.column.addView(section)
        // Only the first row peeks in under the title block
        if (page.column.childCount == 2 && isFullyShown) section.row.arrive()
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
                item = d.item
                actions.bind(d.item)
            }
        }
    }
}
