package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindTest {
    private fun item(id: String, kind: MediaItem.Kind) = MediaItem(
        id = id, kind = kind, title = id, seriesName = null, episodeLabel = null, overview = "", year = null,
        runtimeMinutes = null, officialRating = null, communityRating = null, posterUrl = null, thumbUrl = null,
        backdropUrl = null, logoUrl = null, progress = null, played = false,
    )

    @Test
    fun `a term shorter than two characters starts no search`() {
        assertNull(Find.term(""))
        assertNull(Find.term("a"))
        assertNull(Find.term("  a  "))
    }

    @Test
    fun `a term is trimmed before it is sent`() {
        assertEquals("ab", Find.term(" ab "))
        assertEquals("two words", Find.term("two words"))
    }

    @Test
    fun `groups with no result are dropped`() {
        val found = Find.nonEmpty(
            mapOf(
                FindGroup.MOVIES to emptyList(),
                FindGroup.SERIES to listOf(item("s", MediaItem.Kind.SERIES)),
            ),
        )
        assertEquals(listOf(FindGroup.SERIES), found.keys.toList())
    }

    @Test
    fun `groups follow the group order whatever order the results arrive in`() {
        val found = linkedMapOf(
            FindGroup.COLLECTIONS to listOf(item("c", MediaItem.Kind.OTHER)),
            FindGroup.EPISODES to listOf(item("e", MediaItem.Kind.EPISODE)),
            FindGroup.MOVIES to listOf(item("m", MediaItem.Kind.MOVIE)),
        )
        assertEquals(
            listOf(FindGroup.MOVIES, FindGroup.EPISODES, FindGroup.COLLECTIONS),
            Find.nonEmpty(found).keys.toList(),
        )
    }

    @Test
    fun `a group keeps its items`() {
        val items = listOf(item("a", MediaItem.Kind.MOVIE), item("b", MediaItem.Kind.MOVIE))
        assertEquals(items, Find.nonEmpty(mapOf(FindGroup.MOVIES to items))[FindGroup.MOVIES])
    }

    @Test
    fun `only the episode group is landscape`() {
        assertEquals(listOf(false, false, true, false), FindGroup.entries.map { it.landscape })
    }

    @Test
    fun `no results at all give no groups`() {
        assertEquals(emptyMap<FindGroup, List<MediaItem>>(), Find.nonEmpty(emptyMap()))
    }
}
