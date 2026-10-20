package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TmdbItemTest {
    private val title = MediaItem(
        id = "tmdb-tv-9", kind = MediaItem.Kind.SERIES, title = "Example Show", seriesName = null, episodeLabel = null, overview = "An overview",
        year = 2024, runtimeMinutes = null, officialRating = null, communityRating = 8.4f, posterUrl = "https://img.example.com/p.jpg?src=tmdb",
        thumbUrl = null, backdropUrl = null, logoUrl = null, progress = null, played = false,
        tmdb = TmdbRef(9, inLibrary = true, badge = Badge("Available", Badge.Tone.READY), subtitle = "Tomorrow", airDate = LocalDate.of(2026, 10, 20)),
    )

    @Test
    fun `a title keeps its tmdb fields through the home cache`() {
        val rows = listOf(HomeRow("rawkoon:upcoming", "Coming soon", listOf(title), landscape = false))
        val back = HomeRow.listFromJson(HomeRow.listToJson(rows))
        assertEquals(rows, back)
        assertEquals(9, back.single().items.single().tmdb!!.id)
        assertEquals(LocalDate.of(2026, 10, 20), back.single().items.single().tmdb!!.airDate)
    }

    @Test
    fun `a library item has no tmdb fields`() {
        val plain = title.copy(tmdb = null)
        val back = MediaItem.fromJson(plain.toJson())
        assertNull(back.tmdb)
        assertEquals(plain, back)
    }

    @Test
    fun `a title's meta line is its date or year, its badge and its score`() {
        assertEquals("Tomorrow · Available · ★ 8.4", title.metaLine())
        assertEquals("2024 · Available · ★ 8.4", title.copy(tmdb = title.tmdb!!.copy(subtitle = null)).metaLine())
        assertEquals("2024", title.copy(tmdb = title.tmdb!!.copy(subtitle = null, badge = null), communityRating = null).metaLine())
    }

    @Test
    fun `a row's signature changes with a badge or a subtitle`() {
        val before = HomeRow("r", "R", listOf(title), landscape = false)
        assertNotEquals(before.signature, before.copy(items = listOf(title.copy(tmdb = title.tmdb!!.copy(badge = Badge("Pending", Badge.Tone.WAITING))))).signature)
        assertNotEquals(before.signature, before.copy(items = listOf(title.copy(tmdb = title.tmdb!!.copy(subtitle = "Today")))).signature)
    }

    @Test
    fun `a card that entered the library changes its row's signature`() {
        val before = HomeRow("r", "R", listOf(title.copy(tmdb = title.tmdb!!.copy(inLibrary = false))), landscape = false)
        assertNotEquals(before.signature, before.copy(items = listOf(title.copy(tmdb = title.tmdb!!.copy(inLibrary = true)))).signature)
    }
}
