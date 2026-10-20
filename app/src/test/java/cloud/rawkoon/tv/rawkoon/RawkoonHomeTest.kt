package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class RawkoonHomeTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val now = Instant.parse("2026-10-19T12:00:00Z")

    private fun entry(
        id: String,
        title: String = "Title",
        date: String? = "2026-10-25",
        movie: Boolean = id.startsWith("movie"),
        library: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        end: Int? = null,
        count: Int? = null,
        url: String? = null,
    ) = UpcomingEntry(id, title, movie, date, "https://img.example.com/$id.jpg", null, null, url, library, season, episode, end, count)

    @Test
    fun `upcoming keeps today and later, soonest first, once each`() {
        val cards = RawkoonHome.upcoming(
            listOf(
                entry("movie-1", "B", "2026-10-30"),
                entry("movie-2", "A", "2026-10-19"),
                entry("movie-2", "A again", "2026-10-19"),
                entry("movie-3", "Past", "2026-10-18"),
                entry("movie-4", "Undated", null),
                entry("tv-5", "Same day, later in the alphabet", "2026-10-19"),
            ),
            today,
        )
        assertEquals(listOf("A", "Same day, later in the alphabet", "B"), cards.map { it.title })
    }

    @Test
    fun `an entry known only to a downloader has no tmdb id and is left out`() {
        assertNull(RawkoonHome.tmdbIdOf(entry("lib-7")))
        assertEquals(42, RawkoonHome.tmdbIdOf(entry("tv-42-s1e1")))
        assertEquals(9, RawkoonHome.tmdbIdOf(entry("x", url = "https://www.themoviedb.org/tv/9")))
        assertEquals(emptyList<UpcomingCard>(), RawkoonHome.upcoming(listOf(entry("lib-7")), today))
    }

    @Test
    fun `an episode pill gives numbers, a range, or a count`() {
        fun pill(e: UpcomingEntry) = RawkoonHome.episodePill(RawkoonHome.upcoming(listOf(e), today).single(), FakeText)
        assertEquals("S1 · E7", pill(entry("tv-1", season = 1, episode = 7)))
        assertEquals("S1 · E3–4", pill(entry("tv-1", season = 1, episode = 3, end = 4)))
        assertEquals("S1 · E3", pill(entry("tv-1", season = 1, episode = 3, end = 3)))
        assertEquals("2 episodes", pill(entry("tv-1", count = 2)))
        assertNull(pill(entry("tv-1")))
        assertNull(pill(entry("movie-1", season = 1, episode = 1)))
    }

    @Test
    fun `dates read as words close by and as dates further out`() {
        fun text(days: Long, locale: Locale = Locale.ENGLISH) = RawkoonHome.dateText(today.plusDays(days), today, locale, FakeText)
        assertEquals("Today", text(0))
        assertEquals("Tomorrow", text(1))
        assertEquals("Thursday", text(3))
        assertEquals("Oct 31", text(12))
        assertEquals("Jan 2, 2027", text(75))
        assertEquals("31 oct.", text(12, Locale.FRENCH))
    }

    @Test
    fun `an upcoming card shows its date and its pill, or that the library has it`() {
        val card = { e: UpcomingEntry -> RawkoonHome.upcomingItem(RawkoonHome.upcoming(listOf(e), today).single(), FakeText, today, Locale.ENGLISH) }
        val episode = card(entry("tv-1", "Show", "2026-10-20", season = 2, episode = 1))
        assertEquals("Tomorrow", episode.subtitle)
        assertEquals(Badge("S2 · E1", Badge.Tone.ACTIVE), episode.badge)
        assertEquals("Tomorrow · S2 · E1", episode.metaLine())
        assertEquals(MediaItem.Kind.SERIES, episode.kind)
        assertEquals(1, episode.tmdbId)

        val movie = card(entry("movie-2", "Movie", "2026-10-30", library = 3))
        assertEquals(Badge("in library", Badge.Tone.READY), movie.badge)
        assertEquals(true, movie.inLibrary)
        assertNull(card(entry("movie-3")).badge)
    }

    private fun request(id: Int, status: String, by: String = "me", created: String = "2026-10-10T10:00:00Z", decided: String? = null, type: String = "movie", tmdb: Int? = id) =
        RequestSummary(id, tmdb, type == "movie", type, "Title $id", null, 2024, status, by, created, decided)

    @Test
    fun `requests show the account's own, active first and then recent results`() {
        val all = listOf(
            request(1, "available", decided = "2026-10-17T10:00:00Z"),
            request(2, "pending", created = "2026-10-12T10:00:00Z"),
            request(3, "approved", created = "2026-10-15T10:00:00Z"),
            request(4, "denied", decided = "2026-10-18T10:00:00Z"),
            request(5, "pending", by = "someone-else"),
            request(6, "available", decided = "2026-09-01T10:00:00Z"),
            request(7, "denied", decided = "2026-09-01T10:00:00Z"),
            request(8, "pending", type = "book", tmdb = null),
            request(9, "pending", tmdb = null),
            request(10, "cancelled"),
        )
        assertEquals(listOf(3, 2, 1, 4), RawkoonHome.requests(all, "me", now).map { it.id })
    }

    @Test
    fun `requests are capped`() {
        val many = (1..30).map { request(it, "pending") }
        assertEquals(RawkoonHome.MAX_ITEMS, RawkoonHome.requests(many, "me", now).size)
    }

    @Test
    fun `a request card carries its state as a badge`() {
        val item = RawkoonHome.requestItem(request(3, "approved"), TitleState.Processing(true, 40), FakeText)
        assertEquals("request-3", item.id)
        assertEquals(Badge("downloading 40", Badge.Tone.ACTIVE), item.badge)
        assertEquals("2024 · downloading 40", item.metaLine())
        assertEquals(false, item.inLibrary)
        val ready = RawkoonHome.requestItem(request(4, "available"), TitleState.Available, FakeText)
        assertEquals(true, ready.inLibrary)
        assertEquals(Badge.Tone.READY, ready.badge!!.tone)
    }

    @Test
    fun `the upcoming row is left out when nothing is upcoming`() {
        assertNull(RawkoonHome.upcomingRow(emptyList(), FakeText, today, Locale.ENGLISH))
        val row = RawkoonHome.upcomingRow(listOf(entry("movie-1")), FakeText, today, Locale.ENGLISH)!!
        assertEquals(RawkoonHome.UPCOMING_ID, row.id)
        assertEquals("Coming soon", row.title)
        assertEquals(false, row.landscape)
    }

    @Test
    fun `the rawkoon rows go after continue watching and next up`() {
        fun row(id: String) = HomeRow(id, id, emptyList(), false)
        val jellyfin = listOf(row("resume"), row("nextup"), row("latest:a"), row("latest:b"))
        val rawkoon = listOf(row(RawkoonHome.UPCOMING_ID), row(RawkoonHome.REQUESTS_ID))
        assertEquals(
            listOf("resume", "nextup", RawkoonHome.UPCOMING_ID, RawkoonHome.REQUESTS_ID, "latest:a", "latest:b"),
            RawkoonHome.merge(jellyfin, rawkoon).map { it.id },
        )
        // Nothing resumable: they open the page
        assertEquals(listOf(RawkoonHome.UPCOMING_ID, "latest:a"), RawkoonHome.merge(listOf(row("latest:a")), listOf(row(RawkoonHome.UPCOMING_ID))).map { it.id })
        val same = listOf(row("resume"))
        assertSame(same, RawkoonHome.merge(same, emptyList()))
    }
}
