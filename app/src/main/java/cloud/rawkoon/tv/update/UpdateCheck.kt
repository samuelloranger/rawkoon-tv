package cloud.rawkoon.tv.update

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import cloud.rawkoon.tv.BuildConfig
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.Prefs
import java.util.concurrent.Executors

/**
 * Asks GitHub for the latest release at most once a day: the answer is kept in preferences, so a
 * failed request changes nothing and the settings read it offline. Only the real app updates
 * itself: the `.next` test builds have another package name, which an update could never replace.
 */
class UpdateCheck(context: Context, private val fetcher: Fetcher) {
    private val prefs = Prefs.of(context)
    private val installed = Versions.installed(context)

    /** False in test builds, which neither check nor install. */
    val enabled: Boolean get() = BuildConfig.APPLICATION_ID == RELEASE_ID

    /** When the last check ran, in epoch millis; 0 before the first. */
    val checkedAt: Long get() = prefs.getLong(CHECKED_AT, 0L)

    /** True when the last check could not reach GitHub or read what it found. */
    val failed: Boolean get() = prefs.getBoolean(FAILED, false)

    /**
     * Asks GitHub when the last check is a day old, or at once when [force] (the settings button).
     * Blocking: use [refreshAsync] from the UI.
     */
    fun refresh(force: Boolean = false) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        // A clock that jumped backwards (no real-time clock until the network time arrives) counts as due.
        if (!force && now - checkedAt in 0 until DAY_MS) return
        // Recorded before the request, so an unreachable GitHub is asked once a day, not on every launch.
        prefs.edit().putLong(CHECKED_AT, now).apply()
        val release = fetcher.text(LATEST)?.let { Release.fromGitHub(it, Build.SUPPORTED_ABIS.toList()) }
        prefs.edit().apply {
            putBoolean(FAILED, release == null)
            if (release != null) putString(LATEST_RELEASE, release.serialize())
        }.apply()
    }

    /** [refresh] off the main thread; [onDone] then runs on the main thread. */
    fun refreshAsync(force: Boolean = false, onDone: () -> Unit = {}) {
        executor.execute {
            refresh(force)
            main.post(onDone)
        }
    }

    /** What the last check found, when it is newer than this build. No network. */
    fun pending(): Release? {
        if (!enabled) return null
        val release = prefs.getString(LATEST_RELEASE, null)?.let(Release::parse) ?: return null
        return release.takeIf { Versions.isNewer(it.version, installed) }
    }

    private companion object {
        const val RELEASE_ID = "cloud.rawkoon.tv"
        const val LATEST = "https://api.github.com/repos/samuelloranger/rawkoon-tv/releases/latest"
        const val CHECKED_AT = "update:checkedAt"
        const val FAILED = "update:failed"
        const val LATEST_RELEASE = "update:latest"
        const val DAY_MS = 24 * 60 * 60_000L
        val executor = Executors.newSingleThreadExecutor { Thread(it, "update-check") }
        val main = Handler(Looper.getMainLooper())
    }
}
