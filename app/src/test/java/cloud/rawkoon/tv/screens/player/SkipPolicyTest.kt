package cloud.rawkoon.tv.screens.player

import cloud.rawkoon.tv.data.SkipMode
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipPolicyTest {
    private val intro = MediaSegmentType.INTRO
    private val credits = MediaSegmentType.OUTRO

    @Test
    fun `the button mode shows the button`() {
        assertEquals(SkipAction.SHOW_BUTTON, skipAction(intro, SkipMode.BUTTON, alreadySkipped = false, hasNext = false))
        assertEquals(SkipAction.SHOW_BUTTON, skipAction(credits, SkipMode.BUTTON, alreadySkipped = false, hasNext = true))
    }

    @Test
    fun `do nothing ignores the segment`() {
        assertEquals(SkipAction.NOTHING, skipAction(intro, SkipMode.NONE, alreadySkipped = false, hasNext = false))
    }

    @Test
    fun `automatic mode skips once, then falls back to the button`() {
        assertEquals(SkipAction.SKIP, skipAction(intro, SkipMode.AUTO, alreadySkipped = false, hasNext = true))
        assertEquals(SkipAction.SHOW_BUTTON, skipAction(intro, SkipMode.AUTO, alreadySkipped = true, hasNext = true))
    }

    @Test
    fun `automatic credits with a next episode go to the next episode, without one they are skipped`() {
        assertEquals(SkipAction.GO_TO_NEXT_EPISODE, skipAction(credits, SkipMode.AUTO, alreadySkipped = false, hasNext = true))
        assertEquals(SkipAction.SKIP, skipAction(credits, SkipMode.AUTO, alreadySkipped = false, hasNext = false))
    }

    @Test
    fun `a rewound resume point stops at the start`() {
        assertEquals(55_000L, rewoundStart(60_000, 5_000))
        assertEquals(0L, rewoundStart(2_000, 5_000))
        assertEquals(60_000L, rewoundStart(60_000, 0))
        // Not resuming: nothing to rewind from
        assertEquals(0L, rewoundStart(0, 5_000))
    }

    @Test
    fun `without a limit it never asks, whatever the count`() {
        val tracker = AutoplayTracker(limit = 0)
        repeat(10) { tracker.onAutoStarted() }
        assertFalse(tracker.shouldAsk)
    }

    @Test
    fun `it asks once the count reaches the limit`() {
        val tracker = AutoplayTracker(limit = 3)
        tracker.onAutoStarted()
        tracker.onAutoStarted()
        assertFalse(tracker.shouldAsk)
        tracker.onAutoStarted()
        assertTrue(tracker.shouldAsk)
    }

    @Test
    fun `a key press starts the count over`() {
        val tracker = AutoplayTracker(limit = 2)
        tracker.onAutoStarted()
        tracker.onAutoStarted()
        assertTrue(tracker.shouldAsk)
        tracker.onKey()
        assertEquals(0, tracker.count)
        assertFalse(tracker.shouldAsk)
    }

    @Test
    fun `the plan counts down, waits when autoplay is off, and asks when it is time`() {
        val tracker = AutoplayTracker(limit = 2)
        assertEquals(UpNextPlan(8, stillWatching = false), tracker.plan(autoplay = true, countdownSeconds = 8))
        assertEquals(UpNextPlan(null, stillWatching = false), tracker.plan(autoplay = false, countdownSeconds = 8))
        tracker.onAutoStarted()
        tracker.onAutoStarted()
        assertEquals(UpNextPlan(null, stillWatching = true), tracker.plan(autoplay = true, countdownSeconds = 8))
        assertEquals(UpNextPlan(null, stillWatching = true), tracker.plan(autoplay = false, countdownSeconds = 8))
    }
}
