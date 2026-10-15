package com.github.damontecres.wholphin.rawkoon.seerr

import com.github.damontecres.wholphin.rawkoon.seerr.JellyfinTmdbLookup.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class JellyfinTmdbLookupTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    @Test
    fun the_candidate_with_the_verified_tmdb_id_wins() {
        assertEquals(b, JellyfinTmdbLookup.pick(listOf(Candidate(a, "1"), Candidate(b, "603"), Candidate(c, null)), 603))
    }

    @Test
    fun a_lone_name_match_without_a_tmdb_id_is_accepted() {
        assertEquals(a, JellyfinTmdbLookup.pick(listOf(Candidate(a, null)), 603))
        assertEquals(a, JellyfinTmdbLookup.pick(listOf(Candidate(a, "")), 603))
    }

    @Test
    fun a_lone_match_with_a_different_tmdb_id_is_rejected() {
        assertNull(JellyfinTmdbLookup.pick(listOf(Candidate(a, "1")), 603))
    }

    @Test
    fun several_unverified_candidates_are_ambiguous_so_the_caller_falls_back() {
        assertNull(JellyfinTmdbLookup.pick(listOf(Candidate(a, null), Candidate(b, "7")), 603))
        assertNull(JellyfinTmdbLookup.pick(emptyList(), 603))
    }
}
