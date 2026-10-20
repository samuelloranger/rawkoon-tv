package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.jellyfin.JellyfinTmdbLookup.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinTmdbLookupTest {
    @Test
    fun `the candidate whose tmdb id matches wins`() {
        val picked = JellyfinTmdbLookup.pick(listOf(Candidate("a", "5"), Candidate("b", "603"), Candidate("c", null)), 603)
        assertEquals("b", picked?.id)
    }

    @Test
    fun `a single candidate without a tmdb id is taken`() {
        assertEquals("a", JellyfinTmdbLookup.pick(listOf(Candidate("a", null)), 603)?.id)
        assertEquals("a", JellyfinTmdbLookup.pick(listOf(Candidate("a", "")), 603)?.id)
    }

    @Test
    fun `several unverified candidates are ambiguous, and a different tmdb id is a different title`() {
        assertNull(JellyfinTmdbLookup.pick(listOf(Candidate("a", null), Candidate("b", null)), 603))
        assertNull(JellyfinTmdbLookup.pick(listOf(Candidate("a", "5")), 603))
        assertNull(JellyfinTmdbLookup.pick(emptyList(), 603))
    }
}
