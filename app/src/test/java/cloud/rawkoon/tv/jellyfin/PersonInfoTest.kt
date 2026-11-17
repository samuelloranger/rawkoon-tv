package cloud.rawkoon.tv.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PersonInfoTest {
    private fun person(born: LocalDate?, died: LocalDate? = null) =
        PersonInfo(id = "1", name = "A", bio = "", born = born, died = died, birthplace = null, imageUrl = null)

    @Test
    fun `the age turns on the birthday, not before`() {
        val p = person(LocalDate.of(1980, 6, 15))
        assertEquals(45, p.age(LocalDate.of(2026, 6, 14)))
        assertEquals(46, p.age(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun `a death stops the age`() {
        assertEquals(75, person(LocalDate.of(1940, 3, 1), LocalDate.of(2016, 1, 10)).age(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun `no birth date, no age`() {
        assertNull(person(null).age(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun `a birth date stored at midnight UTC keeps its day west of UTC`() {
        // What the SDK hands over in Toronto for 1978-04-12T00:00Z
        val local = LocalDateTime.of(1978, 4, 11, 19, 0)
        assertEquals(LocalDate.of(1978, 4, 12), utcDate(local, ZoneId.of("America/Toronto")))
    }
}
