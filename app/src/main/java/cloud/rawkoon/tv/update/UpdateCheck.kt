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
 * Asks GitHub for the latest release at most every few hours: the answer is kept in preferences, so a
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

    /** True when the last check got no reply from GitHub, or one that is not a release object. */
    val failed: Boolean get() = prefs.getBoolean(FAILED, false)

    /**
     * Asks GitHub when the last check is [INTERVAL_MS] old, or at once when [force] (the settings button).
     * Blocking: use [refreshAsync] from the UI.
     */
    fun refresh(force: Boolean = false) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (!force && !isDue(now, checkedAt, failed)) return
        // Recorded before the request, so an unreachable GitHub is asked at most every RETRY_MS, not on every return to the app.
        prefs.edit().putLong(CHECKED_AT, now).apply()
        val text = fetcher.text(LATEST)
        val release = text?.let { Release.fromGitHub(it, Build.SUPPORTED_ABIS.toList()) }
        prefs.edit().apply {
            // A readable reply with nothing to install (no asset, draft, pre-release) is "no update"; no reply or a garbled one is retried
            putBoolean(FAILED, text == null || !Release.isGitHubRelease(text))
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

    companion object {
        /**
         * Whether a check is due: [INTERVAL_MS] after the last one, or [RETRY_MS] after one that failed
         * (the network not back yet after the TV wakes). A clock that jumped backwards (no real-time
         * clock until the network time arrives) counts as due.
         */
        fun isDue(now: Long, checkedAt: Long, failed: Boolean): Boolean = now - checkedAt !in 0 until (if (failed) RETRY_MS else INTERVAL_MS)

        private const val RELEASE_ID = "cloud.rawkoon.tv"
        private const val LATEST = "https://api.github.com/repos/samuelloranger/rawkoon-tv/releases/latest"
        private const val CHECKED_AT = "update:checkedAt"
        private const val FAILED = "update:failed"
        private const val LATEST_RELEASE = "update:latest"
        /** Six hours: a release reaches a TV the same day, and GitHub's limit for anonymous requests is far off. */
        const val INTERVAL_MS = 6 * 60 * 60_000L
        const val RETRY_MS = 15 * 60_000L
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "update-check") }
        private val main = Handler(Looper.getMainLooper())
    }
}
