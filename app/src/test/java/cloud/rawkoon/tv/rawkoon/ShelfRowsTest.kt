package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.IndexedTitle
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
            IndexedTitle("movie-1", 101, isShow = false),
            IndexedTitle("series-2", 202, isShow = true),
        ),
    )

    /** The items as the server answers for the indexed ids. */
    private val items = listOf(libraryItem("movie-1", 101), libraryItem("series-2", 202, show = true)).associateBy { it.id }

    private fun resolve(entries: List<ShelfEntry>) = ShelfRows.resolve(entries, index, items, FakeText)!!

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
        val cards = resolve(listOf(entry(1, 101, inLibrary = true)))
        val card = cards.single().card as ShelfCard.Library
        assertEquals("movie-1", card.item.id)
        assertNull(card.season)
    }

    @Test
    fun `a show's season placed on its own keeps the number it opens`() {
        val card = (resolve(listOf(entry(2, 202, movie = false, season = 3, inLibrary = true))).single().card as ShelfCard.Library)
        assertEquals("series-2", card.item.id)
        assertEquals(3, card.season)
    }

    @Test
    fun `a title the library has but this profile cannot see is dropped`() {
        val cards = resolve(listOf(entry(3, 999, inLibrary = true), entry(4, 101, inLibrary = true)))
        assertEquals(listOf(4), cards.map { it.entry.id })
    }

    @Test
    fun `a title the library lacks stays, marked Request`() {
        val card = resolve(listOf(entry(5, 303, movie = false, inLibrary = false))).single().card as ShelfCard.Requestable
        assertEquals("shelf-5", card.item.id)
        assertEquals(MediaItem.Kind.SERIES, card.item.kind)
        assertEquals(303, card.item.tmdb!!.id)
        assertEquals(false, card.item.tmdb!!.inLibrary)
        assertEquals(Badge("Request", Badge.Tone.NEUTRAL), card.item.tmdb!!.badge)
        assertEquals("2008", card.item.tmdb!!.subtitle)
    }

    @Test
    fun `a missing season says which season it is`() {
        val card = resolve(listOf(entry(6, 404, movie = false, season = 2))).single().card as ShelfCard.Requestable
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
        val row = ShelfRows.rows(listOf(detail), index, items, FakeText).single()
        assertEquals("rawkoon:shelf:7", row.id)
        assertEquals("Featured", row.title)
        assertEquals(false, row.landscape)
        assertEquals(listOf("movie-1", "shelf-12"), row.items.map { it.id })
    }

    @Test
    fun `a shelf with nothing this profile can see makes no row`() {
        val detail = ShelfDetail(summary(8), listOf(entry(13, 999, inLibrary = true)))
        assertTrue(ShelfRows.rows(listOf(detail), index, items, FakeText).isEmpty())
    }

    @Test
    fun `a home row keeps at most the row cap`() {
        val entries = (1..40).map { entry(100 + it, 5000 + it) }
        val row = ShelfRows.rows(listOf(ShelfDetail(summary(9), entries)), index, items, FakeText).single()
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
        val groups = ShelfRows.groups(resolve(entries))
        assertEquals(listOf("Origins", null, "The Infinity Saga"), groups.map { it.first })
        assertEquals(listOf(20, 21, 24), groups[0].second.map { it.entry.id })
        assertEquals(listOf(22), groups[1].second.map { it.entry.id })
        assertEquals(listOf(23), groups[2].second.map { it.entry.id })
    }

    @Test
    fun `a shelf without headers is one group with no name`() {
        val groups = ShelfRows.groups(resolve(listOf(entry(30, 601), entry(31, 602))))
        assertEquals(1, groups.size)
        assertNull(groups.single().first)
        assertEquals(2, groups.single().second.size)
    }

    @Test
    fun `an item that changed since the index was built is the one shown`() {
        val watched = libraryItem("movie-1", 101).copy(played = true)
        val card = ShelfRows.resolve(listOf(entry(1, 101, inLibrary = true)), index, mapOf("movie-1" to watched, "series-2" to items.getValue("series-2")), FakeText)!!.single().card
        assertTrue(card.item.played)
    }

    @Test
    fun `an indexed id the account no longer returns follows the same rule as a missing one`() {
        val none = emptyMap<String, MediaItem>()
        val cards = ShelfRows.resolve(listOf(entry(1, 101, inLibrary = true), entry(2, 101, inLibrary = false)), index, none, FakeText)!!
        assertEquals(listOf(2), cards.map { it.entry.id })
    }

    @Test
    fun `without an index nothing is resolved, so in-library titles are not dropped as unseen`() {
        val entries = listOf(entry(1, 101, inLibrary = true), entry(2, 303))
        assertNull(ShelfRows.resolve(entries, null, emptyMap(), FakeText))
        assertTrue(ShelfRows.libraryIds(entries, null).isEmpty())
        assertTrue(ShelfRows.rows(listOf(ShelfDetail(summary(1), entries)), null, emptyMap(), FakeText).isEmpty())
    }

    @Test
    fun `a built but empty index is an answer, so in-library titles are dropped and requestable ones stay`() {
        val empty = TmdbIndex.of(emptyList())
        val cards = ShelfRows.resolve(listOf(entry(1, 101, inLibrary = true), entry(2, 303)), empty, emptyMap(), FakeText)!!
        assertEquals(listOf(2), cards.map { it.entry.id })
    }

    @Test
    fun `home rows kept from before take the current state of their library cards`() {
        val row = ShelfRows.rows(listOf(ShelfDetail(summary(7), listOf(entry(10, 101, inLibrary = true), entry(12, 303)))), index, items, FakeText)
        assertEquals(listOf("movie-1"), ShelfRows.libraryIds(row))
        val refreshed = ShelfRows.refreshed(row, mapOf("movie-1" to libraryItem("movie-1", 101).copy(played = true)))
        assertEquals(listOf(true, false), refreshed.single().items.map { it.played })
    }
}
