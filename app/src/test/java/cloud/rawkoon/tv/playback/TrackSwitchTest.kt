package cloud.rawkoon.tv.playback

import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSwitchTest {
    private fun sub(index: Int, delivery: SubtitleDeliveryMethod, url: String? = null, codec: String = "srt") =
        Track(index, "sub $index", "eng", codec, delivery == SubtitleDeliveryMethod.EXTERNAL, delivery, url)

    private val subtitles = listOf(
        sub(2, SubtitleDeliveryMethod.EMBED),
        sub(3, SubtitleDeliveryMethod.EXTERNAL, "http://x/3.srt"),
        sub(4, SubtitleDeliveryMethod.EXTERNAL, "http://x/4.srt"),
        sub(5, SubtitleDeliveryMethod.EXTERNAL, null),
        sub(6, SubtitleDeliveryMethod.EXTERNAL, "http://x/6.pgs", codec = "pgssub"),
        sub(7, SubtitleDeliveryMethod.ENCODE),
    )

    private fun transcode(subtitleIndex: Int) = Stream(
        itemId = "i", mediaSourceId = "m", playSessionId = "p", url = "u", method = PlayMethod.TRANSCODE,
        audio = emptyList(), subtitles = subtitles, audioIndex = 1, subtitleIndex = subtitleIndex, durationMs = null,
    )

    @Test
    fun `a transcode switches to a side-loaded subtitle`() {
        assertTrue(canSwitchTracks(transcode(-1), 1, 3))
        assertTrue(canSwitchTracks(transcode(3), 1, 4))
    }

    @Test
    fun `a transcode asks the server for a subtitle it cannot side-load`() {
        assertFalse(canSwitchTracks(transcode(-1), 1, 2))
        assertFalse(canSwitchTracks(transcode(-1), 1, 5))
        assertFalse(canSwitchTracks(transcode(-1), 1, 6))
        assertFalse(canSwitchTracks(transcode(-1), 1, 7))
    }

    @Test
    fun `burned-in subtitles can only change through the server`() {
        assertFalse(canSwitchTracks(transcode(7), 1, 3))
        assertFalse(canSwitchTracks(transcode(7), 1, -1))
        assertTrue(canSwitchTracks(transcode(7), 1, 7))
    }

    @Test
    fun `a transcode keeps its audio`() {
        assertFalse(canSwitchTracks(transcode(-1), 2, 3))
        assertTrue(canSwitchTracks(transcode(3), 1, -1))
    }

    @Test
    fun `direct play switches anything but burned-in subtitles`() {
        val direct = transcode(-1).copy(method = PlayMethod.DIRECT_PLAY)
        assertTrue(canSwitchTracks(direct, 2, 2))
        assertTrue(canSwitchTracks(direct, 1, -1))
        assertFalse(canSwitchTracks(direct, 1, 7))
    }
}
