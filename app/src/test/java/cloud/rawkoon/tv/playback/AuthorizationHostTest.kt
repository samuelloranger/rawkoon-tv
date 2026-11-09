package cloud.rawkoon.tv.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationHostTest {
    @Test
    fun `the Jellyfin host gets the header, in any case`() {
        assertTrue(sendsAuthorization("jf.example", "jf.example"))
        assertTrue(sendsAuthorization("JF.example", "jf.EXAMPLE"))
    }

    @Test
    fun `another host, a missing host or an unknown server gets nothing`() {
        assertFalse(sendsAuthorization("jf.example", "cdn.other.example"))
        assertFalse(sendsAuthorization("jf.example", "jf.example.evil.test"))
        assertFalse(sendsAuthorization("jf.example", null))
        assertFalse(sendsAuthorization(null, "jf.example"))
    }
}
