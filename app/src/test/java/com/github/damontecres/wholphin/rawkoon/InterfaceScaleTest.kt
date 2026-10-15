package com.github.damontecres.wholphin.rawkoon

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceScaleTest {
    @Test
    fun the_options_map_to_their_factors() {
        assertEquals(listOf(100, 90, 85, 80), InterfaceScale.OPTIONS_PERCENT)
        assertEquals(listOf(1.0f, 0.9f, 0.85f, 0.8f), InterfaceScale.OPTIONS_PERCENT.map { InterfaceScale.factor(it) })
    }

    @Test
    fun the_default_is_90_for_new_and_existing_installs() {
        assertEquals(90, InterfaceScale.DEFAULT_PERCENT)
        assertEquals(0.9f, InterfaceScale.factor(0), 0.0001f)
        assertEquals(90, RawkoonConfig().interfaceScalePercent)
        assertEquals(90, InterfaceScale.normalize(0))
    }

    @Test
    fun an_unknown_stored_value_falls_back_to_the_default() {
        assertEquals(90, InterfaceScale.normalize(73))
        assertEquals(90, InterfaceScale.normalize(-5))
        assertEquals(80, InterfaceScale.normalize(80))
    }
}
