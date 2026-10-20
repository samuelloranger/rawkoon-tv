package cloud.rawkoon.tv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Fetcher
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Where the app's install stands; what a screen shows when it attaches to [Installer]. */
sealed interface InstallState {
    data object Idle : InstallState

    /** [percent] is 0 until the size is known. */
    data class Downloading(val percent: Int) : InstallState

    /** Failed with this message string resource. */
    data class Failed(val messageRes: Int) : InstallState

    /** Android has the update and asks the user next. */
    data object Ready : InstallState
}

/**
 * Downloads a release's APK, checks it against the published SHA-256 and hands it to Android's
 * package installer. Android then asks the user to confirm (and, the first time, to let the app
 * install apps); [InstallResult] receives the outcome. One install runs at a time, app-wide, and
 * keeps going when the screen that started it closes: the latest [InstallState] and a single
 * listener live in the companion, so any screen can pick up where it stands.
 */
class Installer(context: Context, private val fetcher: Fetcher) {
    private val context = context.applicationContext

    /** Starts downloading [release]. Ignored while an install runs. Main thread. */
    fun install(release: Release) {
        if (!busy.compareAndSet(false, true)) return
        publish(InstallState.Downloading(0))
        executor.execute {
            val apk = File(context.cacheDir, Release.UNIVERSAL_APK)
            val result = try {
                val expected = Checksum.expected(fetcher.text(release.checksumUrl).orEmpty(), release.apkName)
                    ?: throw Failure(R.string.update_failed)
                download(release, apk, expected)
                commit(apk)
                InstallState.Ready
            } catch (e: Failure) {
                InstallState.Failed(e.messageRes)
            } catch (e: Exception) {
                InstallState.Failed(R.string.update_failed)
            } finally {
                apk.delete()
            }
            main.post {
                busy.set(false)
                publish(result)
            }
        }
    }

    /**
     * Streams the APK to [apk], hashing as it goes. A byte count other than the published size, or a
     * hash other than [expected], is a damaged download. This catches a broken transfer; Android's
     * same-signature rule is what keeps a foreign APK out.
     */
    private fun download(release: Release, apk: File, expected: String) {
        var shown = -1
        val (copied, hash) = fetcher.open(release.apkUrl).use { input ->
            apk.outputStream().use { output ->
                Checksum.copy(input, output) { bytes ->
                    val percent = if (release.size > 0) (bytes * 100 / release.size).toInt().coerceIn(0, 100) else 0
                    if (percent != shown) {
                        shown = percent
                        main.post { if (busy.get()) publish(InstallState.Downloading(percent)) }
                    }
                }
            }
        }
        if ((release.size > 0 && copied != release.size) || hash != expected) throw Failure(R.string.update_corrupt)
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once the app installed itself, Android 12+ can apply later updates without asking.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(Release.UNIVERSAL_APK, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(resultIntent(id).intentSender)
            }
        } catch (e: Exception) {
            // Any failure after createSession would otherwise leave the session open.
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private fun resultIntent(sessionId: Int): PendingIntent {
        // Mutable: the installer adds the status and the confirmation intent as extras.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, InstallResult::class.java).setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    /** A failure with its own message for the user. */
    private class Failure(val messageRes: Int) : Exception()

    companion object {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "update-install") }
        private val main = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean(false)
        private var owner: Any? = null
        private var listener: ((InstallState) -> Unit)? = null

        /** The latest state; main thread only. */
        var state: InstallState = InstallState.Idle
            private set

        val running: Boolean get() = state is InstallState.Downloading

        /** Makes [who] the one screen that follows the install; it is told of each change on the main thread. */
        fun listen(who: Any, onChange: (InstallState) -> Unit) {
            owner = who
            listener = onChange
        }

        /** Detaches [who], unless another screen has attached since. */
        fun unlisten(who: Any) {
            if (owner === who) {
                owner = null
                listener = null
            }
        }

        /** Forgets a finished outcome (ready or failed) once a screen has shown it. */
        fun acknowledge() {
            if (state !is InstallState.Downloading) state = InstallState.Idle
        }

        private fun publish(next: InstallState) {
            state = next
            listener?.invoke(next)
        }
    }
}
