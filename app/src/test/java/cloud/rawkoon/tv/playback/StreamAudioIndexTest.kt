package cloud.rawkoon.tv.playback

import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamAudioIndexTest {
    private val url = "/videos/abc/master.m3u8?DeviceId=d&MediaSourceId=abc&VideoCodec=hevc&AudioCodec=aac&AudioStreamIndex=1&SubtitleStreamIndex=4&api_key=k"

    @Test
    fun `a transcode plays the audio its URL names`() {
        assertEquals(1, streamAudioIndex(PlayMethod.TRANSCODE, url))
        assertEquals(1, streamAudioIndex(PlayMethod.DIRECT_STREAM, url))
    }

    @Test
    fun `the parameter name is matched in any casing`() {
        assertEquals(3, streamAudioIndex(PlayMethod.TRANSCODE, "/x?audioStreamIndex=3"))
    }

    @Test
    fun `a direct play picks its own track`() {
        assertNull(streamAudioIndex(PlayMethod.DIRECT_PLAY, url))
    }

    @Test
    fun `no URL or no index leaves the choice to the server's default`() {
        assertNull(streamAudioIndex(PlayMethod.TRANSCODE, null))
        assertNull(streamAudioIndex(PlayMethod.TRANSCODE, "/x?SubtitleStreamIndex=4"))
    }
}
