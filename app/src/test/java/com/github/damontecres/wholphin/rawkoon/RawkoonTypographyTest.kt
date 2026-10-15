package com.github.damontecres.wholphin.rawkoon

import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RawkoonTypographyTest {
    private val base = Typography()

    @Test
    fun sizes_and_line_heights_stay_wholphins() {
        assertEquals(base.displayLarge.fontSize, RawkoonTypography.displayLarge.fontSize)
        assertEquals(base.titleMedium.lineHeight, RawkoonTypography.titleMedium.lineHeight)
        assertEquals(base.bodyLarge.fontSize, RawkoonTypography.bodyLarge.fontSize)
        assertEquals(base.labelSmall.lineHeight, RawkoonTypography.labelSmall.lineHeight)
    }

    @Test
    fun titles_use_one_family_semibold_and_body_another() {
        val serif = RawkoonTypography.headlineMedium.fontFamily
        assertEquals(FontWeight.SemiBold, RawkoonTypography.headlineMedium.fontWeight)
        assertEquals(serif, RawkoonTypography.displayLarge.fontFamily)
        assertEquals(serif, RawkoonTypography.titleLarge.fontFamily)
        assertEquals(serif, RawkoonTypography.titleMedium.fontFamily)
        val sans = RawkoonTypography.bodyMedium.fontFamily
        assertNotEquals(serif, sans)
        assertEquals(sans, RawkoonTypography.titleSmall.fontFamily)
        assertEquals(sans, RawkoonTypography.labelLarge.fontFamily)
        assertEquals(FontWeight.SemiBold, RawkoonTypography.titleSmall.fontWeight)
    }
}
