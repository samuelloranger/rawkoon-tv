package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.TmdbIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a shelf entry becomes on this profile, and what the Collections and Home rows get from it. */
class ShelfRowsTest {
    private fun libraryItem(id: String, tmdbId: Int, show: Boolean = false) = MediaItem(
        id = id,
        kind = if (show) MediaItem.Kind.SERIES else MediaItem.Kind.MOVIE,
        title = "Library $id",
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

    private val index = TmdbIndex.of(
        listOf(
            libraryItem("movie-1", 101),
            libraryItem("series-2", 202, show = true),
        ),
    )

    private fun entry(
        id: Int,
        tmdbId: Int,
        movie: Boolean = true,
        season: Int? = null,
        inLibrary: Boolean = false,
        group: String? = null,
        position: Int = 0,
    ) = ShelfEntry(
        id = id,
        tmdbId = tmdbId,
        isMovie = movie,
        season = season,
        title = "Entry $id",
        year = 2008,
        posterUrl = "https://image.tmdb.org/t/p/w342/$id.jpg",
        groupLabel = group,
        position = position,
        inLibrary = inLibrary,
    )

    private fun summary(id: Int, name: String = "Shelf $id", homeRow: Boolean = false, visibleNow: Boolean = true, order: ShelfOrder = ShelfOrder.MANUAL) =
        ShelfSummary(id, name, null, ShelfCover.Mosaic(emptyList()), 3, 1, homeRow, visibleNow, order)

    @Test
    fun `a title the library has is the library's own item`() {
        val cards = ShelfRows.resolve(listOf(entry(1, 101, inLibrary = true)), index, FakeText)
        val card = cards.single().card as ShelfCard.Library
        assertEquals("movie-1", card.item.id)
        assertNull(card.season)
    }

    @Test
    fun `a show's season placed on its own keeps the number it opens`() {
        val card = (ShelfRows.resolve(listOf(entry(2, 202, movie = false, season = 3, inLibrary = true)), index, FakeText).single().card as ShelfCard.Library)
        assertEquals("series-2", card.item.id)
        assertEquals(3, card.season)
    }

    @Test
    fun `a title the library has but this profile cannot see is dropped`() {
        val cards = ShelfRows.resolve(listOf(entry(3, 999, inLibrary = true), entry(4, 101, inLibrary = true)), index, FakeText)
        assertEquals(listOf(4), cards.map { it.entry.id })
    }

    @Test
    fun `a title the library lacks stays, marked Request`() {
        val card = ShelfRows.resolve(listOf(entry(5, 303, movie = false, inLibrary = false)), index, FakeText).single().card as ShelfCard.Requestable
        assertEquals("shelf-5", card.item.id)
        assertEquals(MediaItem.Kind.SERIES, card.item.kind)
        assertEquals(303, card.item.tmdb!!.id)
        assertEquals(false, card.item.tmdb!!.inLibrary)
        assertEquals(Badge("Request", Badge.Tone.NEUTRAL), card.item.tmdb!!.badge)
        assertEquals("2008", card.item.tmdb!!.subtitle)
    }

    @Test
    fun `a missing season says which season it is`() {
        val card = ShelfRows.resolve(listOf(entry(6, 404, movie = false, season = 2)), index, FakeText).single().card as ShelfCard.Requestable
        assertEquals("Season 2", card.item.tmdb!!.subtitle)
    }

    @Test
    fun `only shelves in their window and asking for a row get one, at most two`() {
        val shelves = listOf(
            summary(1, homeRow = true),
            summary(2, homeRow = false),
            summary(3, homeRow = true, visibleNow = false),
            summary(4, homeRow = true),
            summary(5, homeRow = true),
        )
        assertEquals(listOf(1, 4), ShelfRows.homeShelves(shelves).map { it.id })
    }

    @Test
    fun `a home row is named after its shelf, keeps the order and drops what it cannot show`() {
        val detail = ShelfDetail(
            summary(7, name = "Featured"),
            listOf(entry(10, 101, inLibrary = true), entry(11, 999, inLibrary = true), entry(12, 303)),
        )
        val row = ShelfRows.rows(listOf(detail), index, FakeText).single()
        assertEquals("rawkoon:shelf:7", row.id)
        assertEquals("Featured", row.title)
        assertEquals(false, row.landscape)
        assertEquals(listOf("movie-1", "shelf-12"), row.items.map { it.id })
    }

    @Test
    fun `a shelf with nothing this profile can see makes no row`() {
        val detail = ShelfDetail(summary(8), listOf(entry(13, 999, inLibrary = true)))
        assertTrue(ShelfRows.rows(listOf(detail), index, FakeText).isEmpty())
    }

    @Test
    fun `a home row keeps at most the row cap`() {
        val entries = (1..40).map { entry(100 + it, 5000 + it) }
        val row = ShelfRows.rows(listOf(ShelfDetail(summary(9), entries)), index, FakeText).single()
        assertEquals(RawkoonHome.MAX_ITEMS, row.items.size)
    }

    @Test
    fun `groups follow the order their headers first appear, with ungrouped titles under no name`() {
        val entries = listOf(
            entry(20, 101, inLibrary = true, group = "Origins", position = 0),
            entry(21, 501, group = "Origins", position = 1),
            entry(22, 502, group = null, position = 2),
            entry(23, 503, group = "The Infinity Saga", position = 3),
            entry(24, 504, group = "Origins", position = 4),
        )
        val groups = ShelfRows.groups(ShelfRows.resolve(entries, index, FakeText))
        assertEquals(listOf("Origins", null, "The Infinity Saga"), groups.map { it.first })
        assertEquals(listOf(20, 21, 24), groups[0].second.map { it.entry.id })
        assertEquals(listOf(22), groups[1].second.map { it.entry.id })
        assertEquals(listOf(23), groups[2].second.map { it.entry.id })
    }

    @Test
    fun `a shelf without headers is one group with no name`() {
        val groups = ShelfRows.groups(ShelfRows.resolve(listOf(entry(30, 601), entry(31, 602)), index, FakeText))
        assertEquals(1, groups.size)
        assertNull(groups.single().first)
        assertEquals(2, groups.single().second.size)
    }
}
