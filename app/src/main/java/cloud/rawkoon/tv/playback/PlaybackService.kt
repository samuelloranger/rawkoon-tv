package cloud.rawkoon.tv.playback

import android.content.Context
import android.util.Log
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.MediaInfo
import cloud.rawkoon.tv.playback.profile.createDeviceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest

/**
 * Jellyfin's side of playback for one profile: asks the server how to play an item for this TV
 * (direct play, direct stream or transcode, from the official client's device profile), reports
 * progress so "Continue watching" stays right, and fetches what the controls need.
 */
class PlaybackService(private val context: Context, private val session: JellyfinSession) {
    private val api get() = session.api

    /** The Authorization header for ExoPlayer and the trickplay tiles: the URLs carry no token. */
    val authorization: String by lazy {
        AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = session.profile.jellyfinToken,
        )
    }

    /** [direct] false asks for a transcode, after a file the TV couldn't play after all. */
    suspend fun stream(
        item: MediaItem,
        startMs: Long,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        direct: Boolean = true,
    ): Stream {
        val itemId = JellyfinSession.parseId(item.id)
        val quality = Quality(Prefs.maxBitrateMbps(context) * BITS_PER_MBPS, Prefs.maxResolution(context))
        val response = api.mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemId,
            data = PlaybackInfoDto(
                userId = session.userId,
                startTimeTicks = startMs * TICKS_PER_MS,
                deviceProfile = profile(quality),
                maxStreamingBitrate = quality.maxBitrate,
                enableDirectPlay = direct,
                enableDirectStream = direct,
                enableTranscoding = true,
                audioStreamIndex = audioIndex,
                subtitleStreamIndex = subtitleIndex,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                autoOpenLiveStream = true,
            ),
        ).content
        response.errorCode?.let { throw PlaybackFailure(it.serialName) }
        val source = response.mediaSources.firstOrNull() ?: throw PlaybackFailure("NoMediaSource")
        val (method, url) = when {
            source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY to (
                source.path?.takeIf { source.isRemote }
                    ?: api.videosApi.getVideoStreamUrl(itemId = itemId, container = source.container, mediaSourceId = source.id, static = true, tag = source.eTag)
                )
            source.supportsDirectStream && source.transcodingUrl != null ->
                PlayMethod.DIRECT_STREAM to api.createUrl(source.transcodingUrl!!, ignorePathParameters = true)
            source.supportsTranscoding && source.transcodingUrl != null ->
                PlayMethod.TRANSCODE to api.createUrl(source.transcodingUrl!!, ignorePathParameters = true)
            else -> throw PlaybackFailure("NoCompatibleStream")
        }
        val streams = source.mediaStreams.orEmpty()
        return Stream(
            itemId = item.id,
            mediaSourceId = source.id,
            playSessionId = response.playSessionId,
            url = url,
            method = method,
            audio = streams.filter { it.type == MediaStreamType.AUDIO }.map { s ->
                Track(s.index, trackTitle(s.displayTitle, s.language, s.codec), s.language, s.codec, s.isExternal, null, null)
            },
            subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }.map { s ->
                Track(
                    s.index, trackTitle(s.displayTitle, s.language, s.codec), s.language, s.codec, s.isExternal, s.deliveryMethod,
                    s.deliveryUrl?.let { api.createUrl(it, ignorePathParameters = true) },
                )
            },
            audioIndex = audioIndex ?: source.defaultAudioStreamIndex,
            subtitleIndex = subtitleIndex ?: source.defaultSubtitleStreamIndex ?: -1,
            durationMs = source.runTimeTicks?.let { it / TICKS_PER_MS },
        )
    }

    private fun trackTitle(display: String?, language: String?, codec: String?): String =
        display?.takeIf { it.isNotBlank() } ?: listOfNotNull(MediaInfo.languageName(language), codec?.uppercase()).joinToString(" · ").ifEmpty { "?" }

    /** Start, progress and stop go out one at a time, in the order they were made, so a late progress can't land after the stop. */
    suspend fun reportStart(stream: Stream, positionMs: Long, paused: Boolean) = reported("start") {
        api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = JellyfinSession.parseId(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                playSessionId = stream.playSessionId,
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = paused,
                isMuted = false,
                playMethod = stream.method,
                audioStreamIndex = stream.audioIndex,
                subtitleStreamIndex = stream.subtitleIndex,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportProgress(stream: Stream, positionMs: Long, paused: Boolean) = reported("progress") {
        api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = JellyfinSession.parseId(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                playSessionId = stream.playSessionId,
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = paused,
                isMuted = false,
                playMethod = stream.method,
                audioStreamIndex = stream.audioIndex,
                subtitleStreamIndex = stream.subtitleIndex,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    /** Reports the stop, and ends the server's transcode so it doesn't keep encoding for nobody. */
    suspend fun reportStop(stream: Stream, positionMs: Long) = reported("stop") {
        api.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = JellyfinSession.parseId(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                playSessionId = stream.playSessionId,
                positionTicks = positionMs * TICKS_PER_MS,
                failed = false,
            ),
        )
        if (stream.method != PlayMethod.DIRECT_PLAY && stream.playSessionId != null) {
            api.hlsSegmentApi.stopEncodingProcess(deviceId = api.deviceInfo.id, playSessionId = stream.playSessionId)
        }
    }

    suspend fun segments(itemId: String): List<Segment> = attempt("segments") {
        api.mediaSegmentsApi.getItemSegments(
            itemId = JellyfinSession.parseId(itemId),
            includeSegmentTypes = listOf(MediaSegmentType.INTRO, MediaSegmentType.RECAP, MediaSegmentType.OUTRO, MediaSegmentType.PREVIEW),
        ).content.items.map { Segment(it.type, it.startTicks / TICKS_PER_MS, it.endTicks / TICKS_PER_MS) }
    } ?: emptyList()

    /** The smallest set of thumbnails at least [minWidth] pixels wide, or the largest there is. */
    suspend fun trickplay(itemId: String, mediaSourceId: String?, minWidth: Int): Trickplay? = attempt("trickplay") {
        val id = JellyfinSession.parseId(itemId)
        val dto = api.userLibraryApi.getItem(itemId = id, userId = session.userId).content
        val byWidth = dto.trickplay?.let { all -> all[mediaSourceId ?: ""] ?: all.values.firstOrNull() } ?: return@attempt null
        val info = byWidth.values.sortedBy { it.width }.let { list -> list.firstOrNull { it.width >= minWidth } ?: list.lastOrNull() } ?: return@attempt null
        if (info.thumbnailCount <= 0 || info.interval <= 0) return@attempt null
        val source = mediaSourceId?.let { JellyfinSession.parseId(it) }
        Trickplay(info.width, info.height, info.tileWidth, info.tileHeight, info.thumbnailCount, info.interval) { tile ->
            api.trickplayApi.getTrickplayTileImageUrl(itemId = id, width = info.width, index = tile, mediaSourceId = source)
        }
    }

    /** The episode after [episode] in its show, for playing on at the end. */
    suspend fun nextEpisode(episode: MediaItem): MediaItem? {
        val series = episode.seriesId ?: return null
        return attempt("next episode") {
            api.tvShowsApi.getEpisodes(
                GetEpisodesRequest(
                    seriesId = JellyfinSession.parseId(series),
                    userId = session.userId,
                    startItemId = JellyfinSession.parseId(episode.id),
                    limit = 2,
                    fields = listOf(ItemFields.OVERVIEW),
                ),
            ).content.items.getOrNull(1)?.let { MediaItem.from(it, session.images) }
        }
    }

    /** Runs [block], giving null (and a log line) when it fails; cancellation is not a failure. */
    private inline fun <T> attempt(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed", e)
        null
    }

    private suspend fun reported(what: String, block: suspend () -> Unit) {
        reportLock.withLock { attempt(what) { block() } }
    }

    /**
     * The device profile is built once per streaming-quality setting and app run: probing the
     * decoders takes a moment. Changing the max bitrate or resolution builds another on the next
     * play. A profile built without knowing the server's version is used but not kept, so the next
     * call asks again.
     */
    private suspend fun profile(quality: Quality): DeviceProfile = profileLock.withLock {
        cachedProfiles[quality] ?: run {
            val version = attempt("server version") { ServerVersion.fromString(api.systemApi.getPublicSystemInfo().content.version.orEmpty()) }
            val built = withContext(Dispatchers.Default) {
                createDeviceProfile(context.applicationContext, version ?: FALLBACK_VERSION, quality.maxBitrate, quality.maxHeight)
            }
            if (version != null) cachedProfiles[quality] = built
            built
        }
    }

    /** What a cached device profile was built for: the bitrate cap in bits per second, and the height cap (0 for none). */
    private data class Quality(val maxBitrate: Int, val maxHeight: Int)

    companion object {
        const val TICKS_PER_MS = 10_000L
        private const val TAG = "PlaybackService"
        private val FALLBACK_VERSION = ServerVersion(10, 11, 0)
        private val reportLock = Mutex()
        private val profileLock = Mutex()
        private val cachedProfiles = mutableMapOf<Quality, DeviceProfile>()
        private const val BITS_PER_MBPS = 1_000_000
    }
}

class PlaybackFailure(val code: String) : Exception(code)
