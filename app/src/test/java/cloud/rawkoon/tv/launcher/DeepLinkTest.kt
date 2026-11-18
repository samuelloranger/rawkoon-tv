package cloud.rawkoon.tv.launcher

import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.ui.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    private val id = "0123456789abcdef0123456789abcdef"
    private val profile = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `a page opens by its name or its web path`() {
        assertEquals(DeepLink.Page(null, Destination.MOVIES), DeepLink.parse("rawkoon://movies"))
        assertEquals(DeepLink.Page(null, Destination.SHELVES), DeepLink.parse("rawkoon://library/shelves"))
        assertEquals(DeepLink.Page(null, Destination.DISCOVER), DeepLink.parse("rawkoon://find"))
        assertEquals(DeepLink.Page(null, Destination.SEARCH), DeepLink.parse("RAWKOON://Search"))
        assertEquals(DeepLink.Page(null, Destination.HOME), DeepLink.parse("rawkoon:///"))
    }

    @Test
    fun `a shelf opens by its id`() {
        assertEquals(DeepLink.Shelf(null, 12), DeepLink.parse("rawkoon://shelves/12"))
        assertEquals(DeepLink.Shelf(profile, 3), DeepLink.parse("rawkoon://library/shelves/3?profile=$profile"))
        assertNull(DeepLink.parse("rawkoon://shelves/new"))
    }

    @Test
    fun `a title needs an id and a kind, and an episode its show`() {
        val movie = DeepLink.parse("rawkoon://item/$id?kind=movie") as DeepLink.Title
        assertNull(movie.profileId)
        assertEquals(id, movie.card.itemId)
        assertEquals(MediaItem.Kind.MOVIE, movie.card.kind)
        val episode = DeepLink.parse("rawkoon://item/$id?kind=episode&series=$id&profile=$profile") as DeepLink.Title
        assertEquals(profile, episode.profileId)
        assertEquals(id, episode.card.seriesId)
        assertNull(DeepLink.parse("rawkoon://item/$id?kind=episode"))
        assertNull(DeepLink.parse("rawkoon://item/$id"))
        assertNull(DeepLink.parse("rawkoon://item/not-an-id?kind=movie"))
        assertEquals(MediaItem.Kind.MOVIE, (DeepLink.parse("rawkoon://item/$id/?kind=%6Dovie") as DeepLink.Title).card.kind)
    }

    @Test
    fun `anything else is refused`() {
        assertNull(DeepLink.parse("rawkoon://nope"))
        assertNull(DeepLink.parse("rawkoon://movies?profile=../../etc"))
        assertNull(DeepLink.parse("rawkoon://movies?profile="))
        assertNull(DeepLink.parse("https://example.com/movies"))
        assertNull(DeepLink.parse("not a url"))
    }
}
