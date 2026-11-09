package cloud.rawkoon.tv.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationHostTest {
    private val server = "https://jf.example"

    @Test
    fun `requests to the Jellyfin host and port get the header, in any case`() {
        assertTrue(sendsAuthorization(server, "https://jf.example/Videos/1/stream"))
        assertTrue(sendsAuthorization("https://JF.example:443/base", "https://jf.EXAMPLE/Videos/1"))
        assertTrue(sendsAuthorization("http://10.0.0.2:8096", "http://10.0.0.2:8096/x"))
    }

    @Test
    fun `another host, port, or an unknown server gets nothing`() {
        assertFalse(sendsAuthorization(server, "https://cdn.other.example/a"))
        assertFalse(sendsAuthorization(server, "https://jf.example.evil.test/a"))
        assertFalse(sendsAuthorization(server, "https://jf.example:8443/a"))
        assertFalse(sendsAuthorization("http://10.0.0.2:8096", "http://10.0.0.2:9000/a"))
        assertFalse(sendsAuthorization(null, "https://jf.example/a"))
    }
}
