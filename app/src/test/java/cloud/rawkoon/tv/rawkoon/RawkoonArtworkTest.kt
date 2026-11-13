package cloud.rawkoon.tv.rawkoon

import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonArtworkTest {
    /** Answers one request with [body] and hands back what was asked: the request line and body. */
    private fun serveOnce(body: String, status: String = "200 OK", extraHeaders: String = "", received: (String) -> Unit = {}): Int {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.use {
                it.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val head = generateSequence { input.readLine() }.takeWhile { line -> line.isNotEmpty() }.toList()
                    val length = head.firstOrNull { line -> line.startsWith("Content-Length:", ignoreCase = true) }?.substringAfter(":")?.trim()?.toInt() ?: 0
                    val sent = CharArray(length)
                    var read = 0
                    while (read < length) read += input.read(sent, read, length - read).takeIf { n -> n > 0 } ?: break
                    received(head.first() + "\n" + String(sent))
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 $status\r\n${extraHeaders}Content-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    out.flush()
                }
            }
        }
        return server.localPort
    }

    @Test
    fun `a picked image is sent as a PATCH of the override, and the Jellyfin result read back`() = runBlocking {
        var request = ""
        val port = serveOnce("""{"item":{},"artwork_sync":{"backdrop":"synced"}}""") { request = it }
        val api = RawkoonDiscover(RawkoonClient("http://127.0.0.1:$port"), "token", "en")
        val result = api.setArtwork(12, "backdrop", "https://image.tmdb.org/t/p/original/b.jpg")
        assertEquals("synced", result)
        assertTrue(request, request.startsWith("PATCH /api/library/12/overrides "))
        assertEquals("https://image.tmdb.org/t/p/original/b.jpg", JSONObject(request.substringAfter("\n")).getString("backdrop_url"))
    }

    @Test
    fun `an http address that redirects to https says so instead of losing the PATCH`() = runBlocking {
        val port = serveOnce("{}", status = "301 Moved Permanently", extraHeaders = "Location: https://rawkoon.example/api/library/12/overrides\r\n")
        val api = RawkoonDiscover(RawkoonClient("http://127.0.0.1:$port"), "token", "en")
        val error = runCatching { api.setArtwork(12, "poster", "https://image.tmdb.org/t/p/original/a.jpg") }.exceptionOrNull()
        assertTrue(error.toString(), error is RawkoonException.UseHttps)
    }
}
