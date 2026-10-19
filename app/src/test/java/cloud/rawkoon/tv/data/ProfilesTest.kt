package cloud.rawkoon.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    private fun profile(id: String = "p1", userId: String = "abc", url: String = "https://jf.example") =
        Profile(id = id, name = "Alex", jellyfinUrl = url, jellyfinUserId = userId, jellyfinToken = "t")

    @Test
    fun `a profile survives a round trip through JSON`() {
        val original = profile().copy(avatarTag = "tag", rawkoonUrl = "https://rk.example", rawkoonToken = "rt", lastUsed = 42L).withPin("1234")
        val decoded = ProfileStore.decode(ProfileStore.encode(listOf(original)))
        assertEquals(listOf(original), decoded)
    }

    @Test
    fun `missing optional fields decode as null`() {
        val decoded = ProfileStore.decode(ProfileStore.encode(listOf(profile())))
        assertNull(decoded.single().rawkoonToken)
        assertNull(decoded.single().avatarTag)
        assertFalse(decoded.single().hasPin)
    }

    @Test
    fun `a broken entry is skipped, not fatal`() {
        val text = "[{\"id\":\"x\"}," + profile().toJson() + "]"
        assertEquals(1, ProfileStore.decode(text).size)
    }

    @Test
    fun `the PIN is checked against its salted hash`() {
        val p = profile().withPin("2580")
        assertTrue(p.checkPin("2580"))
        assertFalse(p.checkPin("0000"))
        assertNotEquals(p.pinHash, profile(id = "p2").withPin("2580").pinHash)
    }

    @Test
    fun `an empty PIN clears it`() {
        assertFalse(profile().withPin("1234").withPin("").hasPin)
    }

    @Test
    fun `same account ignores trailing slashes and case`() {
        assertTrue(profile(userId = "ABC").sameAccount(profile(id = "p2", url = "https://JF.example/")))
        assertFalse(profile().sameAccount(profile(id = "p2", userId = "other")))
    }
}
