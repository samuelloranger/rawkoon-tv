package cloud.rawkoon.tv.clips

import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipKeyTest {
    private fun item(kind: MediaItem.Kind, tmdbId: Int? = null, season: Int? = null, seriesId: String? = null, tmdb: TmdbRef? = null) = MediaItem(
        id = "1", kind = kind, title = "T", seriesName = null, episodeLabel = null, overview = "", year = null, runtimeMinutes = null,
        officialRating = null, communityRating = null, posterUrl = null, thumbUrl = null, backdropUrl = null, logoUrl = null,
        progress = null, played = false, seriesId = seriesId, seasonNumber = season, tmdb = tmdb, libraryTmdbId = tmdbId,
    )

    @Test
    fun `a movie plays its own clip`() {
        assertEquals(ClipKey(10, show = false, season = null), ClipKey.of(item(MediaItem.Kind.MOVIE, tmdbId = 10), null))
        assertEquals("api/clips/file?tmdbId=10&type=movie", ClipKey.of(item(MediaItem.Kind.MOVIE, tmdbId = 10), null)!!.path())
    }

    @Test
    fun `a series plays its first season`() {
        val key = ClipKey.of(item(MediaItem.Kind.SERIES, tmdbId = 20), null)
        assertEquals(ClipKey(20, show = true, season = 1), key)
        assertEquals("api/clips/file?tmdbId=20&type=show&season=1", key!!.path())
    }

    @Test
    fun `a season or episode plays its own season, by the show's id`() {
        assertEquals(ClipKey(20, true, 3), ClipKey.of(item(MediaItem.Kind.SEASON, season = 3, seriesId = "s"), 20))
        assertEquals(ClipKey(20, true, 2), ClipKey.of(item(MediaItem.Kind.EPISODE, season = 2, seriesId = "s"), 20))
        assertEquals("s", ClipKey.seriesToResolve(item(MediaItem.Kind.EPISODE, season = 2, seriesId = "s")))
        assertNull(ClipKey.seriesToResolve(item(MediaItem.Kind.MOVIE, tmdbId = 1)))
    }

    @Test
    fun `no clip without the ids it needs, for specials, or for cards from TMDB`() {
        assertNull(ClipKey.of(item(MediaItem.Kind.MOVIE), null))
        assertNull(ClipKey.of(item(MediaItem.Kind.EPISODE, season = 2, seriesId = "s"), null))
        assertNull(ClipKey.of(item(MediaItem.Kind.EPISODE, season = 0, seriesId = "s"), 20))
        assertNull(ClipKey.of(item(MediaItem.Kind.EPISODE, seriesId = "s"), 20))
        assertNull(ClipKey.of(item(MediaItem.Kind.OTHER, tmdbId = 5), 5))
        val discovery = item(MediaItem.Kind.MOVIE, tmdbId = 10, tmdb = TmdbRef(10))
        assertNull(ClipKey.of(discovery, null))
        assertNull(ClipKey.seriesToResolve(item(MediaItem.Kind.EPISODE, seriesId = "s", tmdb = TmdbRef(1))))
    }

    @Test
    fun `the TMDB id survives the home cache, and rows cached without it still load`() {
        val movie = item(MediaItem.Kind.MOVIE, tmdbId = 10)
        val rows = listOf(HomeRow("latest", "Latest", listOf(movie), landscape = false))
        assertEquals(rows, HomeRow.listFromJson(HomeRow.listToJson(rows)))
        val old = movie.toJson().apply { remove("libraryTmdbId") }
        assertNull(MediaItem.fromJson(old).libraryTmdbId)
    }

    @Test
    fun `a clip starts only when focus has rested and the file is ready, for the card still focused`() {
        val intent = ClipIntent()
        intent.focus("a")
        assertFalse(intent.downloaded("a"))
        assertTrue(intent.dwellElapsed())

        intent.focus("b")
        assertFalse(intent.dwellElapsed())
        assertTrue(intent.downloaded("b"))

        intent.focus("c")
        assertFalse(intent.downloaded("b"))
        intent.stop()
        assertFalse(intent.dwellElapsed())
        assertFalse(intent.downloaded("c"))
    }
}
