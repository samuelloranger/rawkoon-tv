package cloud.rawkoon.tv.screens.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRetryTest {
    @Test
    fun `a dropped or timed-out connection is retried`() {
        assertTrue(isNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(isNetworkError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun `a bad status or a decoder failure is not`() {
        assertFalse(isNetworkError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(isNetworkError(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }
}
