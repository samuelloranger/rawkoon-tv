package cloud.rawkoon.tv.ui

import cloud.rawkoon.tv.jellyfin.MediaItem.Kind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardShapeTest {
    @Test
    fun `a movie in a landscape row is drawn as a poster`() {
        assertFalse(wideArt(rowLandscape = true, kind = Kind.MOVIE))
    }

    @Test
    fun `episodes and other items in a landscape row keep their still`() {
        assertTrue(wideArt(true, Kind.EPISODE))
        assertTrue(wideArt(true, Kind.OTHER))
    }

    @Test
    fun `a poster row never draws stills`() {
        Kind.entries.forEach { assertFalse(wideArt(false, it)) }
    }
}
