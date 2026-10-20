package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class HomeCacheTest {
    private val episode = MediaItem(
        id = "e1", kind = MediaItem.Kind.EPISODE, title = "Pilot", seriesName = "Show", episodeLabel = "S1 · E1",
        overview = "An overview", year = null, runtimeMinutes = 52, officialRating = null, communityRating = 8.4f,
        posterUrl = "https://jf/p", thumbUrl = "https://jf/t", backdropUrl = null, logoUrl = "https://jf/l",
        progress = 0.25f, played = false, seriesId = "s1", seasonId = "se1", seasonNumber = 1, episodeNumber = 1, favorite = true,
    )

    @Test
    fun `home rows survive a round trip through the cache`() {
        val rows = listOf(HomeRow("resume", "Continue watching", listOf(episode), landscape = true))
        assertEquals(rows, HomeRow.listFromJson(HomeRow.listToJson(rows)))
    }

    @Test
    fun `a row's signature changes with progress`() {
        val before = HomeRow("resume", "Continue watching", listOf(episode), landscape = true)
        val after = before.copy(items = listOf(episode.copy(progress = 0.5f)))
        assertNotEquals(before.signature, after.signature)
    }

    @Test
    fun `minutes left follow the progress`() {
        assertEquals(39, episode.minutesLeft())
    }

    @Test
    fun `audio languages are named in the app's language`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRENCH)
            assertEquals("Français", MediaInfo.languageName("fre"))
            assertEquals("Anglais", MediaInfo.languageName("eng"))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("French", MediaInfo.languageName("fra"))
            assertNull(MediaInfo.languageName("und"))
            assertNull(MediaInfo.languageName(null))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
