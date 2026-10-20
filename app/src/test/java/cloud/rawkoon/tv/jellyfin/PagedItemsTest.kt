package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedItemsTest {
    private fun item(n: Int) = MediaItem(
        id = "$n", kind = MediaItem.Kind.MOVIE, title = "Title $n", seriesName = null, episodeLabel = null, overview = "", year = null,
        runtimeMinutes = null, officialRating = null, communityRating = null, posterUrl = null, thumbUrl = null, backdropUrl = null,
        logoUrl = null, progress = null, played = false,
    )

    private fun page(start: Int, count: Int) = List(count) { item(start + it) }

    @Test
    fun `positions read from the page that holds them`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 25)
        paged.put(gen, 2, page(20, 5), 25)
        assertEquals("Title 7", paged[7]?.title)
        assertEquals("Title 24", paged[24]?.title)
        assertNull(paged[15])
        assertEquals(25, paged.total)
    }

    @Test
    fun `focus near a page boundary claims the neighbouring pages once`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 100)
        assertEquals(listOf(1), paged.claimAround(position = 8, margin = 5))
        // Already on its way: not asked for twice
        assertEquals(emptyList<Int>(), paged.claimAround(position = 8, margin = 5))
        assertEquals(listOf(2, 3), paged.claimAround(position = 25, margin = 10).filter { it > 1 })
    }

    @Test
    fun `claims stay inside the list`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 15)
        assertEquals(listOf(1), paged.claimAround(position = 14, margin = 100))
    }

    @Test
    fun `a failed page can be claimed again`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 50)
        assertEquals(listOf(1), paged.claimAround(12, 0))
        paged.release(gen, 1)
        assertEquals(listOf(1), paged.claimAround(12, 0))
    }

    @Test
    fun `a response from before a restart is dropped`() {
        val paged = PagedItems(pageSize = 10)
        val old = paged.begin()
        val fresh = paged.begin()
        assertFalse(paged.put(old, 0, page(0, 10), 99))
        assertEquals(0, paged.total)
        assertTrue(paged.put(fresh, 0, page(0, 10), 10))
        assertEquals(10, paged.total)
    }
}
