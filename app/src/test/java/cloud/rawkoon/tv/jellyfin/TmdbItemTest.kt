package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbItemTest {
    private val title = MediaItem(
        id = "tmdb-tv-9", kind = MediaItem.Kind.SERIES, title = "Example Show", seriesName = null, episodeLabel = null, overview = "An overview",
        year = 2024, runtimeMinutes = null, officialRating = null, communityRating = 8.4f, posterUrl = "https://img.example.com/p.jpg?src=tmdb",
        thumbUrl = null, backdropUrl = null, logoUrl = null, progress = null, played = false,
        tmdbId = 9, inLibrary = true, badge = Badge("Available", Badge.Tone.READY), subtitle = "Tomorrow",
    )

    @Test
    fun `a title keeps its tmdb fields through the home cache`() {
        val rows = listOf(HomeRow("rawkoon:upcoming", "Coming soon", listOf(title), landscape = false))
        val back = HomeRow.listFromJson(HomeRow.listToJson(rows))
        assertEquals(rows, back)
        assertEquals(9, back.single().items.single().tmdbId)
    }

    @Test
    fun `a library item has no tmdb fields`() {
        val plain = title.copy(tmdbId = null, inLibrary = false, badge = null, subtitle = null)
        val back = MediaItem.fromJson(plain.toJson())
        assertNull(back.tmdbId)
        assertNull(back.badge)
        assertNull(back.subtitle)
        assertEquals(false, back.inLibrary)
    }

    @Test
    fun `a title's meta line is its date or year, its badge and its score`() {
        assertEquals("Tomorrow · Available · ★ 8.4", title.metaLine())
        assertEquals("2024 · Available · ★ 8.4", title.copy(subtitle = null).metaLine())
        assertEquals("2024", title.copy(subtitle = null, badge = null, communityRating = null).metaLine())
    }

    @Test
    fun `a row's signature changes with a badge or a subtitle`() {
        val before = HomeRow("r", "R", listOf(title), landscape = false)
        assertNotEquals(before.signature, before.copy(items = listOf(title.copy(badge = Badge("Pending", Badge.Tone.WAITING)))).signature)
        assertNotEquals(before.signature, before.copy(items = listOf(title.copy(subtitle = "Today"))).signature)
    }
}
