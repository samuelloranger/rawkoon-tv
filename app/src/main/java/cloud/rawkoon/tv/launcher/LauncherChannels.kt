package cloud.rawkoon.tv.launcher

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import android.os.SystemClock
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.HomeRow
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What RawkoonTV publishes to the launcher: Watch Next (resume and next up) and a "Recently added"
 * preview channel. The first RawkoonTV published both from this same package, so the channel it
 * made is reused (its URI is kept in the "channels" preferences under "latest") and its Watch Next
 * rows are updated in place. All provider work runs off the main thread, one sync at a time; a
 * device without a TV provider just doesn't publish.
 */
class LauncherChannels(context: Context) {
    private val context = context.applicationContext
    private val resolver get() = this.context.contentResolver
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSyncAt = 0L
    private var lastSyncProfile: String? = null

    /** Home just loaded [rows] for [profile]: publish them, unless that was done a moment ago. */
    fun syncAfterHome(profile: Profile, rows: List<HomeRow>) {
        val now = SystemClock.elapsedRealtime()
        if (!LauncherSchedule.due(lastSyncAt, lastSyncProfile, now, profile.id)) return
        lastSyncAt = now
        lastSyncProfile = profile.id
        scope.launch { quietly { sync(profile, LauncherFeed.from(rows), allowCreate = true) } }
    }

    /**
     * The periodic job: the last used profile's rows, fetched without opening the UI. A session
     * that ended or a server that doesn't answer just skips this round.
     */
    suspend fun syncLastUsed(profile: Profile?) {
        profile ?: return
        quietly {
            val rows = JellyfinSession(context, profile).homeRows()
            // No channel is made from the background: the system's "add this channel" prompt needs a screen
            sync(profile, LauncherFeed.from(rows), allowCreate = false)
        }
    }

    /** Failures here (no provider, a revoked session, the network) must never reach the app. */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to show: the launcher keeps its rows and the next sync tries again
        }
    }

    suspend fun sync(profile: Profile, feed: LauncherFeed, allowCreate: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Rows of another profile mean nothing here, dismissed ones included
            val previous = prefs.getString(KEY_PROFILE, null)
            if (previous != null && previous != profile.id) resolver.delete(WatchNextPrograms.CONTENT_URI, null, null)
            syncWatchNext(profile.id, feed.watchNext)
            syncRecent(profile.id, feed.recent, allowCreate)
            prefs.edit().putString(KEY_PROFILE, profile.id).apply()
        }
    }

    private fun syncWatchNext(profileId: String, items: List<MediaItem>) {
        val specs = items.mapNotNull { ProgramSpec.from(it, profileId) }
        val existing = resolver.query(
            WatchNextPrograms.CONTENT_URI,
            arrayOf(WatchNextPrograms._ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_BROWSABLE),
            null, null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(WatchNextRow(c.getLong(0), c.getString(1), c.getInt(2) != 0))
            }
        }.orEmpty()
        val diff = WatchNextDiff.of(existing, specs.map { it.itemId })
        val now = System.currentTimeMillis()
        // Launchers sort by last engagement, newest first: the server's order is kept by stepping back from now
        val values = specs.withIndex().associate { (i, spec) -> spec.itemId to watchNextValues(spec, now - i * ENGAGEMENT_STEP_MS) }
        diff.remove.forEach { resolver.delete(TvContract.buildWatchNextProgramUri(it), null, null) }
        diff.update.forEach { (rowId, itemId) -> values[itemId]?.let { resolver.update(TvContract.buildWatchNextProgramUri(rowId), it, null, null) } }
        val added = diff.add.mapNotNull { values[it] }
        if (added.isNotEmpty()) resolver.bulkInsert(WatchNextPrograms.CONTENT_URI, added.toTypedArray())
    }

    private fun syncRecent(profileId: String, items: List<MediaItem>, allowCreate: Boolean) {
        val channelId = channelId(allowCreate) ?: return
        resolver.update(TvContract.buildChannelUri(channelId), channelValues(), null, null)
        val programs = items.mapNotNull { ProgramSpec.from(it, profileId) }.map { previewValues(channelId, it) }
        // Replace the lot: the channel only holds what is recent right now
        resolver.delete(TvContract.buildPreviewProgramsUriForChannel(channelId), null, null)
        if (programs.isNotEmpty()) resolver.bulkInsert(PreviewPrograms.CONTENT_URI, programs.toTypedArray())
    }

    /**
     * The "Recently added" channel: the one the first RawkoonTV made when it still exists, else a
     * preview channel of this package found in the provider, else a new one (never a second).
     */
    private fun channelId(allowCreate: Boolean): Long? {
        val saved = prefs.getString(KEY_CHANNEL, null)?.let { runCatching { ContentUris.parseId(Uri.parse(it)) }.getOrNull() }
        val id = saved?.takeIf(::channelExists) ?: ownPreviewChannel()
        if (id != null) {
            if (id != saved) prefs.edit().putString(KEY_CHANNEL, TvContract.buildChannelUri(id).toString()).apply()
            if (!prefs.getBoolean(KEY_LOGO, false)) writeLogo(id)
            return id
        }
        if (!allowCreate) return null
        val created = resolver.insert(Channels.CONTENT_URI, channelValues().apply { put(Channels.COLUMN_TYPE, Channels.TYPE_PREVIEW) }) ?: return null
        val createdId = ContentUris.parseId(created)
        prefs.edit().putString(KEY_CHANNEL, created.toString()).apply()
        writeLogo(createdId)
        try {
            TvContract.requestChannelBrowsable(context, createdId)
        } catch (e: Exception) {
            // The prompt couldn't open (no screen to show it on): the channel can still be added from the launcher
        }
        return createdId
    }

    private fun channelExists(id: Long) =
        resolver.query(TvContract.buildChannelUri(id), arrayOf(Channels._ID), null, null, null)?.use { it.moveToFirst() } == true

    private fun ownPreviewChannel(): Long? =
        resolver.query(Channels.CONTENT_URI, arrayOf(Channels._ID, Channels.COLUMN_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == Channels.TYPE_PREVIEW) return@use c.getLong(0)
            null
        }

    /** Name and "open the app" link, written on every sync: the first RawkoonTV's link pointed at an activity that is gone. */
    private fun channelValues() = ContentValues().apply {
        put(Channels.COLUMN_DISPLAY_NAME, context.getString(R.string.recently_added))
        put(Channels.COLUMN_APP_LINK_INTENT_URI, Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))
    }

    /** The app icon as the channel's logo. */
    private fun writeLogo(channelId: Long) {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, LOGO_SIZE, LOGO_SIZE)
        icon.draw(Canvas(bitmap))
        val written = resolver.openOutputStream(TvContract.buildChannelLogoUri(channelId))?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        bitmap.recycle()
        if (written) prefs.edit().putBoolean(KEY_LOGO, true).apply()
    }

    private fun programValues(spec: ProgramSpec) = ContentValues().apply {
        put(PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, spec.itemId)
        put(PreviewPrograms.COLUMN_TYPE, spec.type)
        put(PreviewPrograms.COLUMN_TITLE, spec.title)
        put(PreviewPrograms.COLUMN_EPISODE_TITLE, spec.episodeTitle)
        put(PreviewPrograms.COLUMN_SHORT_DESCRIPTION, spec.description)
        put(PreviewPrograms.COLUMN_POSTER_ART_URI, spec.artUrl)
        put(PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, spec.aspect)
        put(PreviewPrograms.COLUMN_DURATION_MILLIS, spec.durationMs)
        put(PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER, spec.seasonNumber?.toString())
        put(PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, spec.episodeNumber?.toString())
        put(PreviewPrograms.COLUMN_INTENT_URI, spec.link.intent(context).toUri(Intent.URI_INTENT_SCHEME))
    }

    private fun previewValues(channelId: Long, spec: ProgramSpec) = programValues(spec).apply {
        put(PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        if (spec.resumable) put(PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, spec.positionMs)
    }

    private fun watchNextValues(spec: ProgramSpec, engagement: Long) = programValues(spec).apply {
        put(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, spec.watchNextType())
        put(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, if (spec.resumable) spec.positionMs else 0)
        put(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, engagement)
    }

    private companion object {
        /** Kept from the first RawkoonTV: its channel's URI lives here. */
        const val PREFS = "channels"
        const val KEY_CHANNEL = "latest"
        const val KEY_PROFILE = "profile"
        const val KEY_LOGO = "logo"
        const val LOGO_SIZE = 512
        const val ENGAGEMENT_STEP_MS = 60_000L
    }
}
