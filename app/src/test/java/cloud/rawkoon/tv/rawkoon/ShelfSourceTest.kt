package cloud.rawkoon.tv.rawkoon

import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What a screen gets from the shelves: Rawkoon's answer, the saved copy, or the failure. */
class ShelfSourceTest {
    private lateinit var dir: File
    private val servers = ArrayList<ServerSocket>()

    @Before
    fun setUp() {
        dir = File.createTempFile("shelves", "").apply { delete(); mkdirs() }
    }

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun cache() = ShelfCache(File(dir, "list.json"), File(dir, "detail"))

    /** Answers every request with [status] and [body] until the test ends. */
    private fun serve(status: String, body: String = "{}"): Int {
        val server = ServerSocket(0).also { servers += it }
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: Exception) { return@thread }
                socket.use {
                    val input = it.getInputStream().bufferedReader()
                    generateSequence { input.readLine() }.takeWhile { line -> line.isNotEmpty() }.count()
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                        flush()
                    }
                }
            }
        }
        return server.localPort
    }

    /** A port nothing listens on: the connection is refused. */
    private fun closedPort() = ServerSocket(0).use { it.localPort }

    private fun source(port: Int) = ShelfSource(RawkoonDiscover(RawkoonClient("http://127.0.0.1:$port"), "token", "en"), cache())

    private val listBody = """{"shelves":[{"id":3,"name":"A shelf","entry_count":1,"in_library_count":1,"home_row":false,"visible_now":true,"order_mode":"manual","cover":{"style":"mosaic","posters":[]}}]}"""

    @Test
    fun `a 404 means no shelves, even with a saved list`() = runBlocking {
        cache().saveList(parseShelves(org.json.JSONObject(listBody)))
        assertEquals(emptyList<ShelfSummary>(), source(serve("404 Not Found", "Not Found")).shelves())
        assertNull(source(serve("404 Not Found", "Not Found")).shelf(3))
    }

    @Test
    fun `an answer is returned and saved for the next time`() = runBlocking {
        assertEquals(listOf(3), source(serve("200 OK", listBody)).shelves().map { it.id })
        assertEquals(listOf(3), cache().list()!!.map { it.id })
    }

    @Test
    fun `a network error falls back to the saved list`() = runBlocking {
        cache().saveList(parseShelves(org.json.JSONObject(listBody)))
        assertEquals(listOf(3), source(closedPort()).shelves().map { it.id })
    }

    @Test
    fun `a network error with nothing saved is thrown, not shown as no shelves`() {
        val error = runCatching { runBlocking { source(closedPort()).shelves() } }.exceptionOrNull()
        assertTrue(error.toString(), error is RawkoonException)
    }

    @Test
    fun `a server error with nothing saved is thrown too`() {
        val error = runCatching { runBlocking { source(serve("500 Internal Server Error")).shelves() } }.exceptionOrNull()
        assertTrue(error.toString(), error is RawkoonException.Http)
    }

    @Test
    fun `a refused session is never answered from the saved copy`() {
        cache().saveList(parseShelves(org.json.JSONObject(listBody)))
        val error = runCatching { runBlocking { source(serve("401 Unauthorized")).shelves() } }.exceptionOrNull()
        assertTrue(error.toString(), error is RawkoonException.Unauthorized)
    }

    @Test
    fun `a saved empty list is an answer, unlike none saved`() = runBlocking {
        cache().saveList(emptyList())
        assertEquals(emptyList<ShelfSummary>(), source(closedPort()).shelves())
    }
}
