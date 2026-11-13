package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteArtTest {
    @Test
    fun `a TMDB original is previewed at a small size`() {
        val art = RemoteArt("https://image.tmdb.org/t/p/original/a.jpg", null, "TheMovieDb", 2000, 3000, "en")
        assertEquals("https://image.tmdb.org/t/p/w342/a.jpg", art.previewUrl("w342"))
    }

    @Test
    fun `a provider's own thumbnail is used as it is`() {
        val art = RemoteArt("https://assets.example/full.jpg", "https://assets.example/preview.jpg", "Fanart", null, null, null)
        assertEquals("https://assets.example/preview.jpg", art.previewUrl("w342"))
    }

    @Test
    fun `details give the size and language that are known`() {
        assertEquals("2000 × 3000 · EN", RemoteArt("u", null, null, 2000, 3000, "en").details())
        assertEquals("FR", RemoteArt("u", null, null, null, 3000, "fr").details())
        assertEquals("", RemoteArt("u", null, null, null, null, null).details())
    }
}
