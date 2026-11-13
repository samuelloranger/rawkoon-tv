package cloud.rawkoon.tv.jellyfin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The paging of the index read, against a fake library of `size` titles. */
class TmdbIndexBuildTest {
    private class Library(val size: Int, val reportTotal: Boolean = true) {
        val asked = ArrayList<Pair<Int, Int>>()

        fun page(start: Int, limit: Int): IndexPage {
            asked += start to limit
            val titles = (start until minOf(size, start + limit)).map { IndexedTitle("id-$it", 1000 + it, isShow = false) }
            // As Jellyfin does: the total comes with the first page only
            return IndexPage(titles, if (reportTotal && start == 0) size else null)
        }
    }

    @Test
    fun `pages are read until the first page's total is reached`() = runBlocking {
        val library = Library(1200)
        val index = buildTmdbIndex(pageSize = 500, page = library::page)
        assertEquals(1200, index.size)
        assertEquals(listOf(0 to 500, 500 to 500, 1000 to 500), library.asked)
    }

    @Test
    fun `a total that lands on a page boundary needs no extra request`() = runBlocking {
        val library = Library(1000)
        buildTmdbIndex(pageSize = 500, page = library::page)
        assertEquals(2, library.asked.size)
    }

    @Test
    fun `without a total it stops at the first empty page`() = runBlocking {
        val library = Library(700, reportTotal = false)
        val index = buildTmdbIndex(pageSize = 500, page = library::page)
        assertEquals(700, index.size)
        assertEquals(listOf(0, 500, 700), library.asked.map { it.first })
    }

    @Test
    fun `an empty library is one request and an empty index`() = runBlocking {
        val library = Library(0)
        assertEquals(0, buildTmdbIndex(page = library::page).size)
        assertEquals(1, library.asked.size)
    }

    @Test
    fun `the read stops at the ceiling even when the server claims more`() = runBlocking {
        val library = Library(5000)
        val index = buildTmdbIndex(pageSize = 400, maxItems = 1000, page = library::page)
        assertEquals(1000, index.size)
        // The last page is trimmed to what the ceiling leaves
        assertEquals(listOf(0 to 400, 400 to 400, 800 to 200), library.asked)
    }
}
