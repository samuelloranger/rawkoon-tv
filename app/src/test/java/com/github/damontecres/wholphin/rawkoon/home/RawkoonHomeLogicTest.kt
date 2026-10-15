package com.github.damontecres.wholphin.rawkoon.home

import com.github.damontecres.wholphin.data.model.HomePageSettings
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.RawkoonRequestsRow
import com.github.damontecres.wholphin.data.model.RawkoonUpcomingRow
import com.github.damontecres.wholphin.rawkoon.LibraryEntry
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.rawkoon.TitleStatus
import com.github.damontecres.wholphin.rawkoon.seerr.RkRequest
import com.github.damontecres.wholphin.rawkoon.seerr.RkRequestUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class RawkoonHomeLogicTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    // Wednesday
    private val today = LocalDate.of(2026, 10, 14)

    private fun label(
        date: LocalDate,
        locale: Locale,
    ) = RawkoonHomeLogic.dateLabel(date, today, locale)

    @Test
    fun today_and_tomorrow_are_words() {
        assertEquals(DateLabel.Today, label(today, en))
        assertEquals(DateLabel.Tomorrow, label(today.plusDays(1), fr))
    }

    @Test
    fun the_next_six_days_are_a_weekday_in_the_locale() {
        assertEquals(DateLabel.Text("Friday"), label(today.plusDays(2), en))
        assertEquals(DateLabel.Text("Vendredi"), label(today.plusDays(2), fr))
        assertEquals(DateLabel.Text("Tuesday"), label(today.plusDays(6), en))
    }

    @Test
    fun later_dates_are_short_dates_with_the_year_only_when_it_differs() {
        assertEquals(DateLabel.Text("Oct 21"), label(today.plusDays(7), en))
        assertEquals(DateLabel.Text("21 oct."), label(today.plusDays(7), fr))
        assertEquals(DateLabel.Text("Mar 5, 2027"), label(LocalDate.of(2027, 3, 5), en))
        assertEquals(DateLabel.Text("5 mars 2027"), label(LocalDate.of(2027, 3, 5), fr))
    }

    private fun item(
        id: String,
        date: String?,
        title: String = id,
        type: String = "movie",
        libraryId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        end: Int? = null,
        count: Int? = null,
    ) = RkUpcomingItem(
        id = id,
        title = title,
        mediaType = type,
        releaseDate = date,
        libraryId = libraryId,
        seasonNumber = season,
        episodeNumber = episode,
        episodeNumberEnd = end,
        episodeCount = count,
    )

    @Test
    fun upcoming_keeps_today_or_later_soonest_first() {
        val cards =
            RawkoonHomeLogic.upcoming(
                listOf(
                    item("movie-3", "2026-11-01"),
                    item("movie-1", "2026-10-13"),
                    item("movie-2", "2026-10-14T00:00:00.000Z"),
                    item("movie-4", null),
                ),
                today,
            )
        assertEquals(listOf(2, 3), cards.map { it.tmdbId })
    }

    @Test
    fun upcoming_is_capped_and_reads_ids_episodes_and_library_marks() {
        val many = (1..30).map { item("movie-$it", LocalDate.of(2026, 11, 1).plusDays(it.toLong()).toString()) }
        assertEquals(20, RawkoonHomeLogic.upcoming(many, today).size)
        val card =
            RawkoonHomeLogic
                .upcoming(listOf(item("tv-77-2026-10-20-s2e5", "2026-10-20", type = "tv", libraryId = 4, season = 2, episode = 5)), today)
                .single()
        assertEquals(77, card.tmdbId)
        assertEquals(false, card.isMovie)
        assertEquals(EpisodePill.Numbers("S2 · E5"), card.pill)
        assertTrue(card.inLibrary)
        // An item only a downloader knows has no TMDB id; the TMDB link is the other source
        assertTrue(RawkoonHomeLogic.upcoming(listOf(item("sonarr-9-2026-10-20", "2026-10-20", type = "tv")), today).isEmpty())
        val byUrl = item("sonarr-9", "2026-10-20", type = "tv").copy(tmdbUrl = "https://www.themoviedb.org/tv/555")
        assertEquals(555, RawkoonHomeLogic.upcoming(listOf(byUrl), today).single().tmdbId)
    }

    private fun pill(
        season: Int? = null,
        episode: Int? = null,
        end: Int? = null,
        count: Int? = null,
        type: String = "tv",
    ) = RawkoonHomeLogic
        .upcoming(listOf(item("$type-1-2026-10-20", "2026-10-20", type = type, season = season, episode = episode, end = end, count = count)), today)
        .single()
        .pill

    @Test
    fun the_pill_says_a_single_episode_a_range_a_count_or_nothing() {
        assertEquals(EpisodePill.Numbers("S1 · E7"), pill(season = 1, episode = 7))
        assertEquals(EpisodePill.Numbers("S1 · E7"), pill(season = 1, episode = 7, end = 7, count = 1))
        assertEquals(EpisodePill.Numbers("S1 · E3–4"), pill(season = 1, episode = 3, end = 4, count = 2))
        assertEquals(EpisodePill.Count(2), pill(count = 2))
        assertEquals(EpisodePill.Count(3), pill(season = 1, count = 3))
        assertNull(pill())
        assertNull(pill(count = 1))
        // A movie never has one
        assertNull(pill(type = "movie", count = 2))
    }

    @Test
    fun older_servers_without_the_new_fields_still_decode() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<RkUpcoming>("""{"enabled":true,"items":[{"id":"tv-1-2026-10-20","title":"T","media_type":"tv","release_date":"2026-10-20"}]}""")
        val card = RawkoonHomeLogic.upcoming(old.items, today).single()
        assertEquals(1, card.episodeCount)
        assertNull(card.pill)
        val new = json.decodeFromString<RkUpcoming>("""{"enabled":true,"items":[{"id":"tv-1","title":"T","media_type":"tv","release_date":"2026-10-20","season_number":1,"episode_number":3,"episode_number_end":4,"episode_count":2}]}""")
        assertEquals(EpisodePill.Numbers("S1 · E3–4"), RawkoonHomeLogic.upcoming(new.items, today).single().pill)
    }

    @Test
    fun duplicate_upcoming_items_are_dropped_by_id() {
        val cards =
            RawkoonHomeLogic.upcoming(
                listOf(item("movie-1", "2026-10-20", "First"), item("movie-1", "2026-10-20", "Again"), item("movie-2", "2026-10-21")),
                today,
            )
        assertEquals(listOf("First", "movie-2"), cards.map { it.title })
    }

    private val now = Instant.parse("2026-10-14T12:00:00Z")

    private fun request(
        id: Int,
        status: String,
        created: String = "2026-10-01T00:00:00.000Z",
        decided: String? = null,
        by: String = "me",
        type: String = "movie",
        tmdb: Int? = id,
    ) = RkRequest(id, tmdb, type, "T$id", null, 2000, status, RkRequestUser(by, "n"), created, decided)

    @Test
    fun requests_are_the_users_own_movies_and_shows_only() {
        val result =
            RawkoonHomeLogic.requests(
                listOf(
                    request(1, "pending"),
                    request(2, "pending", by = "someone"),
                    request(3, "pending", type = "book", tmdb = null),
                    request(4, "approved", type = "show", created = "2026-10-05T00:00:00.000Z"),
                ),
                "me",
                now,
            )
        assertEquals(listOf(4, 1), result.map { it.id })
    }

    @Test
    fun requests_are_ordered_active_then_available_then_denied_newest_first() {
        val result =
            RawkoonHomeLogic.requests(
                listOf(
                    request(1, "denied", decided = "2026-10-13T00:00:00.000Z"),
                    request(2, "available", decided = "2026-10-12T00:00:00.000Z"),
                    request(3, "pending", created = "2026-09-01T00:00:00.000Z"),
                    request(4, "approved", created = "2026-10-10T00:00:00.000Z"),
                    request(5, "available", decided = "2026-10-13T00:00:00.000Z"),
                    request(6, "denied", decided = "2026-09-20T00:00:00.000Z"),
                    request(7, "available", decided = "2026-09-20T00:00:00.000Z"),
                ),
                "me",
                now,
            )
        // Old pending stays (it is still active); available and denied older than 14 days are dropped
        assertEquals(listOf(4, 3, 5, 2, 1), result.map { it.id })
    }

    @Test
    fun requests_are_capped_at_twenty() {
        val many = (1..30).map { request(it, "pending") }
        assertEquals(20, RawkoonHomeLogic.requests(many, "me", now).size)
    }

    private fun live(
        status: String,
        episodes: Int? = null,
        done: Int? = null,
    ) = TitleStatus(library = LibraryEntry(1, status, episodes, done))

    @Test
    fun the_chip_follows_the_request_and_for_approved_ones_the_library() {
        assertEquals(RequestChip.Pending, RawkoonHomeLogic.chip(request(1, "pending"), null))
        assertEquals(RequestChip.Denied, RawkoonHomeLogic.chip(request(1, "denied"), null))
        assertEquals(RequestChip.Ready, RawkoonHomeLogic.chip(request(1, "available"), null))
        val approved = request(1, "approved")
        assertEquals(RequestChip.Approved, RawkoonHomeLogic.chip(approved, null))
        assertEquals(RequestChip.Approved, RawkoonHomeLogic.chip(approved, live("wanted")))
        assertEquals(RequestChip.Downloading(40), RawkoonHomeLogic.chip(approved, live("downloading", 10, 4)))
        assertEquals(RequestChip.Downloading(null), RawkoonHomeLogic.chip(approved, live("downloading")))
        assertEquals(RequestChip.Downloading(99), RawkoonHomeLogic.chip(approved, live("downloading", 100, 100)))
        assertEquals(RequestChip.Ready, RawkoonHomeLogic.chip(approved, live("downloaded")))
        assertEquals(RequestChip.Ready, RawkoonHomeLogic.chip(approved, live("upgrading")))
    }

    @Test
    fun only_approved_requests_get_a_live_status_call() {
        assertTrue(RawkoonHomeLogic.needsLiveStatus(request(1, "approved")))
        assertEquals(false, RawkoonHomeLogic.needsLiveStatus(request(1, "pending")))
        assertEquals(false, RawkoonHomeLogic.needsLiveStatus(request(1, "available")))
    }

    @Test
    fun the_rows_survive_the_home_settings_json() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = HomePageSettings(listOf(HomeRowConfig.ContinueWatching(), RawkoonUpcomingRow(), RawkoonRequestsRow()), 1)
        val decoded = json.decodeFromString<HomePageSettings>(json.encodeToString(HomePageSettings.serializer(), settings))
        assertEquals(settings, decoded)
    }

    private fun rows(settings: RawkoonSettingsRepository) = RawkoonHomeRows(mockk(), mockk(), settings, mockk(), mockk(), mockk(), mockk())

    @Test
    fun a_saved_home_page_gets_the_rows_once_right_after_continue_watching() =
        runBlocking {
            val settings = mockk<RawkoonSettingsRepository>(relaxed = true)
            coEvery { settings.homeRowsAddedFor(any()) } returns false
            val user = UUID.randomUUID()
            val saved = HomePageSettings(listOf(HomeRowConfig.NextUp(), HomeRowConfig.ContinueWatching(), HomeRowConfig.Recordings()), 1)
            val updated = rows(settings).withRows(user, saved)!!
            assertEquals(
                listOf("NextUp", "ContinueWatching", "RawkoonUpcomingRow", "RawkoonRequestsRow", "Recordings"),
                updated.rows.map { it::class.simpleName },
            )
            coVerify { settings.markHomeRowsAdded(user.toString()) }
        }

    @Test
    fun the_rows_are_not_added_again_after_the_user_removed_them() =
        runBlocking {
            val settings = mockk<RawkoonSettingsRepository>(relaxed = true)
            coEvery { settings.homeRowsAddedFor(any()) } returns true
            assertNull(rows(settings).withRows(UUID.randomUUID(), HomePageSettings(listOf(HomeRowConfig.NextUp()), 1)))
        }
}
