package cloud.rawkoon.tv.screens

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.rawkoon.RawkoonHome
import cloud.rawkoon.tv.rawkoon.rawTmdbImage
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.ui.CardMenu
import cloud.rawkoon.tv.ui.Icon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What a card's menu can do with its item. */
enum class CardAction { PLAY, RESUME, START_OVER, MARK_WATCHED, MARK_UNWATCHED, ADD_FAVORITE, REMOVE_FAVORITE, ADD_TO_LIST, REMOVE_FROM_LIST, GO_TO_SHOW }

/**
 * The actions a card's menu offers for [item], in order: play or resume (and start over) for what
 * plays on its own, watched and favorite for anything in the library, "My list" for a movie or a
 * show when [inList] is known (null: no Rawkoon, or no TMDB id), and the show for an episode or a
 * season. A TMDB title from Rawkoon is not in the library: only "My list" applies to it.
 */
fun cardActions(item: MediaItem, inList: Boolean?): List<CardAction> = buildList {
    val list = when (inList) {
        true -> CardAction.REMOVE_FROM_LIST
        false -> CardAction.ADD_TO_LIST
        null -> null
    }
    if (item.tmdb != null) {
        list?.let(::add)
        return@buildList
    }
    val kind = item.kind
    if (kind == MediaItem.Kind.OTHER) return@buildList
    if (kind == MediaItem.Kind.MOVIE || kind == MediaItem.Kind.EPISODE) {
        if (item.resumeMs > 0) {
            add(CardAction.RESUME)
            add(CardAction.START_OVER)
        } else {
            add(CardAction.PLAY)
        }
    }
    add(if (item.played) CardAction.MARK_UNWATCHED else CardAction.MARK_WATCHED)
    add(if (item.favorite) CardAction.REMOVE_FAVORITE else CardAction.ADD_FAVORITE)
    if (kind == MediaItem.Kind.MOVIE || kind == MediaItem.Kind.SERIES) list?.let(::add)
    if ((kind == MediaItem.Kind.EPISODE || kind == MediaItem.Kind.SEASON) && item.seriesId != null) add(CardAction.GO_TO_SHOW)
}

/** Applies a change (or its undo) to the page's copy of the item with this id, wherever it is now. */
typealias Changed = (id: String, change: (MediaItem) -> MediaItem) -> Unit

/** The title as "My list" keys it, movie or show and its TMDB id; null for what can't be in the list. */
data class ListKey(val isMovie: Boolean, val tmdbId: Int)

fun listKey(item: MediaItem): ListKey? {
    item.tmdb?.let { return ListKey(item.kind != MediaItem.Kind.SERIES, it.id) }
    if (item.kind != MediaItem.Kind.MOVIE && item.kind != MediaItem.Kind.SERIES) return null
    return item.libraryTmdbId?.let { ListKey(item.kind == MediaItem.Kind.MOVIE, it) }
}

/** The menu's subtitle: "S1 · E3 · Title" for an episode, the year (or a TMDB title's date) for the rest. */
fun cardMenuSubtitle(item: MediaItem): String? = when {
    item.tmdb != null -> item.tmdb.subtitle ?: item.year?.toString()
    item.kind == MediaItem.Kind.EPISODE -> listOfNotNull(item.episodeLabel, item.title).joinToString(" · ")
    else -> item.year?.toString()
}

/**
 * Opens a card's menu and runs what is picked, for every card of a page. Changes show at once
 * through the page's `changed` and go back, with a message, if the server refuses. Whether a
 * title is in "My list" is one read of the watchlist, kept for a minute and updated by the
 * menu's own changes.
 */
class CardMenus(private val screen: RailScreen) {
    private val context = screen.context
    private var menu: CardMenu? = null
    private var watchlist: Set<ListKey>? = null
    private var watchlistAt = 0L
    private var listBusy = false

    /**
     * Long-press OK or Menu on [card] opens its menu. [item] reads the card's current item (null:
     * not loaded yet); [extras] are the page's own actions for it, after the shared ones; [changed]
     * applies a change (or its undo) to the page's copy of an item, found by id: a grid cell may
     * show another title by the time the server answers.
     */
    fun attach(card: View, item: () -> MediaItem?, extras: (MediaItem) -> List<CardMenu.Entry> = { emptyList() }, changed: Changed = { _, _ -> }) {
        val open = { item()?.let { open(card, it, extras(it), changed) } }
        card.setOnLongClickListener {
            open()
            true
        }
        card.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_MENU) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) open()
            true
        }
    }

    /** Closes an open menu; true when there was one. */
    fun close(): Boolean {
        val open = menu?.takeIf { it.isAttachedToWindow && !it.closing } ?: return false
        open.close()
        return true
    }

    private fun open(card: View, item: MediaItem, extras: List<CardMenu.Entry>, changed: Changed) {
        if (menu?.isAttachedToWindow == true || !card.isAttachedToWindow) return
        val key = listKey(item)
        val api = rawkoon()
        val known = key?.let { k -> freshWatchlist()?.let { k in it } }
        val entries = cardActions(item, known).map { entry(it, item, key, changed) } + extras
        val lookUp = key != null && api != null && known == null
        if (entries.isEmpty() && !lookUp) return
        // A title whose only action is "My list" waits for the list: an empty menu would only flash
        val shown = if (entries.isNotEmpty()) show(card, item, entries) else null
        if (!lookUp) return
        screen.scope.launch {
            val list = try {
                api!!.watchlist().map { ListKey(it.isMovie, it.tmdbId) }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            watchlist = list
            watchlistAt = SystemClock.elapsedRealtime()
            val action = if (key!! in list) CardAction.REMOVE_FROM_LIST else CardAction.ADD_TO_LIST
            val entry = entry(action, item, key, changed)
            when {
                shown != null -> shown.add(entry)
                card.isAttachedToWindow && card.hasFocus() && menu?.isAttachedToWindow != true -> show(card, item, listOf(entry))
            }
        }
    }

    private fun show(card: View, item: MediaItem, entries: List<CardMenu.Entry>): CardMenu =
        CardMenu(context, item.headerTitle, cardMenuSubtitle(item), entries).also {
            menu = it
            it.onClosed = { if (menu === it) menu = null }
            it.open(screen.view, card)
        }

    private fun freshWatchlist() = watchlist?.takeIf { SystemClock.elapsedRealtime() - watchlistAt < WATCHLIST_FRESH_MS && !RawkoonHome.watchlistChanged }

    private fun rawkoon() = (screen.app.profiles.get(screen.session.profile.id) ?: screen.session.profile).rawkoonDiscover(context)

    private fun entry(action: CardAction, item: MediaItem, key: ListKey?, changed: Changed): CardMenu.Entry {
        fun e(glyph: String, text: Int, run: () -> Unit) = CardMenu.Entry(glyph, screen.string(text), run)
        return when (action) {
            CardAction.PLAY -> e(Icon.PLAY, R.string.play) { play(item) }
            CardAction.RESUME -> e(Icon.PLAY, R.string.resume) { play(item) }
            CardAction.START_OVER -> e(Icon.REWIND, R.string.card_start_over) { play(item.copy(resumeMs = 0, progress = null)) }
            CardAction.MARK_WATCHED -> e(Icon.CHECK, R.string.card_mark_watched) { setPlayed(item, true, changed) }
            CardAction.MARK_UNWATCHED -> e(Icon.EYE_SLASH, R.string.card_mark_unwatched) { setPlayed(item, false, changed) }
            CardAction.ADD_FAVORITE -> e(Icon.HEART, R.string.card_add_favorite) { setFavorite(item, true, changed) }
            CardAction.REMOVE_FAVORITE -> e(Icon.HEART_CRACK, R.string.card_remove_favorite) { setFavorite(item, false, changed) }
            CardAction.ADD_TO_LIST -> e(Icon.BOOKMARK, R.string.rk_watchlist_add) { setInList(item, key ?: return@e, true) }
            CardAction.REMOVE_FROM_LIST -> e(Icon.XMARK, R.string.card_remove_from_list) { setInList(item, key ?: return@e, false) }
            CardAction.GO_TO_SHOW -> e(Icon.TV, R.string.card_go_to_show) { goToShow(item) }
        }
    }

    private fun play(item: MediaItem) = screen.activity.push(PlayerScreen(screen.activity, screen.session, item))

    /** Marking watched clears the resume point on the server: the card loses its bar at once. */
    private fun setPlayed(item: MediaItem, played: Boolean, changed: Changed) {
        changed(item.id) { if (played) it.copy(played = true, progress = null, resumeMs = 0) else it.copy(played = false) }
        screen.scope.launch {
            try {
                screen.session.setPlayed(item.id, played)
                screen.onCardChanged(rawkoon = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Put back only this field: another action may have changed the item meanwhile
                changed(item.id) { it.copy(played = item.played, progress = item.progress, resumeMs = item.resumeMs) }
                failed()
            }
        }
    }

    private fun setFavorite(item: MediaItem, favorite: Boolean, changed: Changed) {
        changed(item.id) { it.copy(favorite = favorite) }
        screen.scope.launch {
            try {
                screen.session.setFavorite(item.id, favorite)
                screen.onCardChanged(rawkoon = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                changed(item.id) { it.copy(favorite = item.favorite) }
                failed()
            }
        }
    }

    private fun setInList(item: MediaItem, key: ListKey, wanted: Boolean) {
        val api = rawkoon() ?: return failed()
        if (listBusy) return
        listBusy = true
        watchlist = watchlist?.let { if (wanted) it + key else it - key }
        // Set before the call: leaving the page cancels it after the server may already have the change
        RawkoonHome.watchlistChanged = true
        screen.scope.launch {
            try {
                if (wanted) {
                    api.addToWatchlist(key.isMovie, key.tmdbId, item.title, item.tmdb?.let { rawTmdbImage(item.posterUrl) }, item.overview.ifEmpty { null }, item.year, item.communityRating?.toDouble())
                } else {
                    api.removeFromWatchlist(key.isMovie, key.tmdbId)
                }
                screen.onCardChanged(rawkoon = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                watchlist = watchlist?.let { if (wanted) it - key else it + key }
                failed()
            } finally {
                listBusy = false
            }
        }
    }

    private fun goToShow(item: MediaItem) {
        val seriesId = item.seriesId ?: return
        screen.scope.launch {
            try {
                val show = screen.session.details(seriesId).item
                screen.activity.push(DetailsScreen(screen.activity, screen.session, show))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed()
            }
        }
    }

    private fun failed() = Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()

    private companion object {
        /** How long a read of the watchlist answers "is it in My list?" before the menu asks again. */
        const val WATCHLIST_FRESH_MS = 60_000L
    }
}
