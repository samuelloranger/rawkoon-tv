package cloud.rawkoon.tv.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/**
 * ExoPlayer set up as the official client does it (decoder fallback, the ffmpeg extension for
 * audio the TV can't decode, wider TS timestamp search), with Jellyfin's auth header on every
 * request and Jellyfin's track numbers mapped onto ExoPlayer's tracks. [seekBackMs] and
 * [seekForwardMs] are how far the rewind and fast-forward buttons and keys seek.
 */
@OptIn(UnstableApi::class)
class VideoPlayer(context: Context, authorization: String, seekBackMs: Long, seekForwardMs: Long) {
    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters().setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true).build()
    }
    val exo: ExoPlayer

    /** What the next track change should apply, once ExoPlayer has listed the tracks. */
    private var pending: Pair<Int?, Int>? = null
    private var stream: Stream? = null

    init {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("RawkoonTV")
            .setDefaultRequestProperties(mapOf("Authorization" to authorization))
            .setAllowCrossProtocolRedirects(true)
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3)
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        exo = ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setSeekBackIncrementMs(seekBackMs)
            .setSeekForwardIncrementMs(seekForwardMs)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                pending?.let { (audio, subtitle) -> if (applyTracks(audio, subtitle)) pending = null }
            }
        })
    }

    /** Plays [stream] from [startMs], with its chosen audio and subtitle tracks. */
    fun load(stream: Stream, startMs: Long) {
        this.stream = stream
        val subtitles = stream.subtitles.filter { it.delivery == SubtitleDeliveryMethod.EXTERNAL && it.url != null }.mapNotNull { track ->
            val mime = subtitleMime(track.codec) ?: return@mapNotNull null
            ExoItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setId(externalId(track.index))
                .setMimeType(mime)
                .setLanguage(track.language)
                .setLabel(track.title)
                .build()
        }
        val item = ExoItem.Builder()
            .setUri(stream.url)
            .setMimeType(if (stream.method == PlayMethod.DIRECT_PLAY) null else MimeTypes.APPLICATION_M3U8)
            .setSubtitleConfigurations(subtitles)
            .build()
        // Jellyfin's HLS playlists cover the whole item, so a transcode is sought like a file
        exo.setMediaItem(item, startMs)
        pending = stream.audioIndex to stream.subtitleIndex
        exo.prepare()
        exo.playWhenReady = true
    }

    /** True when the choice can be made here; false when the server must make a new stream (burned-in subtitles, a transcode). */
    fun canSwitchLocally(audio: Int?, subtitle: Int): Boolean {
        val s = stream ?: return false
        if (s.method != PlayMethod.DIRECT_PLAY) {
            // A transcode can only drop its subtitles here when they aren't burned into the picture
            val burnedIn = s.subtitles.firstOrNull { it.index == s.subtitleIndex }?.delivery == SubtitleDeliveryMethod.ENCODE
            return audio == s.audioIndex && (subtitle == s.subtitleIndex || (subtitle == -1 && !burnedIn))
        }
        val sub = s.subtitles.firstOrNull { it.index == subtitle }
        return sub == null || sub.delivery == SubtitleDeliveryMethod.EMBED || sub.delivery == SubtitleDeliveryMethod.EXTERNAL
    }

    fun select(audio: Int?, subtitle: Int) {
        if (!applyTracks(audio, subtitle)) pending = audio to subtitle
    }

    /**
     * Selects tracks by Jellyfin index. Embedded tracks keep the file's order, so the n-th
     * embedded audio (or subtitle) stream is ExoPlayer's n-th group of that type.
     */
    private fun applyTracks(audio: Int?, subtitle: Int): Boolean {
        val s = stream ?: return false
        val groups = exo.currentTracks.groups
        if (groups.isEmpty()) return false
        val builder = trackSelector.buildUponParameters()
        if (s.method == PlayMethod.DIRECT_PLAY) {
            val embeddedAudio = s.audio.filter { !it.external }.indexOfFirst { it.index == audio }
            val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            audioGroups.getOrNull(embeddedAudio)?.let { builder.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
        }
        val track = s.subtitles.firstOrNull { it.index == subtitle }
        val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val group = when {
            track == null -> null
            track.delivery == SubtitleDeliveryMethod.EXTERNAL -> textGroups.firstOrNull { g -> (0 until g.length).any { g.getTrackFormat(it).id?.endsWith(externalId(track.index)) == true } }
            track.delivery == SubtitleDeliveryMethod.EMBED -> textGroups.filter { g -> (0 until g.length).none { g.getTrackFormat(it).id?.contains(EXTERNAL_PREFIX) == true } }
                .getOrNull(s.subtitles.filter { !it.external && it.delivery == SubtitleDeliveryMethod.EMBED }.indexOf(track))
            // In an HLS transcode the chosen subtitle is the manifest's only one
            else -> textGroups.firstOrNull()
        }
        if (group == null || track == null || track.delivery == SubtitleDeliveryMethod.ENCODE) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        trackSelector.parameters = builder.build()
        return true
    }

    fun release() = exo.release()

    private companion object {
        const val EXTERNAL_PREFIX = "jf-ext-"

        fun externalId(index: Int) = "$EXTERNAL_PREFIX$index"

        fun subtitleMime(codec: String?) = when (codec?.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> null
        }
    }
}
