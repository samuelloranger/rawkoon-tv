package cloud.rawkoon.tv.jellyfin

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LibraryQueriesTest {
    private val user = UUID.randomUUID()

    @Test
    fun `date added reads newest first and title reads A first, until reversed`() {
        assertTrue(LibraryQuery(LibrarySort.ADDED).descending)
        assertFalse(LibraryQuery(LibrarySort.TITLE).descending)
        assertFalse(LibraryQuery(LibrarySort.ADDED, reverse = true).descending)
        assertTrue(LibraryQuery(LibrarySort.TITLE, reverse = true).descending)
    }

    @Test
    fun `only the main sort key flips, ties on the title stay A to Z`() {
        assertEquals(listOf(SortOrder.DESCENDING, SortOrder.ASCENDING), LibraryQuery(LibrarySort.YEAR).sortOrder)
        assertEquals(listOf(SortOrder.ASCENDING, SortOrder.ASCENDING), LibraryQuery(LibrarySort.YEAR, reverse = true).sortOrder)
        assertEquals(listOf(SortOrder.ASCENDING), LibraryQuery(LibrarySort.TITLE).sortOrder)
    }

    @Test
    fun `date added breaks ties on the title, so bulk additions page stably`() {
        assertEquals(listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME), LibrarySort.ADDED.keys)
        assertEquals(listOf(SortOrder.DESCENDING, SortOrder.ASCENDING), LibraryQuery(LibrarySort.ADDED).sortOrder)
    }

    @Test
    fun `the filters map to the server's`() {
        assertNull(LibraryFilter.ALL.filters)
        assertEquals(listOf(ItemFilter.IS_UNPLAYED), LibraryFilter.UNWATCHED.filters)
        assertEquals(listOf(ItemFilter.IS_FAVORITE), LibraryFilter.FAVORITES.filters)
    }

    @Test
    fun `a letter is the count of titles before it, counted from the other end when reversed`() {
        assertEquals(NameBound(lessThan = "m"), letterBound('M', descending = false))
        assertNull(letterBound('#', descending = false))
        assertEquals(NameBound(orGreater = "n"), letterBound('M', descending = true))
        assertEquals(NameBound(orGreater = "a"), letterBound('#', descending = true))
        // Reversed, Z leads the grid
        assertNull(letterBound('Z', descending = true))
    }

    @Test
    fun `a library request pages the whole library flat and a collection's children directly`() {
        val query = LibraryQuery(LibrarySort.TITLE, filter = LibraryFilter.FAVORITES)
        val library = libraryRequest(user, listOf(BaseItemKind.MOVIE), null, query, 200, 100)
        assertEquals(true, library.recursive)
        assertEquals(200, library.startIndex)
        assertEquals(100, library.limit)
        assertEquals(listOf(ItemSortBy.SORT_NAME), library.sortBy)
        assertEquals(listOf(ItemFilter.IS_FAVORITE), library.filters)
        assertEquals(true, library.enableTotalRecordCount)

        val collection = libraryRequest(user, listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), UUID.randomUUID(), query, 0, 100, NameBound(lessThan = "C"))
        assertEquals(false, collection.recursive)
        assertEquals("C", collection.nameLessThan)
        assertNull(collection.nameStartsWithOrGreater)
    }
}
