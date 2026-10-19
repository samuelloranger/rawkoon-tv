package com.github.damontecres.wholphin.rawkoon.ui.nav

import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.rawkoon.ui.kit.RawkoonTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonNavTest {
    @Test
    fun `selected icon is apricot even when focused`() {
        assertEquals(RawkoonTokens.Primary400, RawkoonNav.iconColor(selected = true, focused = false))
        assertEquals(RawkoonTokens.Primary400, RawkoonNav.iconColor(selected = true, focused = true))
    }

    @Test
    fun `idle icon is muted and focused icon is light`() {
        assertEquals(RawkoonTokens.Neutral400, RawkoonNav.iconColor(selected = false, focused = false))
        assertEquals(RawkoonTokens.Neutral50, RawkoonNav.iconColor(selected = false, focused = true))
    }

    @Test
    fun `label is light when selected or focused`() {
        assertEquals(RawkoonTokens.Neutral50, RawkoonNav.labelColor(selected = true, focused = false))
        assertEquals(RawkoonTokens.Neutral50, RawkoonNav.labelColor(selected = false, focused = true))
        assertEquals(RawkoonTokens.Neutral400, RawkoonNav.labelColor(selected = false, focused = false))
    }

    @Test
    fun `only the selected item has a background`() {
        assertEquals(Color.Transparent, RawkoonNav.containerColor(selected = false))
        assertEquals(RawkoonTokens.white(0.08f), RawkoonNav.containerColor(selected = true))
    }

    @Test
    fun `avatar initial is the first letter in upper case`() {
        assertEquals("S", RawkoonNav.avatarInitial("sam"))
        assertEquals("A", RawkoonNav.avatarInitial("  (alex)"))
        assertEquals("?", RawkoonNav.avatarInitial(null))
        assertEquals("?", RawkoonNav.avatarInitial("   "))
    }

    @Test
    fun `avatar shows the image only when there is a usable one`() {
        assertTrue(RawkoonNav.showAvatarImage("https://example.test/a.png", loadFailed = false))
        assertFalse(RawkoonNav.showAvatarImage("https://example.test/a.png", loadFailed = true))
        assertFalse(RawkoonNav.showAvatarImage(null, loadFailed = false))
        assertFalse(RawkoonNav.showAvatarImage("  ", loadFailed = false))
    }

    @Test
    fun `rail icons are 20dp`() {
        assertEquals(20f, RawkoonNav.IconSize.value)
    }
}
