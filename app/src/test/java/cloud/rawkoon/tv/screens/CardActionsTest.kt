package cloud.rawkoon.tv.screens

import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbRef
import cloud.rawkoon.tv.screens.CardAction.ADD_FAVORITE
import cloud.rawkoon.tv.screens.CardAction.ADD_TO_LIST
import cloud.rawkoon.tv.screens.CardAction.CHANGE_BACKGROUND
import cloud.rawkoon.tv.screens.CardAction.CHANGE_POSTER
import cloud.rawkoon.tv.screens.CardAction.GO_TO_SHOW
import cloud.rawkoon.tv.screens.CardAction.MARK_UNWATCHED
import cloud.rawkoon.tv.screens.CardAction.MARK_WATCHED
import cloud.rawkoon.tv.screens.CardAction.PLAY
import cloud.rawkoon.tv.screens.CardAction.REMOVE_FAVORITE
import cloud.rawkoon.tv.screens.CardAction.REMOVE_FROM_LIST
import cloud.rawkoon.tv.screens.CardAction.RESUME
import cloud.rawkoon.tv.screens.CardAction.START_OVER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardActionsTest {
    private fun item(kind: MediaItem.Kind) = MediaItem(
        id = "1", kind = kind, title = "Pilot", seriesName = null, episodeLabel = null, overview = "", year = 2026,
        runtimeMinutes = null, officialRating = null, communityRating = null, posterUrl = null, thumbUrl = null,
        backdropUrl = null, logoUrl = null, progress = null, played = false,
    )

    @Test
    fun `an unstarted movie plays, and offers watched, favorite and my list`() {
        assertEquals(listOf(PLAY, MARK_WATCHED, ADD_FAVORITE, ADD_TO_LIST), cardActions(item(MediaItem.Kind.MOVIE), inList = false))
    }

    @Test
    fun `a started movie resumes or starts over`() {
        val movie = item(MediaItem.Kind.MOVIE).copy(progress = 0.4f, resumeMs = 60_000)
        assertEquals(listOf(RESUME, START_OVER, MARK_WATCHED, ADD_FAVORITE), cardActions(movie, inList = null))
    }

    @Test
    fun `the toggles follow the item's state`() {
        val movie = item(MediaItem.Kind.MOVIE).copy(played = true, favorite = true)
        assertEquals(listOf(PLAY, MARK_UNWATCHED, REMOVE_FAVORITE, REMOVE_FROM_LIST), cardActions(movie, inList = true))
    }

    @Test
    fun `an episode leads to its show and is never in my list`() {
        val episode = item(MediaItem.Kind.EPISODE).copy(seriesId = "s")
        assertEquals(listOf(PLAY, MARK_WATCHED, ADD_FAVORITE, GO_TO_SHOW), cardActions(episode, inList = true))
    }

    @Test
    fun `a show and a season don't play from their card`() {
        assertEquals(listOf(MARK_WATCHED, ADD_FAVORITE, ADD_TO_LIST), cardActions(item(MediaItem.Kind.SERIES), inList = false))
        assertEquals(listOf(MARK_WATCHED, ADD_FAVORITE, GO_TO_SHOW), cardActions(item(MediaItem.Kind.SEASON).copy(seriesId = "s"), inList = null))
    }

    @Test
    fun `a title from Rawkoon only has my list, once it is known`() {
        val title = item(MediaItem.Kind.MOVIE).copy(tmdb = TmdbRef(id = 7))
        assertEquals(listOf(REMOVE_FROM_LIST), cardActions(title, inList = true))
        assertEquals(emptyList<CardAction>(), cardActions(title, inList = null))
    }

    @Test
    fun `a collection has nothing to offer`() {
        assertEquals(emptyList<CardAction>(), cardActions(item(MediaItem.Kind.OTHER), inList = false))
    }

    @Test
    fun `my list keys a title by kind and TMDB id`() {
        assertEquals(ListKey(isMovie = false, tmdbId = 7), listKey(item(MediaItem.Kind.SERIES).copy(tmdb = TmdbRef(id = 7))))
        assertEquals(ListKey(isMovie = true, tmdbId = 9), listKey(item(MediaItem.Kind.MOVIE).copy(libraryTmdbId = 9)))
        assertNull(listKey(item(MediaItem.Kind.MOVIE)))
        assertNull(listKey(item(MediaItem.Kind.EPISODE).copy(libraryTmdbId = 9)))
    }

    @Test
    fun `an administrator can change a title's poster and background, a season's poster`() {
        assertEquals(listOf(PLAY, MARK_WATCHED, ADD_FAVORITE, CHANGE_POSTER, CHANGE_BACKGROUND), cardActions(item(MediaItem.Kind.MOVIE), inList = null, canEditArt = true))
        assertEquals(listOf(MARK_WATCHED, ADD_FAVORITE, GO_TO_SHOW, CHANGE_POSTER), cardActions(item(MediaItem.Kind.SEASON).copy(seriesId = "s"), inList = null, canEditArt = true))
    }

    @Test
    fun `an episode, a Rawkoon title and anyone else get no artwork actions`() {
        assertEquals(listOf(PLAY, MARK_WATCHED, ADD_FAVORITE), cardActions(item(MediaItem.Kind.EPISODE), inList = null, canEditArt = true))
        assertEquals(emptyList<CardAction>(), cardActions(item(MediaItem.Kind.MOVIE).copy(tmdb = TmdbRef(id = 7)), inList = null, canEditArt = true))
        assertEquals(listOf(PLAY, MARK_WATCHED, ADD_FAVORITE), cardActions(item(MediaItem.Kind.MOVIE), inList = null, canEditArt = false))
    }

    @Test
    fun `a title page's "…" has only what its buttons don't do`() {
        val extras = { item: MediaItem, admin: Boolean -> cardActions(item, inList = true, canEditArt = admin).filter { it !in ActionBar.BAR_ACTIONS } }
        assertEquals(emptyList<CardAction>(), extras(item(MediaItem.Kind.MOVIE), false))
        assertEquals(listOf(START_OVER), extras(item(MediaItem.Kind.MOVIE).copy(resumeMs = 60_000), false))
        assertEquals(listOf(CHANGE_POSTER, CHANGE_BACKGROUND), extras(item(MediaItem.Kind.SERIES), true))
        assertEquals(listOf(GO_TO_SHOW), extras(item(MediaItem.Kind.EPISODE).copy(seriesId = "s"), false))
    }
}
