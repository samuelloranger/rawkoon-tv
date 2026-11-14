package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
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
    fun `home lists every shelf showing now, in order, as one row`() {
        val shelves = listOf(summary(1, name = "Horror"), summary(2, visibleNow = false), summary(3, name = "Disney"))
        val row = ShelfRows.homeRow(shelves, "Shelves")!!
        assertEquals(ShelfRows.HOME_ROW_ID, row.id)
        assertEquals("Shelves", row.title)
        assertTrue(row.landscape)
        assertEquals(listOf("Horror", "Disney"), row.items.map { it.title })
        assertEquals(listOf(1, 3), ShelfRows.shelvesOf(row).map { it.id })
    }

    @Test
    fun `no shelf showing now makes no row`() {
        assertNull(ShelfRows.homeRow(listOf(summary(1, visibleNow = false)), "Shelves"))
        assertNull(ShelfRows.homeRow(emptyList(), "Shelves"))
    }

    @Test
    fun `the shelves survive the home cache and a change rebuilds the row`() {
        val row = ShelfRows.homeRow(listOf(summary(1, name = "Horror")), "Shelves")!!
        val cached = HomeRow.listFromJson(HomeRow.listToJson(listOf(row))).single()
        assertEquals(ShelfRows.shelvesOf(row), ShelfRows.shelvesOf(cached))
        val renamed = ShelfRows.homeRow(listOf(summary(1, name = "Real horror")), "Shelves")!!
        assertTrue(row.signature != renamed.signature)
    }

    @Test
    fun `another row carries no shelves`() {
        assertTrue(ShelfRows.shelvesOf(HomeRow("resume", "Continue", emptyList(), false)).isEmpty())
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
    }

    @Test
    fun `a built but empty index is an answer, so in-library titles are dropped and requestable ones stay`() {
        val empty = TmdbIndex.of(emptyList())
        val cards = ShelfRows.resolve(listOf(entry(1, 101, inLibrary = true), entry(2, 303)), empty, emptyMap(), FakeText)!!
        assertEquals(listOf(2), cards.map { it.entry.id })
    }
}
