package cloud.rawkoon.tv.jellyfin

import java.net.ServerSocket
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferedBodiesTest {
    /** Serves one response whose body follows its headers after a pause, as a slow network would. */
    private fun serveSlowBody(body: String): Int {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.use {
                it.accept().use { socket ->
                    socket.getInputStream().read(ByteArray(8192))
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    Thread.sleep(300)
                    out.write(body.toByteArray())
                    out.flush()
                }
            }
        }
        return server.localPort
    }

    @Test
    fun `the whole body is in memory once the call returns`() {
        val body = "{\"Items\":[" + (1..200).joinToString(",") { "{\"Id\":\"$it\"}" } + "]}"
        val port = serveSlowBody(body)
        val client = OkHttpClient.Builder().addInterceptor(BufferedBodies).build()
        client.newCall(Request.Builder().url("http://127.0.0.1:$port/Items").build()).execute().use { response ->
            // Nothing left to read from the network: the caller can read it on any thread
            assertEquals(body.length.toLong(), response.body.source().buffer.size)
            assertEquals(body, response.body.string())
        }
    }
}
