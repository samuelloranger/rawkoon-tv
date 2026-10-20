package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.data.optFloatOrNull
import cloud.rawkoon.tv.data.optIntOrNull
import cloud.rawkoon.tv.data.optStringOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * A library item as the UI uses it: text for the header and the image URLs the cards and the
 * backdrop need, picked once when the row loads (an episode borrows its show's poster, backdrop
 * and logo). Plain data, so views never touch the SDK.
 */
data class MediaItem(
    val id: String,
    val kind: Kind,
    val title: String,
    /** The show for an episode; null otherwise. */
    val seriesName: String?,
    /** "S1 · E3" for an episode. */
    val episodeLabel: String?,
    val overview: String,
    val year: Int?,
    val runtimeMinutes: Int?,
    val officialRating: String?,
    val communityRating: Float?,
    val posterUrl: String?,
    val thumbUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    /** 0..1, or null when not started. */
    val progress: Float?,
    val played: Boolean,
    val seriesId: String? = null,
    val seasonId: String? = null,
    /** Season number of an episode, or the number of a season. */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val favorite: Boolean = false,
    val media: MediaInfo? = null,
    /** Episodes in a season, seasons in a show. */
    val childCount: Int? = null,
    /** Where playback resumes, 0 to start over. */
    val resumeMs: Long = 0L,
    /** Set on a title that comes from TMDB through Rawkoon instead of from the library: [id] is then not a Jellyfin id. */
    val tmdb: TmdbRef? = null,
) {
    enum class Kind { MOVIE, SERIES, SEASON, EPISODE, OTHER }

    /** What the header names: the show for an episode. */
    val headerTitle: String get() = seriesName ?: title

    /** "2026 · 1 h 37 min · CA-14A · ★ 7.3", or "S1 · E3 · Title · 52 min" for an episode. */
    fun metaLine(): String {
        tmdb?.let { return listOfNotNull(it.subtitle ?: year?.toString(), it.badge?.text, score()).joinToString(" · ") }
        val parts = mutableListOf<String>()
        if (kind == Kind.EPISODE) {
            episodeLabel?.let { parts += it }
            parts += title
        } else {
            year?.let { parts += it.toString() }
        }
        runtimeMinutes?.takeIf { it > 0 }?.let { parts += duration(it) }
        if (kind != Kind.EPISODE) {
            officialRating?.let { parts += it }
            score()?.let { parts += it }
        }
        return parts.joinToString(" · ")
    }

    fun score(): String? = communityRating?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) }

    /** Minutes left to watch, from the progress. */
    fun minutesLeft(): Int? = runtimeMinutes?.let { total -> (total * (1f - (progress ?: 0f))).toInt().coerceAtLeast(1) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("title", title).putOpt("seriesName", seriesName)
        .putOpt("episodeLabel", episodeLabel).put("overview", overview).putOpt("year", year)
        .putOpt("runtimeMinutes", runtimeMinutes).putOpt("officialRating", officialRating)
        .putOpt("communityRating", communityRating?.toDouble()).putOpt("posterUrl", posterUrl)
        .putOpt("thumbUrl", thumbUrl).putOpt("backdropUrl", backdropUrl).putOpt("logoUrl", logoUrl)
        .putOpt("progress", progress?.toDouble()).put("played", played).putOpt("seriesId", seriesId)
        .putOpt("seasonId", seasonId).putOpt("seasonNumber", seasonNumber).putOpt("episodeNumber", episodeNumber)
        .put("favorite", favorite).putOpt("media", media?.toJson()).putOpt("childCount", childCount)
        .put("resumeMs", resumeMs).putOpt("tmdb", tmdb?.toJson())

    companion object {
        fun duration(minutes: Int) = if (minutes >= 60) MediaWords.duration(minutes / 60, minutes % 60) else MediaWords.minutes(minutes)

        fun fromJson(json: JSONObject): MediaItem {
            return MediaItem(
                id = json.getString("id"),
                kind = runCatching { Kind.valueOf(json.getString("kind")) }.getOrDefault(Kind.OTHER),
                title = json.getString("title"),
                seriesName = json.optStringOrNull("seriesName"),
                episodeLabel = json.optStringOrNull("episodeLabel"),
                overview = json.optString("overview"),
                year = json.optIntOrNull("year"),
                runtimeMinutes = json.optIntOrNull("runtimeMinutes"),
                officialRating = json.optStringOrNull("officialRating"),
                communityRating = json.optFloatOrNull("communityRating"),
                posterUrl = json.optStringOrNull("posterUrl"),
                thumbUrl = json.optStringOrNull("thumbUrl"),
                backdropUrl = json.optStringOrNull("backdropUrl"),
                logoUrl = json.optStringOrNull("logoUrl"),
                progress = json.optFloatOrNull("progress"),
                played = json.optBoolean("played"),
                seriesId = json.optStringOrNull("seriesId"),
                seasonId = json.optStringOrNull("seasonId"),
                seasonNumber = json.optIntOrNull("seasonNumber"),
                episodeNumber = json.optIntOrNull("episodeNumber"),
                favorite = json.optBoolean("favorite"),
                media = json.optJSONObject("media")?.let(MediaInfo::fromJson),
                childCount = json.optIntOrNull("childCount"),
                resumeMs = json.optLong("resumeMs"),
                tmdb = json.optJSONObject("tmdb")?.let(TmdbRef::fromJson),
            )
        }

        fun from(dto: BaseItemDto, images: ImageUrls): MediaItem {
            val kind = when (dto.type) {
                BaseItemKind.MOVIE -> Kind.MOVIE
                BaseItemKind.SERIES -> Kind.SERIES
                BaseItemKind.SEASON -> Kind.SEASON
                BaseItemKind.EPISODE -> Kind.EPISODE
                else -> Kind.OTHER
            }
            val tags = dto.imageTags.orEmpty()
            val own = { type: ImageType -> tags[type]?.let { images.item(dto.id, type, it) } }
            val backdrop = dto.backdropImageTags?.firstOrNull()?.let { images.item(dto.id, ImageType.BACKDROP, it) }
                ?: dto.parentBackdropItemId?.let { id ->
                    dto.parentBackdropImageTags?.firstOrNull()?.let { images.item(id, ImageType.BACKDROP, it) }
                }
            val seriesPoster = dto.seriesId?.let { id -> dto.seriesPrimaryImageTag?.let { images.item(id, ImageType.PRIMARY, it) } }
            val logo = own(ImageType.LOGO)
                ?: dto.parentLogoItemId?.let { id -> dto.parentLogoImageTag?.let { images.item(id, ImageType.LOGO, it) } }
            val parentThumb = dto.parentThumbItemId?.let { id -> dto.parentThumbImageTag?.let { images.item(id, ImageType.THUMB, it) } }
            val episode = kind == Kind.EPISODE
            val season = kind == Kind.SEASON
            val percent = dto.userData?.playedPercentage
            return MediaItem(
                id = dto.id.toString(),
                kind = kind,
                title = dto.name.orEmpty(),
                seriesName = if (episode) dto.seriesName else null,
                episodeLabel = if (episode) listOfNotNull(dto.parentIndexNumber?.let { "S$it" }, dto.indexNumber?.let { "E$it" })
                    .joinToString(" · ").ifEmpty { null } else null,
                overview = dto.overview.orEmpty(),
                year = dto.productionYear,
                runtimeMinutes = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() },
                officialRating = dto.officialRating,
                communityRating = dto.communityRating,
                // A season without its own poster borrows the show's
                posterUrl = if (episode) seriesPoster ?: own(ImageType.PRIMARY) else own(ImageType.PRIMARY) ?: seriesPoster,
                // Landscape cards: an episode's still, else the item's thumb or backdrop
                thumbUrl = if (episode) own(ImageType.PRIMARY) ?: parentThumb ?: backdrop else own(ImageType.THUMB) ?: backdrop ?: parentThumb,
                backdropUrl = backdrop,
                logoUrl = logo,
                progress = percent?.takeIf { it > 0.0 }?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                played = dto.userData?.played == true,
                seriesId = dto.seriesId?.toString() ?: if (kind == Kind.SERIES) dto.id.toString() else null,
                seasonId = dto.seasonId?.toString() ?: if (season) dto.id.toString() else null,
                seasonNumber = if (season) dto.indexNumber else dto.parentIndexNumber,
                episodeNumber = if (episode) dto.indexNumber else null,
                favorite = dto.userData?.isFavorite == true,
                media = MediaInfo.from(dto.mediaStreams ?: dto.mediaSources?.firstOrNull()?.mediaStreams),
                childCount = dto.childCount,
                resumeMs = (dto.userData?.playbackPositionTicks ?: 0L) / 10_000L,
            )
        }
    }
}

/**
 * What a TMDB title carries beyond a library item: its [id], whether Rawkoon has it ([inLibrary]),
 * a status [badge] painted on the poster (a request's state, an episode range, "in your library") and
 * the [subtitle] under a card's title instead of its year. An air date is kept raw in [airDate], so
 * a cached card's "Tomorrow" is worded again when it is shown.
 */
data class TmdbRef(
    val id: Int,
    val inLibrary: Boolean = false,
    val badge: Badge? = null,
    val subtitle: String? = null,
    val airDate: LocalDate? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("inLibrary", inLibrary).putOpt("badge", badge?.toJson())
        .putOpt("subtitle", subtitle).putOpt("airDate", airDate?.toString())

    companion object {
        fun fromJson(json: JSONObject) = TmdbRef(
            id = json.getInt("id"),
            inLibrary = json.optBoolean("inLibrary"),
            badge = json.optJSONObject("badge")?.let(Badge::fromJson),
            subtitle = json.optStringOrNull("subtitle"),
            airDate = json.optStringOrNull("airDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
}

/** A short status on a poster, in one of a few tones. */
data class Badge(val text: String, val tone: Tone) {
    enum class Tone { NEUTRAL, WAITING, ACTIVE, READY, REFUSED }

    fun toJson(): JSONObject = JSONObject().put("text", text).put("tone", tone.name)

    companion object {
        fun fromJson(json: JSONObject) = Badge(
            json.getString("text"),
            runCatching { Tone.valueOf(json.getString("tone")) }.getOrDefault(Tone.NEUTRAL),
        )
    }
}

/** What the chips say about a file: "1080p", "HEVC", "Français 5.1". */
data class MediaInfo(val resolution: String?, val codec: String?, val audio: String?) {
    fun chips(): List<String> = listOfNotNull(resolution, codec, audio)

    fun toJson(): JSONObject = JSONObject().putOpt("resolution", resolution).putOpt("codec", codec).putOpt("audio", audio)

    companion object {
        fun fromJson(json: JSONObject): MediaInfo {
            return MediaInfo(json.optStringOrNull("resolution"), json.optStringOrNull("codec"), json.optStringOrNull("audio"))
        }

        fun from(streams: List<MediaStream>?): MediaInfo? {
            if (streams.isNullOrEmpty()) return null
            val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
            val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault } ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
            return MediaInfo(
                resolution = video?.height?.let { h ->
                    when {
                        h >= 2000 -> "4K"
                        h >= 1000 -> "1080p"
                        h >= 700 -> "720p"
                        else -> MediaWords.standardDefinition()
                    }
                },
                codec = video?.codec?.let { codecName(it) },
                audio = audio?.let { a -> listOfNotNull(languageName(a.language), channels(a)).joinToString(" ").ifEmpty { null } },
            )
        }

        private fun codecName(codec: String) = when (codec.lowercase()) {
            "hevc", "h265" -> "HEVC"
            "h264", "avc" -> "H264"
            "av1" -> "AV1"
            "vp9" -> "VP9"
            "mpeg2video" -> "MPEG-2"
            else -> codec.uppercase()
        }

        /** Jellyfin's three-letter codes, named in the app's language: "fre" becomes "Français". */
        fun languageName(code: String?): String? {
            if (code.isNullOrBlank() || code == "und") return null
            val two = when (code.lowercase()) {
                "fre", "fra" -> "fr"
                "eng" -> "en"
                "spa" -> "es"
                "ger", "deu" -> "de"
                "ita" -> "it"
                "jpn" -> "ja"
                "por" -> "pt"
                "kor" -> "ko"
                "chi", "zho" -> "zh"
                else -> code.lowercase()
            }
            val name = Locale.forLanguageTag(two).getDisplayLanguage(Locale.getDefault())
            if (name.isBlank() || name.equals(two, ignoreCase = true)) return null
            return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

        private fun channels(stream: MediaStream): String? = when (val n = stream.channels) {
            null -> null
            1 -> MediaWords.mono()
            2 -> MediaWords.stereo()
            6 -> "5.1"
            8 -> "7.1"
            else -> MediaWords.channels(n)
        }
    }
}

/** A cast or crew member. */
data class Person(val id: String, val name: String, val role: String?, val imageUrl: String?)

/** What a details page shows beyond the card: genres, cast, the studio, the show's span. */
data class Details(
    val item: MediaItem,
    val genres: List<String>,
    val studio: String?,
    val endYear: Int?,
    val continuing: Boolean,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val people: List<Person>,
)

/** Builds image URLs on one server. Tags make each URL change when the image does, so caches never go stale. */
class ImageUrls(private val base: String) {
    fun item(id: UUID, type: ImageType, tag: String) = "$base/Items/$id/Images/${type.serialName}?tag=$tag&quality=90"

    fun user(userId: String, tag: String) = "$base/Users/$userId/Images/Primary?tag=$tag&quality=90"
}

/** A titled row of items on the home screen. [landscape] rows show stills with progress. */
data class HomeRow(val id: String, val title: String, val items: List<MediaItem>, val landscape: Boolean) {
    /** Changes whenever anything a card shows changes. */
    val signature: String
        get() = items.joinToString("|") { "${it.id}:${it.progress}:${it.played}:${it.thumbUrl}:${it.posterUrl}:${it.tmdb}" }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("landscape", landscape)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })

    companion object {
        fun listToJson(rows: List<HomeRow>): String = JSONArray().apply { rows.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String): List<HomeRow> {
            val array = JSONArray(text)
            return (0 until array.length()).map { i ->
                val row = array.getJSONObject(i)
                val items = row.getJSONArray("items")
                HomeRow(
                    id = row.getString("id"),
                    title = row.getString("title"),
                    items = (0 until items.length()).map { MediaItem.fromJson(items.getJSONObject(it)) },
                    landscape = row.getBoolean("landscape"),
                )
            }
        }
    }
}
