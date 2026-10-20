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
    fun `groups with no result leave no row`() {
        val rows = Find.rows(
            mapOf(
                FindGroup.MOVIES to emptyList(),
                FindGroup.SERIES to listOf(item("s", MediaItem.Kind.SERIES)),
            ),
        ) { it.name }
        assertEquals(listOf("SERIES"), rows.map { it.id })
    }

    @Test
    fun `rows follow the group order whatever order the results arrive in`() {
        val found = linkedMapOf(
            FindGroup.COLLECTIONS to listOf(item("c", MediaItem.Kind.OTHER)),
            FindGroup.EPISODES to listOf(item("e", MediaItem.Kind.EPISODE)),
            FindGroup.MOVIES to listOf(item("m", MediaItem.Kind.MOVIE)),
        )
        assertEquals(listOf("MOVIES", "EPISODES", "COLLECTIONS"), Find.rows(found) { it.name }.map { it.id })
    }

    @Test
    fun `only the episode row is landscape and rows take the given titles`() {
        val found = FindGroup.entries.associateWith { listOf(item(it.name, MediaItem.Kind.OTHER)) }
        val rows = Find.rows(found) { "title ${it.name}" }
        assertEquals(listOf(false, false, true, false), rows.map { it.landscape })
        assertEquals("title SERIES", rows[1].title)
    }

    @Test
    fun `no results at all give no rows`() {
        assertEquals(emptyList<HomeRow>(), Find.rows(emptyMap()) { it.name })
    }
}
