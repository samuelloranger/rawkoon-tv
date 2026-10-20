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

/**
 * Downloads a release's APK, checks it against the published SHA-256 and hands it to Android's
 * package installer. Android then asks the user to confirm (and, the first time, to let the app
 * install apps); [InstallResult] receives the outcome. One install runs at a time, app-wide.
 */
class Installer(context: Context, private val fetcher: Fetcher) {
    private val context = context.applicationContext

    /**
     * [onProgress] gets the percent downloaded, on the main thread. [onDone] runs on the main thread
     * with null once Android has the update (it asks the user next), or with a message string
     * resource when it failed. Ignored while an install runs.
     */
    fun install(release: Release, onProgress: (Int) -> Unit, onDone: (Int?) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            val apk = File(context.cacheDir, Release.UNIVERSAL_APK)
            val error = try {
                download(release, apk, onProgress)
                verify(apk, release)
                commit(apk)
                null
            } catch (e: Failure) {
                e.messageRes
            } catch (e: Exception) {
                R.string.update_failed
            } finally {
                apk.delete()
            }
            busy.set(false)
            main.post { onDone(error) }
        }
    }

    private fun download(release: Release, apk: File, onProgress: (Int) -> Unit) {
        var shown = -1
        fetcher.open(release.apkUrl).use { input ->
            apk.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    val percent = if (release.size > 0) (copied * 100 / release.size).toInt().coerceIn(0, 100) else 0
                    if (percent != shown) {
                        shown = percent
                        main.post { onProgress(percent) }
                    }
                }
            }
        }
    }

    /**
     * Refuses to install without a matching checksum. It catches a broken download; Android's
     * same-signature rule is what keeps a foreign APK out.
     */
    private fun verify(apk: File, release: Release) {
        val expected = fetcher.text(release.checksumUrl)?.let { Checksum.expected(it, release.apkName) }
            ?: throw Failure(R.string.update_failed)
        if (expected != Checksum.of(apk)) throw Failure(R.string.update_corrupt)
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
        installer.openSession(id).use { session ->
            try {
                apk.inputStream().use { input ->
                    session.openWrite(Release.UNIVERSAL_APK, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(resultIntent(id).intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
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
        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean(false)

        val running: Boolean get() = busy.get()
    }
}
