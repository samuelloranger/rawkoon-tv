package com.github.damontecres.wholphin.rawkoon.ui.kit

import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.rawkoon.home.RequestChip
import com.github.damontecres.wholphin.rawkoon.home.toStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RawkoonKitTest {
    @Test
    fun status_tones_follow_the_rawkoon_mapping() {
        listOf(RawkoonStatus.Downloaded, RawkoonStatus.Ready, RawkoonStatus.Watched).forEach {
            assertEquals(RawkoonTone.Ok, it.tone)
        }
        listOf(RawkoonStatus.Downloading, RawkoonStatus.Approved).forEach {
            assertEquals(RawkoonTone.Progress, it.tone)
        }
        listOf(RawkoonStatus.Missing, RawkoonStatus.Failed, RawkoonStatus.Denied).forEach {
            assertEquals(RawkoonTone.Attention, it.tone)
        }
        listOf(RawkoonStatus.Requested, RawkoonStatus.Pending, RawkoonStatus.InProduction).forEach {
            assertEquals(RawkoonTone.Requested, it.tone)
        }
        assertEquals(RawkoonTone.Returning, RawkoonStatus.Returning.tone)
        assertEquals(RawkoonTone.Neutral, RawkoonStatus.Planned.tone)
    }

    @Test
    fun status_colors_match_the_tokens() {
        assertEquals(Color(0xFF34D399), RawkoonStatus.Downloaded.barColor)
        assertEquals(Color(0xFF6EE7B7), RawkoonStatus.Downloaded.badgeText)
        assertEquals(Color(0xFF10B981).copy(alpha = 0.2f), RawkoonStatus.Downloaded.badgeBg)
        assertEquals(Color(0xFF38BDF8), RawkoonStatus.Downloading.barColor)
        assertEquals(Color(0xFFFB7185), RawkoonStatus.Missing.barColor)
        assertEquals(Color(0xFFFBBF24), RawkoonStatus.Pending.barColor)
        assertEquals(Color(0xFFE8A06A), RawkoonStatus.Returning.barColor)
        assertEquals(Color(0xFFAA9A8C), RawkoonStatus.Planned.barColor)
        assertEquals(Color(0xFF322A25), RawkoonStatus.Planned.badgeBg)
    }

    @Test
    fun every_tone_has_distinct_bar_color() {
        val bars = RawkoonTone.entries.map { it.barColor }
        assertEquals(bars.size, bars.toSet().size)
        assertNotEquals(RawkoonStatus.Pending.badgeBg, RawkoonStatus.Approved.badgeBg)
    }

    @Test
    fun request_chips_use_the_request_tones() {
        assertEquals(RawkoonTone.Requested, RequestChip.Pending.toStatus().tone)
        assertEquals(RawkoonTone.Progress, RequestChip.Approved.toStatus().tone)
        assertEquals(RawkoonTone.Progress, RequestChip.Downloading(40).toStatus().tone)
        assertEquals(RawkoonTone.Progress, RequestChip.Downloading(null).toStatus().tone)
        assertEquals(RawkoonTone.Ok, RequestChip.Ready.toStatus().tone)
        assertEquals(RawkoonTone.Attention, RequestChip.Denied.toStatus().tone)
    }

    @Test
    fun button_variants_use_the_spec_colors() {
        val primary = RawkoonButtonVariant.Primary.colors()
        assertEquals(Color(0xFFCF6A4E), primary.container)
        assertEquals(Color(0xFFDF8753), primary.focusedContainer)
        assertEquals(Color(0xFF141010), primary.content)
        val secondary = RawkoonButtonVariant.Secondary.colors()
        assertEquals(Color.White.copy(alpha = 0.10f), secondary.container)
        assertEquals(Color.White.copy(alpha = 0.14f), secondary.focusedContainer)
        val ghost = RawkoonButtonVariant.Ghost.colors()
        assertEquals(Color.Transparent, ghost.container)
        assertEquals(Color.White.copy(alpha = 0.14f), ghost.focusedContainer)
    }

    @Test
    fun motion_durations_are_the_rawkoon_ones() {
        assertEquals(220, RawkoonMotion.ENTER_MILLIS)
        assertEquals(120, RawkoonMotion.EXIT_MILLIS)
        assertEquals(150, RawkoonMotion.COLOR_MILLIS)
        assertEquals(0f, RawkoonMotion.EnterEasing.transform(0f), 1e-6f)
        assertEquals(1f, RawkoonMotion.EnterEasing.transform(1f), 1e-4f)
    }

    @Test
    fun progress_fraction_is_clamped_and_nan_is_zero() {
        assertEquals(0f, progressFraction(Float.NaN), 0f)
        assertEquals(0f, progressFraction(-0.5f), 0f)
        assertEquals(1f, progressFraction(1.7f), 0f)
        assertEquals(0.4f, progressFraction(0.4f), 0f)
        assertEquals(1f, progressFraction(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, progressFraction(Float.NEGATIVE_INFINITY), 0f)
    }
}
