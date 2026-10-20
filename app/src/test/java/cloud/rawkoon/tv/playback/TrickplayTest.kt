package cloud.rawkoon.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TrickplayTest {
    // 320x180 thumbnails, 10 per row and 10 rows per tile, one every 10 seconds
    private val trickplay = Trickplay(320, 180, 10, 10, 250, 10_000) { "tile-$it" }

    @Test
    fun `the first thumbnail is at the top left of the first tile`() {
        assertEquals(Triple("tile-0", 0, 0), trickplay.locate(0))
    }

    @Test
    fun `thumbnails fill a tile row by row`() {
        // 125 s is thumbnail 12: second row, third column
        assertEquals(Triple("tile-0", 2 * 320, 1 * 180), trickplay.locate(125_000))
    }

    @Test
    fun `the hundred-and-first thumbnail starts the next tile`() {
        assertEquals(Triple("tile-1", 0, 0), trickplay.locate(1_000_000))
    }

    @Test
    fun `positions past the end stay on the last thumbnail`() {
        assertEquals(Triple("tile-2", 9 * 320, 4 * 180), trickplay.locate(99_000_000))
    }
}
