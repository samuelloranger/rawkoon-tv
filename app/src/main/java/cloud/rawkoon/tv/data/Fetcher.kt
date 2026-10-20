package cloud.rawkoon.tv.data

import android.content.Context
import android.net.Uri
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opens http(s) URLs. Blocking: call from a worker thread. The context is kept in the signature for
 * the callers that build one; nothing here needs it.
 */
@Suppress("UNUSED_PARAMETER")
class Fetcher(context: Context) {
    /** The stream of [url]; closing it also disconnects an http(s) connection. */
    fun open(url: String): InputStream {
        require(Uri.parse(url).scheme.let { it == "http" || it == "https" }) { "Not an http(s) URL: $url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            return object : FilterInputStream(connection.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        connection.disconnect()
                    }
                }
            }
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }

    /** The body as text, or null on any failure: callers fall back to their cache. */
    fun text(url: String): String? = try {
        open(url).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TIMEOUT_MS = 8000
    }
}
