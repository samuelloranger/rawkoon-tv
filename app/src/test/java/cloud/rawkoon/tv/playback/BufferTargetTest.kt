package cloud.rawkoon.tv.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferTargetTest {
    @Test
    fun `a small heap buffers a third of it`() {
        assertEquals(64 * 1024 * 1024, bufferTargetBytes(192))
        assertEquals(85 * 1024 * 1024, bufferTargetBytes(256))
    }

    @Test
    fun `a large heap keeps media3's default`() {
        assertEquals(C.LENGTH_UNSET, bufferTargetBytes(384))
        assertEquals(C.LENGTH_UNSET, bufferTargetBytes(512))
    }
}
