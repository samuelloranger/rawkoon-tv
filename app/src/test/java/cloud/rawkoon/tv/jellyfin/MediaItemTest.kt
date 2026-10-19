package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.rawkoon.DeviceCode
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemTest {
    private fun item(kind: MediaItem.Kind) = MediaItem(
        id = "1", kind = kind, title = "Pilot", seriesName = if (kind == MediaItem.Kind.EPISODE) "Show" else null,
        episodeLabel = if (kind == MediaItem.Kind.EPISODE) "S1 · E3" else null, overview = "", year = 2026, runtimeMinutes = 97,
        officialRating = "CA-14A", communityRating = 7.25f, posterUrl = null, thumbUrl = null, backdropUrl = null, logoUrl = null,
        progress = null, played = false,
    )

    @Test
    fun `a movie's meta line has year, length, rating and score`() {
        assertEquals("2026 · 1 h 37 min · CA-14A · ★ 7.3", item(MediaItem.Kind.MOVIE).metaLine())
    }

    @Test
    fun `an episode's meta line names the episode, and the header names the show`() {
        val episode = item(MediaItem.Kind.EPISODE).copy(runtimeMinutes = 52)
        assertEquals("S1 · E3 · Pilot · 52 min", episode.metaLine())
        assertEquals("Show", episode.headerTitle)
    }

    @Test
    fun `an eight-letter code is shown in two halves`() {
        assertEquals("ABCD-1234", DeviceCode("d", "ABCD1234", "https://x", null, 5).displayCode)
        assertEquals("AB-12", DeviceCode("d", "AB-12", "https://x", null, 5).displayCode)
    }
}
