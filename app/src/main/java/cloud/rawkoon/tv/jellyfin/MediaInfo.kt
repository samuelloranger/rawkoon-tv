package cloud.rawkoon.tv.jellyfin

import cloud.rawkoon.tv.data.optStringOrNull
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.json.JSONObject
import java.util.Locale

/** What the chips say about a file: "1080p", "HEVC", "Français 5.1". */
data class MediaInfo(val resolution: String?, val codec: String?, val audio: String?) {
    fun chips(): List<String> = listOfNotNull(resolution, codec, audio)

    fun toJson(): JSONObject = JSONObject().putOpt("resolution", resolution).putOpt("codec", codec).putOpt("audio", audio)

    companion object {
        fun fromJson(json: JSONObject): MediaInfo {
            return MediaInfo(json.optStringOrNull("resolution"), json.optStringOrNull("codec"), json.optStringOrNull("audio"))
        }

        /**
         * A show's files in one: the resolutions they span ("720p – 4K", or "1080p" when they all
         * share it) and the codec when every file has the same one. The audio varies too much to say.
         */
        fun span(files: List<MediaInfo>): MediaInfo? {
            val ranks = listOf(MediaWords.standardDefinition(), "720p", "1080p", "4K")
            val resolutions = files.mapNotNull { it.resolution }.distinct().sortedBy { ranks.indexOf(it) }
            val resolution = when (resolutions.size) {
                0 -> null
                1 -> resolutions.first()
                else -> "${resolutions.first()} – ${resolutions.last()}"
            }
            val codec = files.mapNotNull { it.codec }.distinct().singleOrNull()
            if (resolution == null && codec == null) return null
            return MediaInfo(resolution, codec, null)
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
