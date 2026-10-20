package cloud.rawkoon.tv.rawkoon

import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleStateTest {
    private fun status(library: LibraryEntry? = null, request: RequestEntry? = null) = TitleStatus(library, request)

    @Test
    fun `a title nobody has asked for can be requested`() {
        assertEquals(TitleState.Requestable, TitleState.of(null, isShow = false))
        assertEquals(TitleState.Requestable, TitleState.of(status(), isShow = true))
        assertTrue(TitleState.Requestable.canRequest)
        assertFalse(TitleState.Pending.canRequest)
    }

    @Test
    fun `a request's own status maps to its state`() {
        assertEquals(TitleState.Pending, TitleState.of(status(request = RequestEntry(1, "pending", null)), false))
        assertEquals(TitleState.Processing(false, null), TitleState.of(status(request = RequestEntry(1, "approved", null)), false))
        assertEquals(TitleState.Available, TitleState.of(status(request = RequestEntry(1, "available", null)), false))
        assertEquals(TitleState.Refused("Too niche"), TitleState.of(status(request = RequestEntry(1, "denied", "Too niche")), false))
        assertEquals(TitleState.Refused(null), TitleState.of(status(request = RequestEntry(1, "denied", null)), false))
    }

    @Test
    fun `the library entry wins over the request`() {
        val request = RequestEntry(1, "pending", null)
        assertEquals(TitleState.Available, TitleState.of(status(LibraryEntry("downloaded", null, null), request), false))
        assertEquals(TitleState.Available, TitleState.of(status(LibraryEntry("upgrading", null, null), request), false))
        assertEquals(TitleState.Skipped, TitleState.of(status(LibraryEntry("skipped", null, null), request), false))
    }

    @Test
    fun `a show with some episodes is partial, with all of them available`() {
        assertEquals(TitleState.Partial, TitleState.of(status(LibraryEntry("downloading", 10, 4)), isShow = true))
        assertEquals(TitleState.Partial, TitleState.of(status(LibraryEntry("wanted", 0, 2)), isShow = true))
        assertEquals(TitleState.Available, TitleState.of(status(LibraryEntry("returning", 10, 10)), isShow = true))
        assertTrue(TitleState.Partial.inLibrary)
        assertTrue(TitleState.Available.inLibrary)
        assertFalse(TitleState.Pending.inLibrary)
    }

    @Test
    fun `a title on its way knows its progress only for a show`() {
        assertEquals(TitleState.Processing(true, 0), TitleState.of(status(LibraryEntry("downloading", 10, 0)), isShow = true))
        assertEquals(TitleState.Processing(true, null), TitleState.of(status(LibraryEntry("downloading", null, null)), isShow = false))
        assertEquals(TitleState.Processing(false, null), TitleState.of(status(LibraryEntry("wanted", null, null)), isShow = false))
        assertEquals(TitleState.Processing(false, 0), TitleState.of(status(LibraryEntry("wanted", 8, 0)), isShow = true))
    }

    @Test
    fun `a request on the home row only needs the live library for an approved one`() {
        fun request(status: String) = RequestSummary(1, 5, false, "show", "S", null, 2024, status, "u", null, null)
        assertEquals(TitleState.Pending, TitleState.ofRequest(request("pending"), null))
        assertEquals(TitleState.Refused(null), TitleState.ofRequest(request("denied"), null))
        assertEquals(TitleState.Available, TitleState.ofRequest(request("available"), null))
        assertEquals(TitleState.Processing(false, null), TitleState.ofRequest(request("approved"), null))
        assertEquals(TitleState.Processing(true, 50), TitleState.ofRequest(request("approved"), status(LibraryEntry("downloading", 10, 5))))
        assertEquals(TitleState.Available, TitleState.ofRequest(request("approved"), status(LibraryEntry("downloaded", 10, 10))))
    }

    @Test
    fun `tmdb images take a query so appended sizing parameters stay valid`() {
        assertEquals("https://img.example.com/a.jpg?src=tmdb", tmdbImage("https://img.example.com/a.jpg"))
        assertEquals("https://img.example.com/a.jpg?src=tmdb", tmdbImage("https://img.example.com/a.jpg?src=tmdb"))
        assertNull(tmdbImage(null))
        assertEquals("https://img.example.com/a.jpg", rawTmdbImage("https://img.example.com/a.jpg?src=tmdb"))
        assertEquals("https://img.example.com/t/p/w500/l.png?src=tmdb", tmdbLogo("https://img.example.com/t/p/w185/l.png"))
    }

    @Test
    fun `a backdrop is asked for at a screen's size, not as the original`() {
        assertEquals("https://img.example.com/t/p/w1280/b.jpg?src=tmdb", tmdbBackdrop("https://img.example.com/t/p/original/b.jpg"))
        assertEquals("https://img.example.com/t/p/w780/b.jpg?src=tmdb", tmdbBackdrop("https://img.example.com/t/p/w780/b.jpg"))
        assertNull(tmdbBackdrop(null))
    }

    @Test
    fun `a list entry becomes an item, marked when the library has it`() {
        val title = TmdbTitle(101, true, "Example Movie", 2024, "https://img.example.com/a.jpg", "A movie.", 7.25, libraryId = 12, libraryStatus = null)
        val item = title.toItem(FakeText)
        assertEquals("tmdb-movie-101", item.id)
        assertEquals(MediaItem.Kind.MOVIE, item.kind)
        assertEquals(101, item.tmdb!!.id)
        assertTrue(item.tmdb!!.inLibrary)
        assertEquals(Badge("in library", Badge.Tone.READY), item.tmdb!!.badge)
        assertEquals("2024 · in library · ★ 7.3", item.metaLine())

        val onItsWay = title.copy(libraryStatus = "wanted").toItem(FakeText)
        assertEquals(Badge("in progress", Badge.Tone.ACTIVE), onItsWay.tmdb!!.badge)

        val show = title.copy(isMovie = false, libraryId = null).toItem(FakeText)
        assertEquals("tmdb-tv-101", show.id)
        assertEquals(MediaItem.Kind.SERIES, show.kind)
        assertNull(show.tmdb!!.badge)
        assertFalse(show.tmdb!!.inLibrary)
    }
}
