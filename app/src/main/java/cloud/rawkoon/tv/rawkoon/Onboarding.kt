package cloud.rawkoon.tv.rawkoon

import android.content.Context
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.quickConnect
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.IOException

enum class OnboardingStep { READING_ACCOUNT, SIGNING_IN_JELLYFIN }

/**
 * Right after a Rawkoon sign-in: read the account's setup, then sign in to its Jellyfin server
 * with a Quick Connect code that Rawkoon approves. The result is a profile ready to save.
 */
suspend fun onboard(context: Context, rawkoonUrl: String, token: String, onStep: (OnboardingStep) -> Unit): Profile {
    val client = RawkoonClient(rawkoonUrl)
    onStep(OnboardingStep.READING_ACCOUNT)
    val bootstrap = client.bootstrap(token)
    val jellyfinUrl = bootstrap.jellyfinUrl ?: throw RawkoonException.NoJellyfin()
    if (!bootstrap.jellyfinLinked) throw RawkoonException.NotLinked()
    onStep(OnboardingStep.SIGNING_IN_JELLYFIN)
    val approve: suspend (String) -> Unit = { code -> client.approveQuickConnect(token, code) }
    val user = try {
        try {
            quickConnect(context, jellyfinUrl, approve)
        } catch (e: RawkoonException.QuickConnectExpired) {
            // Jellyfin forgot the code between the initiate and the approval: one more try
            quickConnect(context, jellyfinUrl, approve)
        }
    } catch (e: RawkoonException) {
        throw e
    } catch (e: InvalidStatusException) {
        throw RawkoonException.Http(e.status)
    } catch (e: IOException) {
        throw RawkoonException.Unreachable(e)
    }
    return Profile(
        id = Profile.newId(),
        name = user.name.ifBlank { bootstrap.userName },
        jellyfinUrl = user.url,
        jellyfinUserId = user.userId,
        jellyfinToken = user.token,
        avatarTag = user.avatarTag,
        rawkoonUrl = rawkoonUrl,
        rawkoonToken = token,
        rawkoonUserId = bootstrap.userId.ifBlank { null },
        lastUsed = System.currentTimeMillis(),
    )
}

/** The message shown for a failed sign-in. */
fun Throwable.signInMessage(context: Context): String = when (this) {
    is RawkoonException.Unreachable -> context.getString(R.string.error_unreachable)
    is RawkoonException.InvalidUrl -> context.getString(R.string.error_invalid_url)
    is RawkoonException.InvalidCredentials -> context.getString(R.string.error_credentials)
    is RawkoonException.RateLimited -> context.getString(R.string.error_rate)
    is RawkoonException.NotLinked -> context.getString(R.string.error_not_linked)
    is RawkoonException.NoJellyfin -> context.getString(R.string.error_no_jellyfin)
    is RawkoonException.Http -> context.getString(R.string.error_generic, "HTTP $code")
    is IOException -> context.getString(R.string.error_unreachable)
    else -> context.getString(R.string.error_generic, javaClass.simpleName)
}
