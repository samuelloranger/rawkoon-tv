package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlMigrationTest {
    @Test
    fun the_default_update_url_points_at_this_apps_releases() {
        assertEquals(
            "https://api.github.com/repos/samuelloranger/rawkoon-tv/releases/latest",
            AppPreference.UpdateUrl.defaultValue,
        )
    }

    @Test
    fun settings_saved_with_the_upstream_default_move_to_this_apps_releases() =
        runBlocking {
            val saved = AppPreferences.newBuilder().setUpdateUrl(UpdateUrlMigration.UPSTREAM_DEFAULT).build()
            assertTrue(UpdateUrlMigration.shouldMigrate(saved))
            assertEquals(AppPreference.UpdateUrl.defaultValue, UpdateUrlMigration.migrate(saved).updateUrl)
        }

    @Test
    fun a_url_chosen_by_the_user_is_kept() =
        runBlocking {
            val custom = AppPreferences.newBuilder().setUpdateUrl("https://api.github.com/repos/someone/fork/releases/latest").build()
            assertFalse(UpdateUrlMigration.shouldMigrate(custom))
            val current = AppPreferences.newBuilder().setUpdateUrl(AppPreference.UpdateUrl.defaultValue).build()
            assertFalse(UpdateUrlMigration.shouldMigrate(current))
        }
}
