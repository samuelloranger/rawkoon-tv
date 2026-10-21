package cloud.rawkoon.tv

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.data.LegacyImport
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.ProfileStore
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.MediaWords
import cloud.rawkoon.tv.launcher.LauncherChannels
import cloud.rawkoon.tv.launcher.LauncherSyncJob
import cloud.rawkoon.tv.update.UpdateCheck
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull

/** App-wide singletons, built once: no dependency injection, nothing hidden. */
class App : Application() {
    lateinit var profiles: ProfileStore
        private set
    lateinit var images: Images
        private set
    lateinit var launcher: LauncherChannels
        private set

    /** The last item the player played, so the page below can focus it when the player closes. */
    var lastPlayed: MediaItem? = null

    /** For calls that must finish after their screen is gone, like reporting where playback stopped. */
    val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The latest stop report still going out. Reports run one at a time, so when it ends the earlier ones have. */
    var pendingStop: Job? = null

    /** Waits for [pendingStop], so a page reading progress sees it: the server saves it only after ending a transcode. Capped so the UI never hangs. */
    suspend fun awaitStop() {
        withTimeoutOrNull(STOP_WAIT_MS) { pendingStop?.join() }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Language.wrap(base))

    override fun onCreate() {
        super.onCreate()
        MediaWords.source = resources
        profiles = ProfileStore(this)
        images = Images(this, Fetcher(this))
        launcher = LauncherChannels(this)
        LegacyImport.run(this, profiles)
        LauncherSyncJob.schedule(this)
        // Once a day at most, off the main thread; settings offer what it finds.
        if (Prefs.autoUpdateCheck(this)) UpdateCheck(this, Fetcher(this)).refreshAsync()
    }

    private companion object {
        /** The server waits a second or two for a transcode to die before it saves progress. */
        const val STOP_WAIT_MS = 3_000L
    }
}

/** The app language: the TV's, unless the settings force French or English. */
object Language {
    fun wrap(base: Context): Context {
        val choice = Prefs.language(base)
        if (choice == "system") return base
        val locale = Locale.forLanguageTag(choice)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
