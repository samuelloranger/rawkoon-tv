package com.github.damontecres.wholphin.rawkoon

import androidx.datastore.core.DataMigration
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.update

/**
 * Settings saved with the upstream default update URL would keep offering upstream releases: move them to this app's
 * releases. A URL the user changed on purpose is left alone.
 */
object UpdateUrlMigration : DataMigration<AppPreferences> {
    const val UPSTREAM_DEFAULT = "https://api.github.com/repos/damontecres/Wholphin/releases/latest"

    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean = currentData.updateUrl == UPSTREAM_DEFAULT

    override suspend fun migrate(currentData: AppPreferences): AppPreferences =
        currentData.update { updateUrl = AppPreference.UpdateUrl.defaultValue }

    override suspend fun cleanUp() {}
}
