package cloud.rawkoon.tv.rawkoon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shapes are the ones Rawkoon's shelf routes answer with. */
class ShelfParseTest {
    private val list = JSONObject(
        """
        {"shelves":[
          {"id":3,"name":"A shelf","description":"About it","entry_count":12,"in_library_count":7,
           "home_row":true,"visible_from":"11-01","visible_to":"01-15","visible_now":true,"order_mode":"release","seed_key":"x",
           "cover":{"style":"mosaic","posters":["https://img.example.com/a.jpg","https://img.example.com/b.jpg"]}},
          {"id":4,"name":"Another","description":null,"entry_count":0,"in_library_count":0,
           "home_row":false,"visible_from":null,"visible_to":null,"visible_now":false,"order_mode":"manual","seed_key":null,
           "cover":{"style":"backdrop","backdrop_url":null}},
          {"id":0,"name":"No id","entry_count":0,"in_library_count":0,"home_row":false,"visible_now":true,"order_mode":"manual"},
          {"id":5,"name":"","entry_count":0,"in_library_count":0,"home_row":false,"visible_now":true,"order_mode":"manual"}
        ]}
        """,
    )

    @Test
    fun `the list keeps shelves with an id and a name, with their cover and counts`() {
        val shelves = parseShelves(list)
        assertEquals(listOf(3, 4), shelves.map { it.id })
        val first = shelves[0]
        assertEquals("A shelf", first.name)
        assertEquals("About it", first.description)
        assertEquals(12, first.entryCount)
        assertEquals(7, first.inLibraryCount)
        assertEquals(true, first.homeRow)
        assertEquals(true, first.visibleNow)
        assertEquals(ShelfOrder.RELEASE, first.order)
        assertEquals(ShelfCover.Mosaic(listOf("https://img.example.com/a.jpg", "https://img.example.com/b.jpg")), first.cover)
        assertEquals(ShelfCover.Backdrop(null), shelves[1].cover)
        assertEquals(ShelfOrder.MANUAL, shelves[1].order)
        assertNull(shelves[1].description)
    }

    @Test
    fun `a shelf without a cover is an empty mosaic, and an unknown order is by hand`() {
        val shelf = parseShelfSummary(JSONObject("""{"id":1,"name":"Bare","entry_count":0,"in_library_count":0,"order_mode":"sideways"}"""))!!
        assertEquals(ShelfCover.Mosaic(emptyList()), shelf.cover)
        assertEquals(ShelfOrder.MANUAL, shelf.order)
    }

    @Test
    fun `a detail keeps its entries in order, with their season, group and library state`() {
        val detail = parseShelf(
            JSONObject(
                """
                {"shelf":{"id":6,"name":"Ordered","description":null,"entry_count":3,"in_library_count":1,
                  "home_row":false,"visible_from":null,"visible_to":null,"visible_now":true,"order_mode":"manual","seed_key":null,
                  "cover":{"style":"mosaic","posters":[]},
                  "entries":[
                    {"id":10,"tmdb_id":101,"type":"movie","season":null,"title":"First","year":2008,"poster_url":"https://img.example.com/1.jpg",
                     "backdrop_url":null,"group_label":"Phase One","position":0,"in_library":true,"library_id":5,"source":"manual","reason":null},
                    {"id":11,"tmdb_id":202,"type":"show","season":2,"title":"Second","year":2021,"poster_url":null,
                     "backdrop_url":"https://img.example.com/b.jpg","group_label":"Phase Two","position":1,"in_library":false,"library_id":null,"source":"manual","reason":null},
                    {"id":12,"tmdb_id":303,"type":"person","title":"Not a title","position":2,"in_library":false}
                  ]}}
                """,
            ),
        )!!
        assertEquals("Ordered", detail.summary.name)
        assertEquals(2, detail.entries.size)
        val first = detail.entries[0]
        assertEquals(10, first.id)
        assertEquals(101, first.tmdbId)
        assertTrue(first.isMovie)
        assertNull(first.season)
        assertEquals("Phase One", first.groupLabel)
        assertEquals(true, first.inLibrary)
        val second = detail.entries[1]
        assertEquals(2, second.season)
        assertEquals(false, second.isMovie)
        assertEquals(false, second.inLibrary)
        assertEquals(2021, second.year)
    }

    @Test
    fun `a shelf without a shelf object parses to nothing`() {
        assertNull(parseShelf(JSONObject("""{"error":"Shelf not found"}""")))
    }

    @Test
    fun `the for-title answer says which shelves hold the title and which take titles by hand`() {
        val memberships = parseShelfMemberships(
            JSONObject(
                """{"shelves":[
                    {"id":3,"name":"Holds","holds":true,"entry_id":41,"order_mode":"manual"},
                    {"id":4,"name":"Empty","holds":false,"entry_id":null,"order_mode":"manual"},
                    {"id":5,"name":"Sorted","holds":false,"entry_id":null,"order_mode":"release"}
                  ]}""",
            ),
        )
        assertEquals(listOf(3, 4, 5), memberships.map { it.id })
        assertEquals(ShelfMembership(3, "Holds", true, 41, ShelfOrder.MANUAL), memberships[0])
        assertEquals(ShelfMembership(4, "Empty", false, null, ShelfOrder.MANUAL), memberships[1])
        assertEquals(ShelfOrder.RELEASE, memberships[2].order)
    }

    @Test
    fun `a saved shelf reads back as it was`() {
        val detail = parseShelf(
            JSONObject(
                """
                {"shelf":{"id":6,"name":"Saved","description":"Still here","entry_count":2,"in_library_count":1,
                  "home_row":true,"visible_from":null,"visible_to":null,"visible_now":true,"order_mode":"release","seed_key":null,
                  "cover":{"style":"backdrop","backdrop_url":"https://img.example.com/back.jpg"},
                  "entries":[
                    {"id":10,"tmdb_id":101,"type":"movie","season":null,"title":"First","year":2008,"poster_url":null,
                     "group_label":null,"position":0,"in_library":true},
                    {"id":11,"tmdb_id":202,"type":"show","season":1,"title":"Second","year":2021,"poster_url":null,
                     "group_label":"Group","position":1,"in_library":false}
                  ]}}
                """,
            ),
        )!!
        val roundTrip = parseShelfDetail(detail.toJson())!!
        assertEquals(detail, roundTrip)
        assertEquals(ShelfCover.Backdrop("https://img.example.com/back.jpg"), roundTrip.summary.cover)
        assertEquals("Still here", roundTrip.summary.description)
        assertEquals(2, roundTrip.entries.size)
    }

    @Test
    fun `a home row id names its shelf and nothing else`() {
        assertEquals(12, shelfIdOf(homeRowId(12)))
        assertEquals("rawkoon:shelf:12", homeRowId(12))
        assertNull(shelfIdOf("rawkoon:upcoming"))
        assertNull(shelfIdOf("rawkoon:shelf:"))
        assertNull(shelfIdOf("rawkoon:shelf:abc"))
    }
}
