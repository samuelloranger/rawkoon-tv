package cloud.rawkoon.tv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRowTest {
    private val old = setOf("a", "b", "c", "d")

    @Test
    fun `a title added ahead of the remembered card is new ahead`() {
        assertTrue(hasNewAhead(listOf("new", "a", "b", "c", "d"), old, index = 3))
    }

    @Test
    fun `a title added after the remembered card is not`() {
        assertFalse(hasNewAhead(listOf("a", "b", "c", "new", "d"), old, index = 2))
    }

    @Test
    fun `a known card moving to the front is not new`() {
        // Continue watching: the title just played moves to the front.
        assertFalse(hasNewAhead(listOf("c", "a", "b", "d"), old, index = 0))
        assertFalse(hasNewAhead(listOf("c", "a", "b", "d"), old, index = 3))
    }

    @Test
    fun `cards without a key are ignored`() {
        assertFalse(hasNewAhead(listOf(null, "a", "b"), old, index = 2))
    }
}
