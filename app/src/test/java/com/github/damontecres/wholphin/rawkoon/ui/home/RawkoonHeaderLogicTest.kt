package com.github.damontecres.wholphin.rawkoon.ui.home

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonStatus
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawkoonHeaderLogicTest {
    @Test
    fun `playable kinds get a type label and the rest get none`() {
        assertEquals(R.string.rawkoon_header_type_movie, RawkoonHeaderLogic.typeLabelRes(BaseItemKind.MOVIE))
        assertEquals(R.string.rawkoon_header_type_show, RawkoonHeaderLogic.typeLabelRes(BaseItemKind.SERIES))
        assertEquals(R.string.rawkoon_header_type_season, RawkoonHeaderLogic.typeLabelRes(BaseItemKind.SEASON))
        assertEquals(R.string.rawkoon_header_type_episode, RawkoonHeaderLogic.typeLabelRes(BaseItemKind.EPISODE))
        assertNull(RawkoonHeaderLogic.typeLabelRes(BaseItemKind.GENRE))
        assertNull(RawkoonHeaderLogic.typeLabelRes(null))
    }

    @Test
    fun `rating shows one decimal and is hidden when missing`() {
        assertEquals("7.6", RawkoonHeaderLogic.ratingText(7.55f))
        assertEquals("8.0", RawkoonHeaderLogic.ratingText(8f))
        assertNull(RawkoonHeaderLogic.ratingText(0f))
        assertNull(RawkoonHeaderLogic.ratingText(null))
    }

    @Test
    fun `genres are capped and blanks dropped`() {
        assertEquals(
            listOf("Drama", "Mystery", "Crime"),
            RawkoonHeaderLogic.genres(listOf("Drama", " ", "Mystery", "Crime", "Thriller")),
        )
        assertEquals(emptyList<String>(), RawkoonHeaderLogic.genres(null))
    }

    @Test
    fun `status is returning for a continuing series and watched for a played item`() {
        assertEquals(RawkoonStatus.Returning, RawkoonHeaderLogic.status(BaseItemKind.SERIES, "Continuing", played = false))
        assertEquals(RawkoonStatus.Watched, RawkoonHeaderLogic.status(BaseItemKind.MOVIE, null, played = true))
        assertNull(RawkoonHeaderLogic.status(BaseItemKind.SERIES, "Ended", played = false))
        assertNull(RawkoonHeaderLogic.status(BaseItemKind.MOVIE, null, played = false))
    }
}
