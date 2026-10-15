package com.github.damontecres.wholphin.rawkoon

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class RawkoonSessionTest {
    private val settings = mockk<RawkoonSettingsRepository>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val navigation = mockk<SetupNavigationManager>(relaxed = true)
    private val session = RawkoonSession(settings, serverRepository, navigation)

    @Test
    fun an_expired_session_clears_the_token_and_goes_to_sign_in_keeping_jellyfin() =
        runTest {
            session.expired()
            coVerify(exactly = 1) { settings.clearSession() }
            verify { navigation.navigateTo(SetupDestination.RawkoonFlow) }
            coVerify(exactly = 0) { serverRepository.removeUser(any()) }
        }

    @Test
    fun sign_out_also_drops_the_jellyfin_session() =
        runTest {
            val server = JellyfinServer(UUID.randomUUID(), "S", "https://jellyfin.example.test", null)
            val user = JellyfinUser(id = UUID.randomUUID(), name = "Alex", serverId = server.id, accessToken = "tok")
            every { serverRepository.current } returns MutableStateFlow(CurrentUser(server, user))
            coEvery { serverRepository.removeUser(user) } returns Unit
            session.signOut()
            coVerifyOrder {
                settings.clearSession()
                serverRepository.removeUser(user)
            }
            verify { navigation.navigateTo(SetupDestination.RawkoonFlow) }
        }
}
