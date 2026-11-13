package cloud.rawkoon.tv.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The index shelves resolve against: keyed by TMDB id and movie or show, and saved as JSON. */
class TmdbIndexTest {
    private fun item(id: String, tmdbId: Int?, kind: MediaItem.Kind = MediaItem.Kind.MOVIE) = MediaItem(
        id = id,
        kind = kind,
        title = "Title $id",
        seriesName = null,
        episodeLabel = null,
        overview = "",
        year = 2000,
        runtimeMinutes = null,
        officialRating = null,
        communityRating = null,
        posterUrl = null,
        thumbUrl = null,
        backdropUrl = null,
        logoUrl = null,
        progress = null,
        played = false,
        libraryTmdbId = tmdbId,
    )

    @Test
    fun `titles are keyed by their TMDB id and whether they are a show`() {
        val index = TmdbIndex.of(
            listOf(
                item("movie-1", 101),
                item("series-2", 101, MediaItem.Kind.SERIES),
                item("movie-3", null),
                item("season-4", 101, MediaItem.Kind.SEASON),
            ),
        )
        assertEquals(2, index.size)
        assertEquals("movie-1", index.item(101, isShow = false)!!.id)
        assertEquals("series-2", index.item(101, isShow = true)!!.id)
        assertNull(index.item(999, isShow = false))
    }

    @Test
    fun `an index reads back as it was saved`() {
        val index = TmdbIndex.of(listOf(item("movie-1", 101), item("series-2", 202, MediaItem.Kind.SERIES)))
        val roundTrip = TmdbIndex.fromJson(index.toJson().toString())!!
        assertEquals(2, roundTrip.size)
        assertEquals("movie-1", roundTrip.item(101, isShow = false)!!.id)
        assertEquals("series-2", roundTrip.item(202, isShow = true)!!.id)
    }

    @Test
    fun `a saved index from another version or a broken file is not used`() {
        val saved = JSONObject(TmdbIndex.of(listOf(item("movie-1", 101))).toJson().toString())
        assertNull(TmdbIndex.fromJson(saved.put("version", 99).toString()))
        assertNull(TmdbIndex.fromJson("{not json"))
        assertNull(TmdbIndex.fromJson(JSONObject().put("version", 1).toString()))
    }
}
