package com.github.damontecres.wholphin.rawkoon

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawkoonSettingsRepositoryTest {
    private class MemoryDataStore : DataStore<RawkoonSettings> {
        private val state = MutableStateFlow(RawkoonSettings.getDefaultInstance())
        override val data: Flow<RawkoonSettings> = state

        override suspend fun updateData(transform: suspend (t: RawkoonSettings) -> RawkoonSettings): RawkoonSettings {
            state.value = transform(state.value)
            return state.value
        }
    }

    private val repo = RawkoonSettingsRepository(MemoryDataStore())

    @Test
    fun defaults_are_empty_and_not_configured() =
        runTest {
            val c = repo.current()
            assertEquals("", c.url)
            assertFalse(c.hasUrl)
            assertFalse(c.isConfigured)
        }

    @Test
    fun a_url_alone_is_not_a_session() =
        runTest {
            repo.update { it.copy(url = " https://rawkoon.example.test/ ") }
            val c = repo.current()
            assertEquals("https://rawkoon.example.test/", c.url)
            assertTrue(c.hasUrl)
            assertFalse(c.isConfigured)
        }

    @Test
    fun clearing_the_session_keeps_the_url() =
        runTest {
            repo.update { it.copy(url = "https://rawkoon.example.test", token = "tok", userName = "Alex") }
            assertTrue(repo.current().isConfigured)
            repo.clearSession()
            val c = repo.current()
            assertEquals("https://rawkoon.example.test", c.url)
            assertEquals("", c.token)
            assertEquals("", c.userName)
            assertFalse(c.isConfigured)
        }
}
