package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaInfoTest {
    private fun file(resolution: String?, codec: String?) = MediaInfo(resolution, codec, "Français 5.1")

    @Test
    fun `a show whose files share a resolution and codec names both`() {
        assertEquals(listOf("1080p", "HEVC"), MediaInfo.span(listOf(file("1080p", "HEVC"), file("1080p", "HEVC")))?.chips())
    }

    @Test
    fun `mixed resolutions read as a range, lowest first, and mixed codecs are left out`() {
        val files = listOf(file("4K", "HEVC"), file("SD", "H264"), file("1080p", "HEVC"))
        assertEquals(listOf("SD – 4K"), MediaInfo.span(files)?.chips())
    }

    @Test
    fun `a show with no known files has no chips`() {
        assertNull(MediaInfo.span(emptyList()))
        assertNull(MediaInfo.span(listOf(file(null, null))))
    }

    @Test
    fun `one resolution with mixed codecs keeps the resolution alone`() {
        assertEquals(listOf("1080p"), MediaInfo.span(listOf(file("1080p", "HEVC"), file("1080p", "H264")))?.chips())
    }
}
