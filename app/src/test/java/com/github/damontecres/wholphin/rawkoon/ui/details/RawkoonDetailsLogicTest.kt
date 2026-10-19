package com.github.damontecres.wholphin.rawkoon.ui.details

import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RawkoonDetailsLogicTest {
    @Test
    fun resumeTimeShowsHoursOnlyWhenNeeded() {
        assertEquals("1:12:04", formatResumeTime(1.hours + 12.minutes + 4.seconds))
        assertEquals("12:04", formatResumeTime(12.minutes + 4.seconds))
        assertEquals("0:09", formatResumeTime(9.seconds))
        assertEquals("0:00", formatResumeTime((-5).seconds))
    }

    @Test
    fun minutesLeftRoundsAndNeverShowsZero() {
        assertEquals(30, minutesLeft(90.minutes, 60.minutes))
        assertEquals(1, minutesLeft(90.minutes, 89.minutes + 50.seconds))
        assertEquals(2, minutesLeft(90.minutes, 88.minutes + 20.seconds))
    }

    @Test
    fun minutesLeftIsNullWhenNotStartedOrFinished() {
        assertNull(minutesLeft(90.minutes, Duration.ZERO))
        assertNull(minutesLeft(null, 10.minutes))
        assertNull(minutesLeft(90.minutes, 90.minutes))
        assertNull(minutesLeft(90.minutes, 120.minutes))
    }

    @Test
    fun watchedFractionIsClamped() {
        assertEquals(0.5f, watchedFraction(100.minutes, 50.minutes)!!, 0.0001f)
        assertEquals(1f, watchedFraction(100.minutes, 150.minutes)!!, 0.0001f)
        assertNull(watchedFraction(100.minutes, Duration.ZERO))
        assertNull(watchedFraction(Duration.ZERO, 5.minutes))
    }

    @Test
    fun ratingUsesOneDecimal() {
        assertEquals("7.8", formatRating(7.84f, Locale.US))
        assertEquals("8.0", formatRating(8f, Locale.US))
        assertEquals("7,8", formatRating(7.84f, Locale.FRANCE))
    }

    @Test
    fun criticRatingIsAWholePercent() {
        assertEquals("87%", formatCriticRating(87.6f))
        assertEquals("0%", formatCriticRating(0f))
        assertEquals(null, formatCriticRating(null))
    }

    @Test
    fun seriesCountsSkipUnknownParts() {
        assertEquals("3S · 28E", seriesCountsLabel(3, 28))
        assertEquals("3S", seriesCountsLabel(3, null))
        assertEquals("28E", seriesCountsLabel(0, 28))
        assertNull(seriesCountsLabel(null, 0))
    }

    @Test
    fun onlyContinuingShowsGetAStatus() {
        assertEquals(RawkoonStatus.Returning, seriesStatus("Continuing"))
        assertEquals(RawkoonStatus.Returning, seriesStatus("continuing"))
        assertNull(seriesStatus("Ended"))
        assertNull(seriesStatus(null))
    }

    @Test
    fun streamChipsKeepOrderAndDropBlanks() {
        assertEquals(
            listOf("4K HDR10", "HEVC", "English Atmos", "French"),
            streamChipLabels("4K HDR10", "HEVC", "English Atmos", "French"),
        )
        assertEquals(listOf("1080p (+2)", "English"), streamChipLabels("1080p", null, "English", " ", extraVersions = 2))
        assertTrue(streamChipLabels(null, null, null, null).isEmpty())
    }

    @Test
    fun playActionsFollowTheResumeVersionTrailerAndDeleteState() {
        assertEquals(
            listOf(
                RawkoonPlayAction.Play,
                RawkoonPlayAction.Watched,
                RawkoonPlayAction.Favorite,
                RawkoonPlayAction.More,
            ),
            playActions(Duration.ZERO, mediaSourceCount = 1, hasTrailerButton = false, canDelete = false),
        )
        assertEquals(
            listOf(
                RawkoonPlayAction.Play,
                RawkoonPlayAction.Restart,
                RawkoonPlayAction.Version,
                RawkoonPlayAction.Watched,
                RawkoonPlayAction.Favorite,
                RawkoonPlayAction.Trailer,
                RawkoonPlayAction.Delete,
                RawkoonPlayAction.More,
            ),
            playActions(5.minutes, mediaSourceCount = 2, hasTrailerButton = true, canDelete = true),
        )
    }

    @Test
    fun episodeEdgeMapsWatchState() {
        assertEquals(EpisodeEdge.Watched, episodeEdge(played = true, playedPercentage = 0.0))
        assertEquals(EpisodeEdge.Watched, episodeEdge(played = true, playedPercentage = 100.0))
        assertEquals(EpisodeEdge.InProgress, episodeEdge(played = false, playedPercentage = 40.0))
        assertEquals(EpisodeEdge.InProgress, episodeEdge(played = true, playedPercentage = 40.0))
        assertEquals(EpisodeEdge.Unwatched, episodeEdge(played = false, playedPercentage = 0.0))
    }

    @Test
    fun watchedCheckHidesWhilePartlyWatched() {
        assertTrue(showWatchedCheck(played = true, playedPercentage = 0.0))
        assertFalse(showWatchedCheck(played = true, playedPercentage = 30.0))
        assertFalse(showWatchedCheck(played = false, playedPercentage = 0.0))
    }
}
