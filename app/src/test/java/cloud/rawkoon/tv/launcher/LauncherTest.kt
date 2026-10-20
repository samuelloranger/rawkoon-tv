package cloud.rawkoon.tv.launcher

import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTest {
    private fun item(
        id: String,
        kind: MediaItem.Kind = MediaItem.Kind.MOVIE,
        poster: String? = "https://s/$id/poster?tag=a&quality=90",
        thumb: String? = "https://s/$id/thumb?tag=a&quality=90",
        resumeMs: Long = 0,
        runtime: Int? = 100,
        series: String? = null,
    ) = MediaItem(
        id = id, kind = kind, title = "Title $id", seriesName = if (kind == MediaItem.Kind.EPISODE) "Show" else null,
        episodeLabel = null, overview = "About $id", year = 2026, runtimeMinutes = runtime, officialRating = null,
        communityRating = null, posterUrl = poster, thumbUrl = thumb, backdropUrl = null, logoUrl = null, progress = null,
        played = false, seriesId = series, seasonId = if (series != null) "season" else null,
        seasonNumber = if (series != null) 2 else null, episodeNumber = if (series != null) 5 else null, resumeMs = resumeMs,
    )

    private fun episode(id: String, series: String = "show", thumb: String? = "https://s/$id/thumb?tag=a") =
        item(id, MediaItem.Kind.EPISODE, thumb = thumb, series = series)

    private fun spec(item: MediaItem) = ProgramSpec.from(item, "profile")!!

    @Test
    fun `movies and shows use the poster at 2 to 3, sized`() {
        val movie = spec(item("m"))
        assertEquals(PreviewPrograms.ASPECT_RATIO_2_3, movie.aspect)
        assertEquals("https://s/m/poster?tag=a&quality=90&maxWidth=342", movie.artUrl)
        assertEquals(PreviewPrograms.TYPE_MOVIE, movie.type)
        val show = spec(item("s", MediaItem.Kind.SERIES))
        assertEquals(PreviewPrograms.ASPECT_RATIO_2_3, show.aspect)
        assertEquals(PreviewPrograms.TYPE_TV_SERIES, show.type)
    }

    @Test
    fun `an episode uses its still at 16 to 9`() {
        val ep = spec(episode("e"))
        assertEquals(PreviewPrograms.ASPECT_RATIO_16_9, ep.aspect)
        assertEquals("https://s/e/thumb?tag=a&maxWidth=640", ep.artUrl)
        assertEquals(PreviewPrograms.TYPE_TV_EPISODE, ep.type)
    }

    @Test
    fun `the aspect follows the picture used when the preferred one is missing`() {
        assertEquals(PreviewPrograms.ASPECT_RATIO_2_3, spec(episode("e", thumb = null)).aspect)
        val noPoster = spec(item("m", poster = null))
        assertEquals(PreviewPrograms.ASPECT_RATIO_16_9, noPoster.aspect)
        assertTrue(noPoster.artUrl.startsWith("https://s/m/thumb"))
    }

    @Test
    fun `an item with no picture or no page to open is left out`() {
        assertNull(ProgramSpec.from(item("m", poster = null, thumb = null), "p"))
        assertNull(ProgramSpec.from(item("x", MediaItem.Kind.SEASON), "p"))
        assertNull(ProgramSpec.from(item("e", MediaItem.Kind.EPISODE, series = null), "p"))
    }

    @Test
    fun `an episode is titled by its show, with the episode title, numbers and a link to its season`() {
        val ep = spec(episode("e"))
        assertEquals("Show", ep.title)
        assertEquals("Title e", ep.episodeTitle)
        assertEquals(2, ep.seasonNumber)
        assertEquals(5, ep.episodeNumber)
        assertEquals("About e", ep.description)
        assertEquals("show", ep.link.seriesId)
        assertEquals("season", ep.link.seasonId)
        assertEquals("profile", ep.link.profileId)
        val movie = spec(item("m"))
        assertEquals("Title m", movie.title)
        assertNull(movie.episodeTitle)
        assertNull(movie.episodeNumber)
        assertEquals(6_000_000, movie.durationMs)
    }

    @Test
    fun `two minutes in or more continues, anything less is up next`() {
        assertEquals(WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE, spec(item("a", resumeMs = 120_000)).watchNextType())
        assertEquals(120_000, spec(item("a", resumeMs = 120_000)).positionMs)
        assertEquals(WatchNextPrograms.WATCH_NEXT_TYPE_NEXT, spec(item("b", resumeMs = 119_999)).watchNextType())
        assertEquals(WatchNextPrograms.WATCH_NEXT_TYPE_NEXT, spec(item("c")).watchNextType())
    }

    @Test
    fun `a position needs a known length to continue`() {
        assertFalse(spec(item("a", resumeMs = 300_000, runtime = null)).resumable)
        assertFalse(spec(item("a", resumeMs = 7_000_000, runtime = 100)).resumable)
    }

    private fun row(id: Long, item: String?, browsable: Boolean = true) = WatchNextRow(id, item, browsable)

    @Test
    fun `watch next removes what the server dropped, updates what stays and adds what is new`() {
        val diff = WatchNextDiff.of(listOf(row(1, "a"), row(2, "b")), listOf("b", "c"))
        assertEquals(listOf(1L), diff.remove)
        assertEquals(mapOf(2L to "b"), diff.update)
        assertEquals(listOf("c"), diff.add)
    }

    @Test
    fun `a row the user dismissed is neither updated nor added again`() {
        val diff = WatchNextDiff.of(listOf(row(1, "a", browsable = false)), listOf("a", "b"))
        assertTrue(diff.remove.isEmpty())
        assertTrue(diff.update.isEmpty())
        assertEquals(listOf("b"), diff.add)
    }

    @Test
    fun `a dismissed row goes once its item is no longer wanted, so it can come back later`() {
        val diff = WatchNextDiff.of(listOf(row(1, "a", browsable = false)), listOf("b"))
        assertEquals(listOf(1L), diff.remove)
        assertEquals(listOf("b"), diff.add)
    }

    @Test
    fun `duplicates and rows without an id are cleaned up`() {
        val diff = WatchNextDiff.of(listOf(row(1, "a"), row(2, "a"), row(3, null), row(4, "b", false), row(5, "b")), listOf("a", "b"))
        assertEquals(setOf(2L, 3L, 5L), diff.remove.toSet())
        assertEquals(mapOf(1L to "a"), diff.update)
        assertTrue(diff.add.isEmpty())
    }

    private fun rows(vararg rows: HomeRow) = rows.toList()

    private fun homeRow(id: String, vararg items: MediaItem) = HomeRow(id, id, items.toList(), false)

    @Test
    fun `next up skips shows already being resumed`() {
        val feed = LauncherFeed.from(
            rows(
                homeRow("resume", episode("r1", series = "x"), item("m")),
                homeRow("nextup", episode("n1", series = "x"), episode("n2", series = "y")),
            ),
        )
        assertEquals(listOf("r1", "m", "n2"), feed.watchNext.map { it.id })
    }

    @Test
    fun `recent takes the libraries in turn and is capped`() {
        val movies = (1..15).map { item("m$it") }.toTypedArray()
        val shows = (1..15).map { item("s$it", MediaItem.Kind.SERIES) }.toTypedArray()
        val feed = LauncherFeed.from(rows(homeRow("latest:1", *movies), homeRow("latest:2", *shows), homeRow("other", item("z"))))
        assertEquals(LauncherFeed.RECENT_LIMIT, feed.recent.size)
        assertEquals(listOf("m1", "s1", "m2", "s2"), feed.recent.take(4).map { it.id })
        assertTrue(feed.recent.none { it.id == "z" })
    }

    @Test
    fun `a link survives the trip through its extras`() {
        val link = spec(episode("e")).link
        val extras = mapOf(
            LauncherLink.PROFILE to link.profileId, LauncherLink.ITEM to link.itemId, LauncherLink.KIND to link.kind.name,
            LauncherLink.SERIES to link.seriesId, LauncherLink.SEASON to link.seasonId, LauncherLink.TITLE to link.title, LauncherLink.POSTER to link.posterUrl,
        )
        assertEquals(link, LauncherLink.parse { extras[it] })
    }

    @Test
    fun `an intent that isn't a launcher link, or can't open a page, parses to nothing`() {
        assertNull(LauncherLink.parse { null })
        val base = mapOf(LauncherLink.PROFILE to "p", LauncherLink.ITEM to "i", LauncherLink.KIND to "EPISODE")
        assertNull(LauncherLink.parse { base[it] })
        assertNull(LauncherLink.parse { (base + (LauncherLink.KIND to "OTHER"))[it] })
        assertEquals(MediaItem.Kind.EPISODE, LauncherLink.parse { (base + (LauncherLink.SERIES to "s"))[it] }?.kind)
    }

    @Test
    fun `the same profile syncs again after a few minutes, another one at once`() {
        val gap = LauncherSchedule.MIN_GAP_MS
        assertTrue(LauncherSchedule.due(0, null, 10, "a"))
        assertFalse(LauncherSchedule.due(1_000, "a", 1_000 + gap - 1, "a"))
        assertTrue(LauncherSchedule.due(1_000, "a", 1_000 + gap, "a"))
        assertTrue(LauncherSchedule.due(1_000, "a", 1_001, "b"))
    }
}
