package cloud.rawkoon.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.io.path.createTempDirectory

class SkipProfilePickerTest {
    private fun profile(id: String) = Profile(id = id, name = id, jellyfinUrl = "https://jf.example", jellyfinUserId = id, jellyfinToken = "t")

    @Test
    fun `one profile without a PIN opens when the setting is on`() {
        val only = profile("a")
        assertEquals(only, profileToAutoOpen(listOf(only), skip = true))
    }

    @Test
    fun `the setting off keeps the picker`() {
        assertNull(profileToAutoOpen(listOf(profile("a")), skip = false))
    }

    @Test
    fun `a PIN keeps the picker`() {
        assertNull(profileToAutoOpen(listOf(profile("a").withPin("1234")), skip = true))
    }

    @Test
    fun `no profile or several keep the picker`() {
        assertNull(profileToAutoOpen(emptyList(), skip = true))
        assertNull(profileToAutoOpen(listOf(profile("a"), profile("b")), skip = true))
    }
}

class FormatBytesTest {
    private fun fmt(bytes: Long) = formatBytes(bytes, Locale.US)

    @Test
    fun `small sizes are whole bytes`() {
        assertEquals("0 B", fmt(0))
        assertEquals("1023 B", fmt(1023))
        assertEquals("0 B", fmt(-5))
    }

    @Test
    fun `larger sizes step up with one decimal`() {
        assertEquals("1.0 KB", fmt(1024))
        assertEquals("1.5 KB", fmt(1536))
        assertEquals("3.5 MB", fmt(3_670_016))
        assertEquals("60.0 MB", fmt(60L * 1024 * 1024))
        assertEquals("2.0 GB", fmt(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `units follow the language`() {
        val french = listOf("o", "Ko", "Mo", "Go", "To")
        assertEquals("512 o", formatBytes(512, Locale.FRENCH, french))
        assertEquals("12,6 Mo", formatBytes(13_212_058, Locale.FRENCH, french))
    }

    @Test
    fun `the unit rolls over just below the next step`() {
        assertEquals("1023.0 KB", fmt(1023L * 1024))
        assertEquals("1.0 MB", fmt(1024L * 1024))
    }
}

class LicencesTest {
    @Test
    fun `every credit is named once`() {
        val names = Licences.credits.map { it.name }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `a known licence is never blank`() {
        assertTrue(Licences.credits.all { it.licence == null || it.licence.isNotBlank() })
    }
}

class DiskCacheClearTest {
    @Test
    fun `clearing the cache dir leaves downloads in progress and the size counts what is there`() {
        val dir = createTempDirectory().toFile()
        try {
            java.io.File(dir, "a").writeBytes(ByteArray(100))
            java.io.File(dir, "b").writeBytes(ByteArray(50))
            java.io.File(dir, "c.tmp").writeBytes(ByteArray(10))
            assertEquals(160L, dirSize(dir))
            clearDir(dir)
            assertEquals(listOf("c.tmp"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }
}
