package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.ui.theme.getThemeColors
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMigrationTest {
    private fun prefs(
        theme: AppThemeColors,
        applied: Boolean = false,
    ) = AppPreferences
        .newBuilder()
        .setInterfacePreferences(InterfacePreferences.newBuilder().setAppThemeColors(theme).setRawkoonThemeApplied(applied))
        .build()

    private fun migrate(p: AppPreferences) = runBlocking { if (ThemeMigration.shouldMigrate(p)) ThemeMigration.migrate(p) else p }

    @Test
    fun rawkoon_is_the_default_theme() {
        assertEquals(AppThemeColors.RAWKOON, AppPreference.ThemeColors.defaultValue)
    }

    @Test
    fun purple_becomes_rawkoon_once() {
        val first = migrate(prefs(AppThemeColors.PURPLE))
        assertEquals(AppThemeColors.RAWKOON, first.interfacePreferences.appThemeColors)
        assertTrue(first.interfacePreferences.rawkoonThemeApplied)
        assertFalse(runBlocking { ThemeMigration.shouldMigrate(first) })
    }

    @Test
    fun another_theme_is_kept_but_the_migration_is_still_spent() {
        val result = migrate(prefs(AppThemeColors.GREEN))
        assertEquals(AppThemeColors.GREEN, result.interfacePreferences.appThemeColors)
        assertTrue(result.interfacePreferences.rawkoonThemeApplied)
    }

    @Test
    fun purple_chosen_after_the_migration_stays_purple() {
        val migrated = migrate(prefs(AppThemeColors.PURPLE))
        val chosen = migrated.toBuilder().setInterfacePreferences(migrated.interfacePreferences.toBuilder().setAppThemeColors(AppThemeColors.PURPLE)).build()
        assertEquals(AppThemeColors.PURPLE, migrate(chosen).interfacePreferences.appThemeColors)
    }

    @Test
    fun the_picker_maps_rawkoon_to_its_position_and_back() {
        assertEquals(8, RawkoonTheme.toIndex(AppThemeColors.RAWKOON))
        assertEquals(AppThemeColors.RAWKOON, RawkoonTheme.fromIndex(8))
        assertEquals(100, AppThemeColors.RAWKOON.number)
        AppThemeColors.entries.filter { it != AppThemeColors.UNRECOGNIZED }.forEach {
            assertEquals(it, RawkoonTheme.fromIndex(RawkoonTheme.toIndex(it)))
        }
        // The picker's option count: every real theme has its own position
        assertEquals(
            (0..8).toList(),
            AppThemeColors.entries.filter { it != AppThemeColors.UNRECOGNIZED }.map { RawkoonTheme.toIndex(it) }.sorted(),
        )
    }

    @Test
    fun every_theme_has_colors_and_rawkoon_is_the_warm_dark_one() {
        AppThemeColors.entries.forEach { assertNotNull(getThemeColors(it)) }
        val scheme = getThemeColors(AppThemeColors.RAWKOON).darkScheme
        assertEquals(0xFF1C1715.toInt(), scheme.background.toArgb())
        assertEquals(0xFFE8A06A.toInt(), scheme.border.toArgb())
    }
}
