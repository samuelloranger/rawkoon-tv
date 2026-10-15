package com.github.damontecres.wholphin.rawkoon

import androidx.datastore.core.DataMigration
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.update

/**
 * Installs made before the Rawkoon theme existed still have the old default, purple: switch them to Rawkoon once. The
 * flag keeps a later choice of purple.
 */
object ThemeMigration : DataMigration<AppPreferences> {
    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean = !currentData.interfacePreferences.rawkoonThemeApplied

    override suspend fun migrate(currentData: AppPreferences): AppPreferences =
        currentData.update {
            interfacePreferences =
                currentData.interfacePreferences
                    .toBuilder()
                    .apply {
                        if (appThemeColors == AppThemeColors.PURPLE) appThemeColors = AppThemeColors.RAWKOON
                        rawkoonThemeApplied = true
                    }.build()
        }

    override suspend fun cleanUp() {}
}
