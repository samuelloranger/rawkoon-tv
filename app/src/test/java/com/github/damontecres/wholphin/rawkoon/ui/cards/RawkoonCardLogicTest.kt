package com.github.damontecres.wholphin.rawkoon.ui.cards

import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonCardLogicTest {
    @Test
    fun `tall shapes are posters and wide or square shapes are not`() {
        assertTrue(RawkoonCardLogic.isPoster(2f / 3f))
        assertFalse(RawkoonCardLogic.isPoster(1f))
        assertFalse(RawkoonCardLogic.isPoster(16f / 9f))
    }

    @Test
    fun `progress is a fraction only while in progress`() {
        assertEquals(0.25f, RawkoonCardLogic.progress(25.0)!!, 0.0001f)
        assertNull(RawkoonCardLogic.progress(null))
        assertNull(RawkoonCardLogic.progress(0.0))
        assertNull(RawkoonCardLogic.progress(100.0))
        assertNull(RawkoonCardLogic.progress(140.0))
    }

    @Test
    fun `a played item is watched unless it is partway through`() {
        assertEquals(RawkoonStatus.Watched, RawkoonCardLogic.status(played = true, percent = null))
        assertEquals(RawkoonStatus.Watched, RawkoonCardLogic.status(played = true, percent = 100.0))
        assertNull(RawkoonCardLogic.status(played = true, percent = 40.0))
        assertNull(RawkoonCardLogic.status(played = false, percent = null))
    }

    @Test
    fun `year text skips missing and zero years`() {
        assertEquals("2024", RawkoonCardLogic.yearText(2024))
        assertNull(RawkoonCardLogic.yearText(null))
        assertNull(RawkoonCardLogic.yearText(0))
    }

    @Test
    fun `availability maps onto the status tones`() {
        assertEquals(RawkoonStatus.Pending, RawkoonCardLogic.discoverBadge(SeerrAvailability.PENDING)!!.status)
        assertEquals(RawkoonStatus.Approved, RawkoonCardLogic.discoverBadge(SeerrAvailability.PROCESSING)!!.status)
        assertEquals(RawkoonStatus.Downloaded, RawkoonCardLogic.discoverBadge(SeerrAvailability.AVAILABLE)!!.status)
        assertFalse(RawkoonCardLogic.discoverBadge(SeerrAvailability.AVAILABLE)!!.partial)
        assertTrue(RawkoonCardLogic.discoverBadge(SeerrAvailability.PARTIALLY_AVAILABLE)!!.partial)
        assertNull(RawkoonCardLogic.discoverBadge(SeerrAvailability.UNKNOWN))
        assertNull(RawkoonCardLogic.discoverBadge(SeerrAvailability.BLOCKLISTED))
        assertNull(RawkoonCardLogic.discoverBadge(null))
    }
}
