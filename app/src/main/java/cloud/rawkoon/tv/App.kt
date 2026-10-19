package cloud.rawkoon.tv

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import cloud.rawkoon.tv.data.Fetcher
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.data.LegacyImport
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.ProfileStore
import java.util.Locale

/** App-wide singletons, built once: no dependency injection, nothing hidden. */
class App : Application() {
    lateinit var profiles: ProfileStore
        private set
    lateinit var images: Images
        private set

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Language.wrap(base))

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileStore(this)
        images = Images(this, Fetcher(this))
        LegacyImport.run(this, profiles)
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
