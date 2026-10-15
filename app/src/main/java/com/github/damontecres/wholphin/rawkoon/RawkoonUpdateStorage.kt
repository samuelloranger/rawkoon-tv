package com.github.damontecres.wholphin.rawkoon

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.edit
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.util.Version
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

class NotEnoughStorageException(
    message: String,
) : Exception(message)

/**
 * The two storage problems of updating a TV with little room: starting a download that the installer can never fit, and
 * leaving the downloaded APK behind afterwards
 */
object RawkoonUpdateStorage {
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MARGIN_BYTES = 50 * BYTES_PER_MB
    private const val APK_COPIES = 3
    private const val PREFS = "rawkoon_update"
    private const val KEY_DOWNLOADED_FOR = "downloaded_for"

    /** The installer needs the APK, its own copy and the dexopt output, plus some room */
    fun requiredBytes(apkBytes: Long): Long = apkBytes * APK_COPIES + MARGIN_BYTES

    fun hasEnoughSpace(
        apkBytes: Long,
        freeBytes: Long,
    ): Boolean = freeBytes >= requiredBytes(apkBytes)

    /** Rounded up, so the message never promises less than is needed */
    fun megabytes(bytes: Long): Long = (bytes + BYTES_PER_MB - 1) / BYTES_PER_MB

    /** The downloaded file has done its job once the app is at least the version it was downloaded for */
    fun shouldDeleteApk(
        installed: Version,
        downloadedFor: Version?,
    ): Boolean = downloadedFor != null && installed.isAtLeast(downloadedFor)

    /**
     * Before downloading [release]: stop with a clear message when the data volume cannot hold the update, and remember
     * which version the download is for
     *
     * Blocking: call it on an IO thread.
     */
    fun prepare(
        context: Context,
        client: OkHttpClient,
        release: Release,
    ) {
        val size = release.downloadUrl?.let { apkSize(client, it) }
        if (size != null) {
            val free = freeBytes(context)
            if (!hasEnoughSpace(size, free)) {
                throw NotEnoughStorageException(
                    context.getString(
                        R.string.rawkoon_update_no_space,
                        megabytes(requiredBytes(size)),
                        free / BYTES_PER_MB,
                    ),
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_DOWNLOADED_FOR, release.version.toString())
        }
    }

    /**
     * At app start: after a successful update, delete the downloaded APK
     */
    fun cleanUp(context: Context) {
        val app = context.applicationContext
        thread(name = "rawkoon-apk-cleanup") {
            try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val downloadedFor = Version.tryFromString(prefs.getString(KEY_DOWNLOADED_FOR, null))
                val installed = Version.tryFromString(BuildConfig.VERSION_NAME) ?: return@thread
                if (shouldDeleteApk(installed, downloadedFor)) {
                    deleteDownloadedApk(app)
                    prefs.edit { remove(KEY_DOWNLOADED_FOR) }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not remove the downloaded update")
            }
        }
    }

    private fun deleteDownloadedApk(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rows =
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
                    arrayOf("${UpdateChecker.ASSET_NAME}%", "application/vnd.android.package-archive"),
                )
            Timber.i("Deleted %d downloaded update file(s)", rows)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), UpdateChecker.APK_NAME).delete()
        }
    }

    /** What the installer can use: free space plus what the system would clear from caches, else the plain free space */
    private fun freeBytes(context: Context): Long {
        val plain = context.filesDir.usableSpace
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return plain
        return try {
            val manager = context.getSystemService(StorageManager::class.java)
            manager.getAllocatableBytes(manager.getUuidForPath(context.filesDir))
        } catch (e: Exception) {
            Timber.w(e, "Allocatable bytes unavailable")
            plain
        }
    }

    /** The APK's size from a one-byte ranged request, so nothing is downloaded to find out */
    private fun apkSize(
        client: OkHttpClient,
        url: String,
    ): Long? =
        try {
            client.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute().use { response ->
                response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: response.body.contentLength().takeIf { it > 1 }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read the update size")
            null
        }
}
