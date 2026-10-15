package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.util.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonUpdateStorageTest {
    private val mb = 1024L * 1024L

    @Test
    fun an_update_needs_three_times_the_apk_and_a_margin() {
        assertEquals(3 * 45 * mb + 50 * mb, RawkoonUpdateStorage.requiredBytes(45 * mb))
    }

    @Test
    fun space_is_enough_only_at_or_above_the_requirement() {
        val need = RawkoonUpdateStorage.requiredBytes(45 * mb)
        assertTrue(RawkoonUpdateStorage.hasEnoughSpace(45 * mb, need))
        assertFalse(RawkoonUpdateStorage.hasEnoughSpace(45 * mb, need - 1))
        // A TV with about 220 MB free cannot take a 70 MB update
        assertFalse(RawkoonUpdateStorage.hasEnoughSpace(70 * mb, 220 * mb))
        assertTrue(RawkoonUpdateStorage.hasEnoughSpace(40 * mb, 220 * mb))
    }

    @Test
    fun the_message_rounds_megabytes_up() {
        assertEquals(1, RawkoonUpdateStorage.megabytes(1))
        assertEquals(1, RawkoonUpdateStorage.megabytes(mb))
        assertEquals(2, RawkoonUpdateStorage.megabytes(mb + 1))
        assertEquals(0, RawkoonUpdateStorage.megabytes(0))
    }

    @Test
    fun the_apk_is_deleted_once_the_app_is_at_the_version_it_was_downloaded_for() {
        val target = Version.fromString("v1.2.0")
        assertTrue(RawkoonUpdateStorage.shouldDeleteApk(Version.fromString("1.2.0-0-gabc"), target))
        assertTrue(RawkoonUpdateStorage.shouldDeleteApk(Version.fromString("1.3.1"), target))
        assertFalse(RawkoonUpdateStorage.shouldDeleteApk(Version.fromString("1.1.9-4-gabc"), target))
        // Nothing was downloaded: nothing to delete
        assertFalse(RawkoonUpdateStorage.shouldDeleteApk(Version.fromString("1.3.1"), null))
    }
}
