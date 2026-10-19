package com.github.damontecres.wholphin.rawkoon.ui.preferences

import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonPreferenceStyleTest {
    @Test
    fun `track is terracotta when on and dark when off`() {
        assertEquals(RawkoonTokens.Primary600, RawkoonPreferenceStyle.trackColor(true))
        assertEquals(RawkoonTokens.Neutral700, RawkoonPreferenceStyle.trackColor(false))
    }

    @Test
    fun `thumb is inset equally from both ends of the track`() {
        val off = RawkoonPreferenceStyle.thumbOffset(false)
        val on = RawkoonPreferenceStyle.thumbOffset(true)
        assertEquals(3.dp, off)
        val endGap = RawkoonPreferenceStyle.SwitchWidth - on - RawkoonPreferenceStyle.ThumbSize
        assertEquals(off, endGap)
        assertTrue(on > off)
    }
}
