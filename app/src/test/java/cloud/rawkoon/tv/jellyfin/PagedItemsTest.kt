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
        paged.release(gen, 1, nowMs = 0)
        assertEquals(listOf(1), paged.claimAround(12, 0, nowMs = PagedItems.RETRY_MS))
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

    @Test
    fun `the first page's total stands for the whole generation`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 50)
        paged.put(gen, 1, page(10, 10), 53)
        assertEquals(50, paged.total)
    }

    @Test
    fun `a failed page is left alone until the retry delay has passed`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 50)
        assertEquals(listOf(1), paged.claimAround(12, 0, nowMs = 0))
        paged.release(gen, 1, nowMs = 1_000)
        assertEquals(emptyList<Int>(), paged.claimAround(12, 0, nowMs = 2_000))
        assertEquals(listOf(1), paged.claimAround(12, 0, nowMs = 1_000 + PagedItems.RETRY_MS))
    }

    @Test
    fun `keeping only some pages drops the others so they are fetched afresh`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 50)
        paged.put(gen, 1, page(10, 10), 50)
        paged.put(gen, 2, page(20, 10), 50)
        paged.keepOnly(setOf(1))
        assertNull(paged[5])
        assertEquals("Title 15", paged[15]?.title)
        assertEquals(listOf(2), paged.claimAround(25, 0))
    }

    @Test
    fun `keeping only some pages leaves a page still on its way claimed`() {
        val paged = PagedItems(pageSize = 10)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 50)
        assertEquals(listOf(3), paged.claimAround(35, 0))
        paged.keepOnly(setOf(0))
        // Page 3 is still being fetched: it is not asked for a second time
        assertEquals(emptyList<Int>(), paged.claimAround(35, 0))
        paged.put(gen, 3, page(30, 10), 50)
        paged.keepOnly(setOf(0))
        assertNull(paged[35])
        assertEquals(listOf(3), paged.claimAround(35, 0))
    }

    @Test
    fun `pages far from the focus are evicted and fetched again when scrolled back to`() {
        val paged = PagedItems(pageSize = 10, keepRadius = 2)
        val gen = paged.begin()
        paged.put(gen, 0, page(0, 10), 200)
        for (p in 1..5) {
            paged.claimAround(p * 10, 0)
            paged.put(gen, p, page(p * 10, 10), 200)
        }
        // Focus is on page 5: pages 3 to 7 stay, 0 to 2 are gone
        assertNull(paged[5])
        assertNull(paged[25])
        assertEquals("Title 30", paged[30]?.title)
        assertEquals("Title 55", paged[55]?.title)
        assertEquals(listOf(2), paged.claimAround(25, 0))
    }
}
