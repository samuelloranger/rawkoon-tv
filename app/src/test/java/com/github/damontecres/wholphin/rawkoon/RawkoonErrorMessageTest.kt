package com.github.damontecres.wholphin.rawkoon.ui

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.RawkoonException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RawkoonErrorMessageTest {
    @Test
    fun each_error_has_a_distinct_explanation() {
        assertEquals(R.string.rawkoon_error_not_configured, RawkoonException.NotConfigured().messageRes())
        assertEquals(R.string.rawkoon_error_credentials, RawkoonException.InvalidCredentials().messageRes())
        assertEquals(R.string.rawkoon_error_rate_limited, RawkoonException.RateLimited().messageRes())
        assertEquals(R.string.rawkoon_error_quick_connect, RawkoonException.QuickConnectExpired().messageRes())
        assertEquals(R.string.rawkoon_error_jellyfin_unreachable, RawkoonException.JellyfinUnreachable().messageRes())
        assertEquals(R.string.rawkoon_error_unauthorized, RawkoonException.Unauthorized().messageRes())
        assertEquals(R.string.rawkoon_error_not_linked, RawkoonException.NotLinked().messageRes())
        assertEquals(R.string.rawkoon_error_no_integration, RawkoonException.NoJellyfinIntegration().messageRes())
        assertEquals(R.string.rawkoon_error_invalid_url, RawkoonException.InvalidSettings().messageRes())
        assertEquals(R.string.rawkoon_error_unreachable, RawkoonException.Unreachable(IOException()).messageRes())
        assertEquals(R.string.rawkoon_error_http, RawkoonException.Http(500).messageRes())
    }
}

class RawkoonUrlNormalizationTest {
    @Test
    fun adds_https_and_drops_invisible_characters() {
        assertEquals("https://rawkoon.example.test", normalizeRawkoonUrl("  rawkoon.example.test\u200B\n"))
        assertEquals("http://10.0.0.2:3000", normalizeRawkoonUrl("http://10.0.0.2:3000"))
        assertEquals("", normalizeRawkoonUrl(" \n"))
    }
}
