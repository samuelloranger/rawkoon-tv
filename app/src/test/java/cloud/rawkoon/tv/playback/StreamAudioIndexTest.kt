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

    @Test
    fun `the stream URL beats both the requested track and the default`() {
        // The server default says 3 while the URL still carries 1: track 1 is what plays
        assertEquals(1, playedAudioIndex(PlayMethod.TRANSCODE, url, requested = null, default = 3))
        assertEquals(1, playedAudioIndex(PlayMethod.DIRECT_STREAM, url, requested = 2, default = 3))
    }

    @Test
    fun `without a URL index the requested track beats the default`() {
        assertEquals(2, playedAudioIndex(PlayMethod.DIRECT_PLAY, url, requested = 2, default = 3))
        assertEquals(2, playedAudioIndex(PlayMethod.TRANSCODE, null, requested = 2, default = 3))
        assertEquals(3, playedAudioIndex(PlayMethod.DIRECT_PLAY, null, requested = null, default = 3))
    }
}
