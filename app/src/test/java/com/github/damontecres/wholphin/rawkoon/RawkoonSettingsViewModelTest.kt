package com.github.damontecres.wholphin.rawkoon.ui

import com.github.damontecres.wholphin.rawkoon.RawkoonConfig
import com.github.damontecres.wholphin.rawkoon.RawkoonSession
import com.github.damontecres.wholphin.rawkoon.RawkoonSettingsRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RawkoonSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<RawkoonSettingsRepository>()
    private val session = mockk<RawkoonSession>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WholphinDispatchers.configure(dispatcher)
        every { repository.config } returns flowOf(RawkoonConfig("https://rawkoon.example.test", "t", "Alex"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WholphinDispatchers.reset()
    }

    @Test
    fun sign_out_ends_both_sessions() {
        RawkoonSettingsViewModel(repository, session).signOut()
        coVerify(exactly = 1) { session.signOut() }
    }
}
