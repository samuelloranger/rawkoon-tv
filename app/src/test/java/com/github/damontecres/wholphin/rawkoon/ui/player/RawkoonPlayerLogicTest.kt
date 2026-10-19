package com.github.damontecres.wholphin.rawkoon.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonPlayerLogicTest {
    @Test
    fun clock_formats_minutes_and_hours() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:09", formatClock(9_999))
        assertEquals("12:05", formatClock(725_000))
        assertEquals("1:12:04", formatClock(4_324_000))
        assertEquals("0:00", formatClock(-5_000))
    }

    @Test
    fun seek_delta_is_signed() {
        assertEquals("+1:30", formatSeekDelta(90_000))
        assertEquals("-0:30", formatSeekDelta(-30_000))
        assertEquals("+0:00", formatSeekDelta(0))
    }

    @Test
    fun skip_amount_uses_seconds_below_a_minute() {
        assertEquals("+30s", formatSkipAmount(30_000))
        assertEquals("-10s", formatSkipAmount(-10_000))
        assertEquals("+1:30", formatSkipAmount(90_000))
        assertEquals("-2:00", formatSkipAmount(-120_000))
    }

    @Test
    fun seek_delta_label_is_hidden_at_zero() {
        assertNull(seekDeltaLabel(0))
        assertEquals("+1:30", seekDeltaLabel(90_000))
        assertEquals("-0:30", seekDeltaLabel(-30_000))
    }

    @Test
    fun segment_span_is_clamped_and_empty_spans_are_dropped() {
        assertEquals(FractionSpan(0.1f, 0.2f), segmentSpan(10_000, 20_000, 100_000))
        assertEquals(FractionSpan(0f, 1f), segmentSpan(-5_000, 200_000, 100_000))
        assertNull(segmentSpan(20_000, 20_000, 100_000))
        assertNull(segmentSpan(0, 10_000, 0))
    }

    @Test
    fun fraction_of_handles_unknown_duration() {
        assertEquals(0.25f, fractionOf(25, 100), 0.0001f)
        assertEquals(0f, fractionOf(25, -1), 0f)
        assertEquals(1f, fractionOf(500, 100), 0f)
    }

    @Test
    fun control_row_has_three_columns_in_focus_order() {
        val full = controlOrder(previousEnabled = true, nextEnabled = true)
        assertEquals(3, full.size)
        assertEquals(listOf(RawkoonPlayerControl.Audio, RawkoonPlayerControl.Subtitles), full[0])
        assertEquals(
            listOf(
                RawkoonPlayerControl.Previous,
                RawkoonPlayerControl.SeekBack,
                RawkoonPlayerControl.PlayPause,
                RawkoonPlayerControl.SeekForward,
            ),
            full[1],
        )
        assertEquals(listOf(RawkoonPlayerControl.Next, RawkoonPlayerControl.Settings), full[2])
    }

    @Test
    fun control_row_drops_previous_and_next_when_disabled() {
        val bare = controlOrder(previousEnabled = false, nextEnabled = false)
        assertTrue(RawkoonPlayerControl.Previous !in bare[1])
        assertEquals(RawkoonPlayerControl.PlayPause, bare[1][1])
        assertEquals(listOf(RawkoonPlayerControl.Settings), bare[2])
    }

    @Test
    fun remaining_time_accounts_for_speed() {
        assertEquals(60_000, remainingWallMillis(120_000, 60_000, 1f))
        assertEquals(30_000, remainingWallMillis(120_000, 60_000, 2f))
        assertEquals(0, remainingWallMillis(-1, 0, 1f))
        assertEquals(60_000, remainingWallMillis(120_000, 60_000, 0f))
    }

    @Test
    fun minutes_left_rounds_up() {
        assertEquals(1, minutesLeft(100_000, 99_000))
        assertEquals(2, minutesLeft(180_000, 61_000))
        assertEquals(0, minutesLeft(100_000, 100_000))
        assertEquals(0, minutesLeft(-1, 0))
    }

    @Test
    fun season_and_episode_text() {
        assertEquals("S1 · E4", formatSeasonEpisode(1, 4))
        assertEquals("E4", formatSeasonEpisode(null, 4))
        assertNull(formatSeasonEpisode(null, null))
    }

    @Test
    fun track_chip_value_prefers_language() {
        assertEquals("English 5.1", trackChipValue("English", "Commentary", "English - AC3", "5.1"))
        assertEquals("Commentary", trackChipValue(null, "Commentary", "x"))
        assertEquals("English - AC3", trackChipValue(" ", null, "English - AC3", "5.1"))
        assertNull(trackChipValue(null, "", null))
    }
}
