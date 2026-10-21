package cloud.rawkoon.tv.live

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The server will not serve this stream to this token ([code] 401), or has no such stream ([code] 404): retrying cannot help. */
class SseRefused(val code: Int) : IOException("stream refused: HTTP $code")

/** The server answered with something other than a stream. */
class SseFailed(val code: Int) : IOException("stream failed: HTTP $code")

/** No byte, not even a heartbeat, for [SseConnection.SILENCE_MS]. */
class SseSilent : IOException("stream went silent")

/**
 * One Server-Sent Events connection on [HttpURLConnection]: [read] returns when the server closes
 * it, throws when it fails, and ends at once (closing the socket) when its coroutine is cancelled.
 */
object SseConnection {
    /** The server pings every 15 s: three missed pings mean the connection is dead, not quiet. */
    const val SILENCE_MS = 45_000
    private const val CONNECT_MS = 10_000

    /**
     * Calls [onOpen] once the server accepts, then [onEvent] for each event, both on an IO thread.
     * Throws [SseRefused] for a 401 or 404, [SseFailed] for another status, [SseSilent] after
     * [SILENCE_MS] without a byte, and the [IOException] of a broken connection.
     */
    suspend fun read(url: String, token: String, onOpen: () -> Unit, onEvent: (SseEvent) -> Unit) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        // A blocking read cannot be interrupted, so cancelling closes the socket under it
        val closer = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            connection.connectTimeout = CONNECT_MS
            connection.readTimeout = SILENCE_MS
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Cache-Control", "no-cache")
            // A compressing proxy would hold events back in its buffer
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            when (code) {
                200 -> Unit
                401, 404 -> throw SseRefused(code)
                else -> throw SseFailed(code)
            }
            onOpen()
            val parser = SseParser()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    parser.line(line)?.let(onEvent)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw SseSilent()
        } catch (e: IOException) {
            // The closer's disconnect surfaces as an IOException: that is a cancel, not a failure
            if (!isActive) throw CancellationException("closed")
            throw e
        } finally {
            closer.cancel()
            connection.disconnect()
        }
    }
}
