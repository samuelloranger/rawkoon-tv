package cloud.rawkoon.tv.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The index shelves resolve against: keyed by TMDB id and movie or show, and saved as JSON. */
class TmdbIndexTest {
    private fun title(id: String, tmdbId: Int?, show: Boolean = false) = IndexedTitle(id, tmdbId, show)

    @Test
    fun `titles are keyed by their TMDB id and whether they are a show`() {
        val index = TmdbIndex.of(listOf(title("movie-1", 101), title("series-2", 101, show = true), title("movie-3", null)))
        assertEquals(2, index.size)
        assertEquals("movie-1", index.itemId(101, isShow = false))
        assertEquals("series-2", index.itemId(101, isShow = true))
        assertNull(index.itemId(999, isShow = false))
    }

    @Test
    fun `an index reads back as it was saved`() {
        val index = TmdbIndex.of(listOf(title("movie-1", 101), title("series-2", 202, show = true)))
        val roundTrip = TmdbIndex.fromJson(index.toJson().toString())!!
        assertEquals(2, roundTrip.size)
        assertEquals("movie-1", roundTrip.itemId(101, isShow = false))
        assertEquals("series-2", roundTrip.itemId(202, isShow = true))
    }

    @Test
    fun `a saved index from another version or a broken file is not used`() {
        val saved = JSONObject(TmdbIndex.of(listOf(title("movie-1", 101))).toJson().toString())
        assertNull(TmdbIndex.fromJson(saved.put("version", 99).toString()))
        assertNull(TmdbIndex.fromJson("{not json"))
        assertNull(TmdbIndex.fromJson(JSONObject().put("version", 1).toString()))
    }
}
