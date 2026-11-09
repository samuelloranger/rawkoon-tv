package cloud.rawkoon.tv.screens.player

import androidx.media3.common.PlaybackException

/** A dropped or timed-out connection: the stream is fine, so trying it again beats a transcode or giving up. */
internal fun isNetworkError(errorCode: Int) =
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

internal const val MAX_NETWORK_RETRIES = 3
internal const val NETWORK_RETRY_DELAY_MS = 2_000L
