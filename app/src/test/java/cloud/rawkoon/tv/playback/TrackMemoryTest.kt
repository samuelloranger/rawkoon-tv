package cloud.rawkoon.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMemoryTest {
    private fun track(
        index: Int,
        language: String?,
        name: String? = null,
        codec: String = "aac",
        forced: Boolean = false,
        hearingImpaired: Boolean = false,
        channels: Int? = null,
    ) = Track(index, "t$index", language, codec, false, null, null, name, forced, hearingImpaired, channels)

    private fun pick(track: Track) = TrackPick.of(track)

    @Test
    fun `the same language is found under another index`() {
        val picked = pick(track(2, "fre"))
        assertEquals(5, bestMatch(picked, listOf(track(4, "eng"), track(5, "fre"))))
    }

    @Test
    fun `no track in the language keeps the server's choice`() {
        assertNull(bestMatch(pick(track(2, "fre")), listOf(track(1, "eng"), track(3, "spa"))))
    }

    @Test
    fun `forced subtitles stay forced and full ones stay full`() {
        val tracks = listOf(track(3, "eng", forced = true), track(4, "eng"))
        assertEquals(4, bestMatch(pick(track(9, "eng")), tracks))
        assertEquals(3, bestMatch(pick(track(9, "eng", forced = true)), tracks))
    }

    @Test
    fun `forced subtitles never stand in for full ones`() {
        assertNull(bestMatch(pick(track(9, "eng", forced = true)), listOf(track(4, "eng"))))
        assertNull(bestMatch(pick(track(9, "eng")), listOf(track(3, "eng", forced = true))))
    }

    @Test
    fun `both ways of tagging a language match, and und is no language`() {
        assertEquals(5, bestMatch(pick(track(2, "fre")), listOf(track(4, "eng"), track(5, "fra"))))
        assertEquals(5, bestMatch(pick(track(2, "ger")), listOf(track(5, "deu"))))
        assertEquals(5, bestMatch(pick(track(2, "fr")), listOf(track(4, "eng"), track(5, "fre"))))
        assertEquals(2, bestMatch(pick(track(1, "und")), listOf(track(1, "eng"), track(2, null))))
    }

    @Test
    fun `hearing impaired subtitles are preferred when they were picked`() {
        val tracks = listOf(track(3, "eng"), track(4, "eng", hearingImpaired = true))
        assertEquals(4, bestMatch(pick(track(9, "eng", hearingImpaired = true)), tracks))
    }

    @Test
    fun `the film's audio is not swapped for a commentary`() {
        val tracks = listOf(track(1, "eng", name = "Commentary", codec = "aac", channels = 2), track(2, "eng", codec = "eac3", channels = 6))
        assertEquals(2, bestMatch(pick(track(7, "eng", codec = "eac3", channels = 6)), tracks))
        assertEquals(1, bestMatch(pick(track(8, "eng", name = "Commentary", channels = 2)), tracks))
    }

    @Test
    fun `a track without a language matches only another without one`() {
        assertEquals(2, bestMatch(pick(track(1, null)), listOf(track(1, "eng"), track(2, null))))
    }

    @Test
    fun `subtitles turned off stay off, and kinds never picked stay with the server`() {
        val audio = listOf(track(1, "eng"), track(2, "fre"))
        val subtitles = listOf(track(3, "eng"))
        assertEquals(null to -1, rememberedIndices(RememberedTracks(subtitle = SubtitlePick.Off), audio, subtitles))
        assertEquals(2 to null, rememberedIndices(RememberedTracks(audio = pick(track(5, "fre"))), audio, subtitles))
        assertEquals(null to 3, rememberedIndices(RememberedTracks(subtitle = SubtitlePick.On(pick(track(8, "eng")))), audio, subtitles))
    }

    @Test
    fun `a pick survives its JSON`() {
        val original = TrackPick("eng", "SDH", "subrip", forced = false, hearingImpaired = true, channels = null)
        assertEquals(original, TrackPick.from(original.toJson()))
    }
}
