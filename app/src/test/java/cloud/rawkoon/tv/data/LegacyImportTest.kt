package cloud.rawkoon.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LegacyImportTest {
    private fun user(name: String, lastUsed: Long, pin: String? = null) =
        LegacyImport.OldUser(id = "0123456789abcdef0123456789abcdef", name = name, token = "tok-$name", pin = pin, serverUrl = "https://jf.example/", lastUsed = lastUsed)

    private val rawkoon = LegacyImport.OldRawkoon(url = "https://rk.example", token = "rk-token", userName = "Alex", userId = "u1")

    @Test
    fun `the Rawkoon session goes to the user with the same name`() {
        val profiles = LegacyImport.toProfiles(listOf(user("Sam", 2), user("alex", 1)), rawkoon)
        assertEquals(listOf("Sam", "alex"), profiles.map { it.name })
        assertNull(profiles[0].rawkoonToken)
        assertEquals("rk-token", profiles[1].rawkoonToken)
        assertEquals("u1", profiles[1].rawkoonUserId)
    }

    @Test
    fun `a single user gets the Rawkoon session even under another name`() {
        val profiles = LegacyImport.toProfiles(listOf(user("Sam", 1)), rawkoon)
        assertEquals("rk-token", profiles.single().rawkoonToken)
    }

    @Test
    fun `with several users and no name match, nobody gets the session`() {
        val profiles = LegacyImport.toProfiles(listOf(user("Sam", 1), user("Kim", 2)), rawkoon)
        assertTrue(profiles.all { it.rawkoonToken == null })
    }

    @Test
    fun `old PINs keep working and URLs lose their trailing slash`() {
        val profile = LegacyImport.toProfiles(listOf(user("Sam", 1, pin = "4321")), null).single()
        assertTrue(profile.checkPin("4321"))
        assertEquals("https://jf.example", profile.jellyfinUrl)
    }

    @Test
    fun `the old settings protobuf is read by field number`() {
        val bytes = ByteArrayOutputStream().apply {
            field(1, "https://rk.example")
            field(2, "rk-token")
            field(3, "Alex")
            write(0x20) // field 4, varint
            write(0x96)
            write(0x01)
            field(5, "u1")
            field(6, "skipped")
        }.toByteArray()
        assertEquals(rawkoon, LegacyImport.parseRawkoon(bytes))
    }

    private fun ByteArrayOutputStream.field(number: Int, value: String) {
        val data = value.toByteArray()
        write((number shl 3) or 2)
        write(data.size)
        write(data)
    }
}
