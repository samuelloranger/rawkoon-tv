package cloud.rawkoon.tv.live

import java.util.UUID
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.UserDataChangeInfo
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEventsTest {
    private val me = UUID.randomUUID()

    private fun data() = UserItemDataDto(playbackPositionTicks = 0, playCount = 1, isFavorite = false, played = true, itemId = UUID.randomUUID(), key = "k")

    private fun library(added: List<String> = emptyList(), removed: List<String> = emptyList(), updated: List<String> = emptyList()) =
        LibraryUpdateInfo(
            foldersAddedTo = emptyList(), foldersRemovedFrom = emptyList(), itemsAdded = added, itemsRemoved = removed,
            itemsUpdated = updated, collectionFolders = emptyList(), isEmpty = added.isEmpty() && removed.isEmpty() && updated.isEmpty(),
        )

    @Test
    fun `user data of the signed-in user counts`() {
        assertTrue(UserDataChangeInfo(me, listOf(data())).concerns(me))
    }

    @Test
    fun `user data of another user is ignored`() {
        assertFalse(UserDataChangeInfo(UUID.randomUUID(), listOf(data())).concerns(me))
    }

    @Test
    fun `a user data change with no items is ignored`() {
        assertFalse(UserDataChangeInfo(me, emptyList()).concerns(me))
    }

    @Test
    fun `a message without a payload is ignored`() {
        assertFalse((null as UserDataChangeInfo?).concerns(me))
        assertFalse((null as LibraryUpdateInfo?).concernsLibrary())
    }

    @Test
    fun `a library change counts when anything was added, removed or updated`() {
        assertTrue(library(added = listOf("a")).concernsLibrary())
        assertTrue(library(removed = listOf("a")).concernsLibrary())
        assertTrue(library(updated = listOf("a")).concernsLibrary())
        assertFalse(library().concernsLibrary())
    }

    @Test
    fun `every library page follows both events except favorites, which follows the user's data only`() {
        for (page in listOf(LivePage.HOME, LivePage.DETAILS, LivePage.SEASON, LivePage.LIBRARY)) {
            assertTrue(page.reactsTo(LiveKind.USER_DATA))
            assertTrue(page.reactsTo(LiveKind.LIBRARY))
        }
        assertTrue(LivePage.FAVORITES.reactsTo(LiveKind.USER_DATA))
        assertFalse(LivePage.FAVORITES.reactsTo(LiveKind.LIBRARY))
        assertFalse(LivePage.FAVORITES.reactsToAny(setOf(LiveKind.LIBRARY)))
        assertTrue(LivePage.FAVORITES.reactsToAny(setOf(LiveKind.LIBRARY, LiveKind.USER_DATA)))
    }

    @Test
    fun `rawkoon news reaches home and the title page, and no jellyfin library page`() {
        for (kind in listOf(LiveKind.RAWKOON_LANDED, LiveKind.REQUEST_CHANGED)) {
            assertTrue(LivePage.HOME.reactsTo(kind))
            assertTrue(LivePage.DISCOVER_DETAILS.reactsTo(kind))
            for (page in listOf(LivePage.DETAILS, LivePage.SEASON, LivePage.LIBRARY, LivePage.FAVORITES, LivePage.DISCOVER, LivePage.OTHER)) {
                assertFalse("$page $kind", page.reactsTo(kind))
            }
        }
    }

    @Test
    fun `the discover title page follows the library to find its jellyfin item, not watched state`() {
        assertTrue(LivePage.DISCOVER_DETAILS.reactsTo(LiveKind.LIBRARY))
        assertFalse(LivePage.DISCOVER_DETAILS.reactsTo(LiveKind.USER_DATA))
    }

    @Test
    fun `each page opens only the connections it follows`() {
        val jellyfin = LivePage.entries.filter { it.followsJellyfin }.toSet()
        assertEquals(setOf(LivePage.HOME, LivePage.DETAILS, LivePage.SEASON, LivePage.FAVORITES, LivePage.LIBRARY, LivePage.DISCOVER_DETAILS), jellyfin)
        // Discover keeps Rawkoon's streams for toasts without a Jellyfin socket; the player has neither
        assertFalse(LivePage.DISCOVER.followsJellyfin)
        assertTrue(LivePage.DISCOVER.followsRawkoon)
        assertFalse(LivePage.OTHER.followsJellyfin)
        assertFalse(LivePage.OTHER.followsRawkoon)
        assertEquals(setOf(LiveKind.USER_DATA, LiveKind.LIBRARY), LiveKind.JELLYFIN)
    }

    @Test
    fun `screens that only reload when shown never react`() {
        assertFalse(LivePage.OTHER.reactsTo(LiveKind.USER_DATA))
        assertFalse(LivePage.OTHER.reactsTo(LiveKind.LIBRARY))
        assertEquals(false, LivePage.OTHER.reactsToAny(LiveKind.entries.toSet()))
    }

    @Test
    fun `a burst waits for the window once and delivers every kind together`() {
        val batch = LiveBatch(windowMs = 1_000, minGapMs = 5_000)
        assertEquals(1_000L, batch.add(LiveKind.LIBRARY, now = 0))
        assertNull(batch.add(LiveKind.LIBRARY, now = 100))
        assertNull(batch.add(LiveKind.USER_DATA, now = 200))
        assertEquals(setOf(LiveKind.LIBRARY, LiveKind.USER_DATA), batch.drain(now = 1_000))
    }

    @Test
    fun `a delivery never follows the last one by less than the minimum gap`() {
        val batch = LiveBatch(windowMs = 1_000, minGapMs = 5_000)
        batch.add(LiveKind.USER_DATA, now = 0)
        batch.drain(now = 1_000)
        // Due at 6_000: 4_000 from the new event at 2_000
        assertEquals(4_000L, batch.add(LiveKind.USER_DATA, now = 2_000))
        batch.drain(now = 6_000)
        // Long after the gap, only the window applies
        assertEquals(1_000L, batch.add(LiveKind.USER_DATA, now = 60_000))
    }

    @Test
    fun `drain empties the batch and a cleared batch delivers nothing old`() {
        val batch = LiveBatch()
        batch.add(LiveKind.LIBRARY, now = 0)
        assertEquals(setOf(LiveKind.LIBRARY), batch.drain(now = 1_500))
        assertEquals(emptySet<LiveKind>(), batch.drain(now = 9_000))
        batch.add(LiveKind.USER_DATA, now = 10_000)
        batch.clear()
        assertEquals(1_500L, batch.add(LiveKind.LIBRARY, now = 20_000))
        assertEquals(setOf(LiveKind.LIBRARY), batch.drain(now = 21_500))
    }
}
