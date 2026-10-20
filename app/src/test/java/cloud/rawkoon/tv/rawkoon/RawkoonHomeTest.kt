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
        val cards = UpcomingRows.upcoming(
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
        assertNull(UpcomingRows.tmdbIdOf(entry("lib-7")))
        assertEquals(42, UpcomingRows.tmdbIdOf(entry("tv-42-s1e1")))
        assertEquals(9, UpcomingRows.tmdbIdOf(entry("x", url = "https://www.themoviedb.org/tv/9")))
        assertEquals(emptyList<UpcomingCard>(), UpcomingRows.upcoming(listOf(entry("lib-7")), today))
    }

    @Test
    fun `an episode pill gives numbers, a range, or a count`() {
        fun pill(e: UpcomingEntry) = UpcomingRows.episodePill(UpcomingRows.upcoming(listOf(e), today).single(), FakeText)
        assertEquals("S1 · E7", pill(entry("tv-1", season = 1, episode = 7)))
        assertEquals("S1 · E3–4", pill(entry("tv-1", season = 1, episode = 3, end = 4)))
        assertEquals("S1 · E3", pill(entry("tv-1", season = 1, episode = 3, end = 3)))
        assertEquals("2 episodes", pill(entry("tv-1", count = 2)))
        assertNull(pill(entry("tv-1")))
        assertNull(pill(entry("movie-1", season = 1, episode = 1)))
    }

    @Test
    fun `dates read as words close by and as dates further out`() {
        fun text(days: Long, locale: Locale = Locale.ENGLISH) = UpcomingRows.dateText(today.plusDays(days), today, locale, FakeText)
        assertEquals("Today", text(0))
        assertEquals("Tomorrow", text(1))
        assertEquals("Thursday", text(3))
        assertEquals("Oct 31", text(12))
        assertEquals("Jan 2, 2027", text(75))
        assertEquals("31 oct.", text(12, Locale.FRENCH))
    }

    @Test
    fun `an upcoming card shows its date and its pill, or that the library has it`() {
        val card = { e: UpcomingEntry -> UpcomingRows.item(UpcomingRows.upcoming(listOf(e), today).single(), FakeText, today, Locale.ENGLISH) }
        val episode = card(entry("tv-1", "Show", "2026-10-20", season = 2, episode = 1))
        assertEquals("Tomorrow", episode.tmdb!!.subtitle)
        assertEquals(Badge("S2 · E1", Badge.Tone.ACTIVE), episode.tmdb!!.badge)
        assertEquals("Tomorrow · S2 · E1", episode.metaLine())
        assertEquals(MediaItem.Kind.SERIES, episode.kind)
        assertEquals(1, episode.tmdb!!.id)

        val movie = card(entry("movie-2", "Movie", "2026-10-30", library = 3))
        assertEquals(Badge("in library", Badge.Tone.READY), movie.tmdb!!.badge)
        assertEquals(true, movie.tmdb!!.inLibrary)
        assertNull(card(entry("movie-3")).tmdb!!.badge)
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
        assertEquals(listOf(3, 2, 1, 4), RequestRows.requests(all, "me", now).map { it.id })
    }

    @Test
    fun `requests are capped`() {
        val many = (1..30).map { request(it, "pending") }
        assertEquals(RawkoonHome.MAX_ITEMS, RequestRows.requests(many, "me", now).size)
    }

    @Test
    fun `a request card carries its state as a badge`() {
        val item = RequestRows.item(request(3, "approved"), TitleState.Processing(true, 40), FakeText)
        assertEquals("request-3", item.id)
        assertEquals(Badge("downloading 40", Badge.Tone.ACTIVE), item.tmdb!!.badge)
        assertEquals("2024 · downloading 40", item.metaLine())
        assertEquals(false, item.tmdb!!.inLibrary)
        val ready = RequestRows.item(request(4, "available"), TitleState.Available, FakeText)
        assertEquals(true, ready.tmdb!!.inLibrary)
        assertEquals(Badge.Tone.READY, ready.tmdb!!.badge!!.tone)
    }

    @Test
    fun `the upcoming row is left out when nothing is upcoming`() {
        assertNull(UpcomingRows.row(emptyList(), FakeText, today, Locale.ENGLISH))
        val row = UpcomingRows.row(listOf(entry("movie-1")), FakeText, today, Locale.ENGLISH)!!
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

    private fun row(id: String) = HomeRow(id, id, emptyList(), false)

    @Test
    fun `jellyfin rows show at once with the rawkoon rows already on screen`() {
        val previous = listOf(row(RawkoonHome.UPCOMING_ID), row(RawkoonHome.REQUESTS_ID))
        val jellyfin = listOf(row("resume"), row("latest:a"))
        val interim = RawkoonHome.merge(jellyfin, previous)
        assertEquals(listOf("resume", RawkoonHome.UPCOMING_ID, RawkoonHome.REQUESTS_ID, "latest:a"), interim.map { it.id })
    }

    @Test
    fun `a failed or slow rawkoon refresh keeps the rows shown before`() {
        val shown = listOf(row("resume"), row(RawkoonHome.UPCOMING_ID), row("latest:a"))
        assertEquals(listOf(RawkoonHome.UPCOMING_ID), RawkoonHome.rowsFor(RawkoonOutcome.Unavailable, shown).map { it.id })
    }

    @Test
    fun `a refused session or no session shows no rawkoon rows, a good answer replaces them`() {
        val shown = listOf(row(RawkoonHome.UPCOMING_ID), row(RawkoonHome.REQUESTS_ID))
        assertEquals(emptyList<HomeRow>(), RawkoonHome.rowsFor(RawkoonOutcome.Refused, shown))
        assertEquals(emptyList<HomeRow>(), RawkoonHome.rowsFor(RawkoonOutcome.NoSession, shown))
        val fresh = listOf(row(RawkoonHome.REQUESTS_ID))
        assertEquals(fresh, RawkoonHome.rowsFor(RawkoonOutcome.Loaded(fresh), shown))
    }

    @Test
    fun `an entry without an id is never taken for a repeat, one with an id is kept once`() {
        val cards = UpcomingRows.upcoming(
            listOf(entry("", "A", url = "https://www.themoviedb.org/movie/1"), entry("", "B", url = "https://www.themoviedb.org/movie/2"), entry("movie-3", "C"), entry("movie-3", "C again")),
            today,
        )
        assertEquals(listOf("A", "B", "C"), cards.map { it.title })
    }

    @Test
    fun `a cached upcoming row words its dates again and drops what has passed`() {
        val cached = UpcomingRows.row(listOf(entry("movie-1", "Soon", "2026-10-20"), entry("movie-2", "Later", "2026-10-30")), FakeText, today, Locale.ENGLISH)!!
        assertEquals(listOf("Tomorrow", "Oct 30"), cached.items.map { it.tmdb!!.subtitle })
        val nextDay = RawkoonHome.refreshed(listOf(cached), FakeText, today.plusDays(1), Locale.ENGLISH).single()
        assertEquals(listOf("Today", "Oct 30"), nextDay.items.map { it.tmdb!!.subtitle })
        val later = RawkoonHome.refreshed(listOf(cached), FakeText, today.plusDays(2), Locale.ENGLISH).single()
        assertEquals(listOf("Later"), later.items.map { it.title })
        assertEquals(emptyList<HomeRow>(), RawkoonHome.refreshed(listOf(cached), FakeText, today.plusDays(20), Locale.ENGLISH))
    }

    @Test
    fun `refreshing leaves the requests row alone`() {
        val requests = row(RawkoonHome.REQUESTS_ID)
        assertEquals(listOf(requests), RawkoonHome.refreshed(listOf(requests), FakeText, today, Locale.ENGLISH))
    }

    @Test
    fun `discover shows the wanted genres only, movies then shows`() {
        val movies = listOf(Genre(28, "Action"), Genre(99, "Documentary"), Genre(35, "Comedy"))
        val shows = listOf(Genre(80, "Crime"), Genre(16, "Animation"))
        val rows = DiscoverRows.genreRows(movies, shows)
        assertEquals(listOf(true to 28, true to 35, false to 80), rows.map { it.isMovie to it.genre.id })
    }
}
