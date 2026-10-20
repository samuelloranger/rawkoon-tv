package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.data.ContinueOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HomeLayoutTest {
    private fun row(id: String) = HomeRow(id, id, emptyList(), landscape = false)

    private val rows = listOf(row("resume"), row("nextup"), row("rawkoon:upcoming"), row("latest:a"), row("latest:b"))

    private fun ids(list: List<HomeRow>) = list.map { it.id }

    @Test
    fun `nothing saved leaves the rows as they are`() {
        assertEquals(rows, arrangeRows(rows, emptyList(), emptySet()))
    }

    @Test
    fun `saved order is followed`() {
        val order = listOf("latest:b", "resume", "latest:a", "nextup", "rawkoon:upcoming")
        assertEquals(order, ids(arrangeRows(rows, order, emptySet())))
    }

    @Test
    fun `rows missing from the saved order go at the end, in their natural order`() {
        val order = listOf("latest:b", "resume")
        assertEquals(listOf("latest:b", "resume", "nextup", "rawkoon:upcoming", "latest:a"), ids(arrangeRows(rows, order, emptySet())))
    }

    @Test
    fun `saved ids that are gone or repeated are ignored`() {
        val order = listOf("latest:gone", "latest:a", "latest:a", "resume")
        assertEquals(listOf("latest:a", "resume", "nextup", "rawkoon:upcoming", "latest:b"), ids(arrangeRows(rows, order, emptySet())))
    }

    @Test
    fun `hidden rows are left out, whether or not they were moved`() {
        val order = listOf("latest:b", "resume")
        val arranged = arrangeRows(rows, order, setOf("resume", "rawkoon:upcoming"))
        assertEquals(listOf("latest:b", "nextup", "latest:a"), ids(arranged))
    }

    @Test
    fun `a hidden row that is not on the page is harmless, and hiding everything leaves nothing`() {
        assertEquals(rows, arrangeRows(rows, emptyList(), setOf("latest:gone")))
        assertTrue(arrangeRows(rows, emptyList(), rows.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `a row moves up and down and stops at the ends`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), movedBy(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), movedBy(list, "b", 1))
        assertEquals(list, movedBy(list, "a", -1))
        assertEquals(list, movedBy(list, "c", 1))
        assertEquals(list, movedBy(list, "missing", 1))
    }

    @Test
    fun `items per row default to what home always asked for`() {
        assertEquals(20, HomeFetch().limit)
    }

    @Test
    fun `next up cutoff counts back from now, and is unset when off`() {
        val now = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertNull(HomeFetch(nextUpDays = 0).nextUpCutoff(now))
        assertEquals(LocalDateTime.of(2026, 9, 20, 12, 0), HomeFetch(nextUpDays = 30).nextUpCutoff(now))
        assertEquals(LocalDateTime.of(2025, 10, 20, 12, 0), HomeFetch(nextUpDays = 365).nextUpCutoff(now))
    }

    @Test
    fun `rewatching is left unset when off`() {
        assertNull(HomeFetch().nextUpRewatching())
        assertEquals(true, HomeFetch(rewatching = true).nextUpRewatching())
    }

    private fun item(kind: MediaItem.Kind, tmdb: TmdbRef? = null) = MediaItem(
        id = "x", kind = kind, title = "T", seriesName = null, episodeLabel = null, overview = "", year = null, runtimeMinutes = null,
        officialRating = null, communityRating = null, posterUrl = null, thumbUrl = null, backdropUrl = null, logoUrl = null,
        progress = null, played = false, tmdb = tmdb,
    )

    @Test
    fun `cards play right away only in continue watching and next up, and only when asked`() {
        val play = ContinueOpen.PLAY
        assertTrue(playsRightAway(play, "resume", item(MediaItem.Kind.MOVIE)))
        assertTrue(playsRightAway(play, "resume", item(MediaItem.Kind.EPISODE)))
        assertTrue(playsRightAway(play, "nextup", item(MediaItem.Kind.EPISODE)))
        assertFalse(playsRightAway(play, "nextup", item(MediaItem.Kind.MOVIE)))
        assertFalse(playsRightAway(play, "resume", item(MediaItem.Kind.SERIES)))
        assertFalse(playsRightAway(play, "latest:a", item(MediaItem.Kind.MOVIE)))
        assertFalse(playsRightAway(ContinueOpen.PAGE, "resume", item(MediaItem.Kind.EPISODE)))
    }

    @Test
    fun `a saved row the list doesn't show keeps its place`() {
        // C was first, but the last home load didn't have it; B is moved up
        assertEquals(listOf("C", "B", "A"), mergedOrder(listOf("B", "A"), listOf("C", "A", "B")))
        assertEquals(listOf("A", "C", "B"), mergedOrder(listOf("A", "B"), listOf("A", "C", "B")))
        assertEquals(listOf("B", "A"), mergedOrder(listOf("B", "A"), emptyList()))
    }
}
