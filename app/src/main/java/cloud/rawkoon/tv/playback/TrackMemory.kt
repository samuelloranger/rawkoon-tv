package cloud.rawkoon.tv.playback

import android.content.Context
import android.content.SharedPreferences
import cloud.rawkoon.tv.data.optIntOrNull
import cloud.rawkoon.tv.data.optStringOrNull
import org.json.JSONObject
import java.util.Locale

/**
 * A track picked on one episode, described by what it is rather than its index: two episodes of a
 * show seldom number their tracks the same way.
 */
data class TrackPick(
    val language: String?,
    val name: String?,
    val codec: String?,
    val forced: Boolean,
    val hearingImpaired: Boolean,
    val channels: Int?,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("language", language).putOpt("name", name).putOpt("codec", codec)
        .put("forced", forced).put("hearingImpaired", hearingImpaired).putOpt("channels", channels)

    companion object {
        fun of(track: Track) = TrackPick(track.language, track.name, track.codec, track.forced, track.hearingImpaired, track.channels)

        fun from(json: JSONObject) = TrackPick(
            language = json.optStringOrNull("language"),
            name = json.optStringOrNull("name"),
            codec = json.optStringOrNull("codec"),
            forced = json.optBoolean("forced"),
            hearingImpaired = json.optBoolean("hearingImpaired"),
            channels = json.optIntOrNull("channels"),
        )
    }
}

/** The subtitle picked on a show: a track, or none at all. */
sealed interface SubtitlePick {
    data object Off : SubtitlePick

    data class On(val track: TrackPick) : SubtitlePick
}

/** What was last picked on a show, each kind on its own; null is a kind never picked there. */
data class RememberedTracks(val audio: TrackPick? = null, val subtitle: SubtitlePick? = null)

/**
 * The best match for [pick] among [tracks], or null when none shares its language and its forced
 * flag: forced subtitles never stand in for full ones, nor the other way round. Then the SDH flag
 * weighs most, the track's name (which tells a commentary from the film), the codec and the
 * channels. A tie goes to the first.
 */
fun bestMatch(pick: TrackPick, tracks: List<Track>): Int? = tracks
    .filter { languageKey(it.language) == languageKey(pick.language) && it.forced == pick.forced }
    .maxByOrNull { track ->
        (if (track.hearingImpaired == pick.hearingImpaired) 4 else 0) +
            (if (track.name.equals(pick.name, ignoreCase = true)) 2 else 0) +
            (if (track.codec.equals(pick.codec, ignoreCase = true)) 1 else 0) +
            (if (track.channels == pick.channels) 1 else 0)
    }?.index

/**
 * One code per language, whichever way a file tags it: "fre" and "fra" are both French, and "fr"
 * too. "und" is no language, like a missing tag.
 */
internal fun languageKey(code: String?): String? {
    val lower = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
    BIBLIOGRAPHIC[lower]?.let { return it }
    if (lower.length != 2) return lower
    return runCatching { Locale.forLanguageTag(lower).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() } ?: lower
}

/** The ISO 639-2 bibliographic codes that differ from the terminology ones [Locale] gives. */
private val BIBLIOGRAPHIC = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho", "cze" to "ces", "dut" to "nld",
    "fre" to "fra", "geo" to "kat", "ger" to "deu", "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri",
    "may" to "msa", "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)

/**
 * The tracks to ask for on an episode, from what was picked on its show: null for a kind where the
 * server's choice stands (never picked, or nothing on this episode like it).
 */
fun rememberedIndices(remembered: RememberedTracks, audio: List<Track>, subtitles: List<Track>): Pair<Int?, Int?> =
    remembered.audio?.let { bestMatch(it, audio) } to when (val pick = remembered.subtitle) {
        null -> null
        SubtitlePick.Off -> -1
        is SubtitlePick.On -> bestMatch(pick.track, subtitles)
    }

/** The audio and subtitles last picked on each show, per profile, so the next episode plays with the same. */
class TrackMemory(context: Context, private val profileId: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(seriesId: String): RememberedTracks? {
        val json = prefs.getString(key(seriesId), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return RememberedTracks(
            audio = json.optJSONObject("audio")?.let(TrackPick::from),
            subtitle = when {
                json.optBoolean("subtitlesOff") -> SubtitlePick.Off
                else -> json.optJSONObject("subtitle")?.let { SubtitlePick.On(TrackPick.from(it)) }
            },
        )
    }

    fun rememberAudio(seriesId: String, track: Track) = update(seriesId) { it.copy(audio = TrackPick.of(track)) }

    /** [track] null is subtitles turned off. */
    fun rememberSubtitle(seriesId: String, track: Track?) =
        update(seriesId) { it.copy(subtitle = track?.let { t -> SubtitlePick.On(TrackPick.of(t)) } ?: SubtitlePick.Off) }

    private fun update(seriesId: String, change: (RememberedTracks) -> RememberedTracks) {
        val tracks = change(read(seriesId) ?: RememberedTracks())
        val json = JSONObject().putOpt("audio", tracks.audio?.toJson())
        when (val subtitle = tracks.subtitle) {
            null -> Unit
            SubtitlePick.Off -> json.put("subtitlesOff", true)
            is SubtitlePick.On -> json.put("subtitle", subtitle.track.toJson())
        }
        prefs.edit().putString(key(seriesId), json.toString()).apply()
    }

    private fun key(seriesId: String) = "$profileId:$seriesId"

    private companion object {
        const val NAME = "rawkoon-tracks"
    }
}
