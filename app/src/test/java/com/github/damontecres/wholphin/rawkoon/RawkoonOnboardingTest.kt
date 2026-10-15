package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class RawkoonOnboardingTest {
    private val client = mockk<RawkoonClient>()
    private val settings = mockk<RawkoonSettingsRepository>(relaxed = true)
    private val jellyfin = mockk<Jellyfin>()
    private val serverRepository = mockk<ServerRepository>()
    private val quickConnect = mockk<QuickConnectLogin>()
    private val onboarding = RawkoonOnboarding(client, settings, jellyfin, serverRepository, quickConnect)

    private fun bootstrap(
        jellyfinInfo: BootstrapJellyfin? = BootstrapJellyfin("https://jellyfin.example.test", true),
    ) = Bootstrap(RawkoonUser("u1", "Alex", false), jellyfinInfo, BootstrapTmdb("0123456789abcdef0123456789abcdef"))

    @Test
    fun reading_the_account_keeps_the_user_name() =
        runTest {
            coEvery { client.bootstrap() } returns bootstrap()
            assertEquals("https://jellyfin.example.test", onboarding.readAccount().jellyfin?.url)
            coVerify { settings.update(any()) }
        }

    @Test
    fun an_account_that_is_not_linked_stops_with_not_linked() =
        runTest {
            coEvery { client.bootstrap() } returns bootstrap(BootstrapJellyfin("https://jellyfin.example.test", linked = false))
            try {
                onboarding.readAccount()
                fail("expected NotLinked")
            } catch (_: RawkoonException.NotLinked) {
            }
        }

    @Test
    fun no_jellyfin_integration_stops_the_setup() =
        runTest {
            coEvery { client.bootstrap() } returns bootstrap(jellyfinInfo = null)
            try {
                onboarding.run {}
                fail("expected NoJellyfinIntegration")
            } catch (_: RawkoonException.NoJellyfinIntegration) {
            }
            coVerify(exactly = 0) { quickConnect.login(any(), any(), any(), any(), any()) }
        }

    @Test
    fun an_existing_session_on_the_same_server_is_kept() =
        runTest {
            val server = JellyfinServer(UUID.randomUUID(), "S", "https://Jellyfin.example.test/", null)
            val current = CurrentUser(server, JellyfinUser(id = UUID.randomUUID(), name = "Alex", serverId = server.id, accessToken = "tok"))
            every { serverRepository.current } returns MutableStateFlow(current)
            coEvery { client.bootstrap() } returns bootstrap()
            val steps = mutableListOf<OnboardingStep>()
            assertEquals(current, onboarding.run { steps += it })
            assertEquals(listOf(OnboardingStep.READING_ACCOUNT), steps)
            coVerify(exactly = 0) { quickConnect.login(any(), any(), any(), any(), any()) }
        }
}
