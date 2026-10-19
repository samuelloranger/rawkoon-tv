package com.github.damontecres.wholphin.rawkoon.ui.update

import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.util.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RawkoonUpdateLogicTest {
    private fun release(
        version: String,
        body: String? = "## Fixes\n- one",
        publishedAt: String? = null,
    ) = Release(
        version = Version.fromString(version),
        downloadUrl = null,
        publishedAt = publishedAt,
        body = body,
        notes = listOf(),
    )

    @Test
    fun a_newer_release_is_available_and_the_same_or_older_is_up_to_date() {
        val installed = Version.fromString("v2.5.0")
        assertEquals(UpdateKind.AVAILABLE, updateKind(installed, release("v2.6.0")))
        assertEquals(UpdateKind.UP_TO_DATE, updateKind(installed, release("v2.5.0")))
        assertEquals(UpdateKind.UP_TO_DATE, updateKind(installed, release("v2.4.9")))
    }

    @Test
    fun download_fraction_is_null_until_the_size_is_known_and_clamped_after() {
        assertNull(downloadFraction(100L, -1L))
        assertNull(downloadFraction(100L, 0L))
        assertEquals(0.25f, downloadFraction(25L, 100L)!!, 0.0001f)
        assertEquals(1f, downloadFraction(150L, 100L)!!, 0.0001f)
    }

    @Test
    fun download_percent_is_a_whole_clamped_number() {
        assertEquals(0, downloadPercent(-0.5f))
        assertEquals(42, downloadPercent(0.429f))
        assertEquals(100, downloadPercent(1.7f))
    }

    @Test
    fun release_notes_drop_the_version_heading() {
        val notes = releaseNotesBody(release("v2.6.0"))
        assertEquals("## Fixes\n- one", notes)
    }

    @Test
    fun published_date_is_localized_or_null() {
        assertNull(formatPublishedDate(null))
        assertNull(formatPublishedDate("not a date"))
        assertEquals("Oct 18, 2026", formatPublishedDate("2026-10-18T14:00:00Z", Locale.US))
    }

    @Test
    fun dev_builds_compare_against_releases() {
        assertEquals(UpdateKind.UP_TO_DATE, updateKind(Version.fromString("v2.5.0-3-gabc1234"), release("v2.5.0")))
        assertEquals(UpdateKind.AVAILABLE, updateKind(Version.fromString("v2.5.0"), release("v2.5.1")))
        assertEquals(UpdateKind.AVAILABLE, updateKind(Version.fromString("v2.5.0-3-gabc1234"), release("v2.6.0")))
    }

    @Test
    fun release_notes_without_a_body_are_empty() {
        assertEquals("", releaseNotesBody(release("v2.6.0", body = null)))
    }

    @Test
    fun edge_inputs_are_safe() {
        assertNull(formatPublishedDate("  "))
        assertEquals(0f, downloadFraction(-10L, 100L)!!, 0.0001f)
    }
}
