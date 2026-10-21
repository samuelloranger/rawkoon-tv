package cloud.rawkoon.tv.rawkoon

/** Why a Rawkoon call failed, each with its own message on screen. */
sealed class RawkoonException(cause: Throwable? = null) : Exception(cause) {
    class Unreachable(cause: Throwable? = null) : RawkoonException(cause)
    class InvalidUrl : RawkoonException()
    class Http(val code: Int) : RawkoonException()
    class InvalidResponse(cause: Throwable? = null) : RawkoonException(cause)
    class InvalidCredentials : RawkoonException()
    class RateLimited : RawkoonException()
    class NoJellyfin : RawkoonException()
    class NotLinked : RawkoonException()
    class QuickConnectExpired : RawkoonException()

    /** Rawkoon answered, but its call to Jellyfin failed. */
    class JellyfinUnreachable : RawkoonException()

    /** The server has no route for the call: it predates it. */
    class ServerTooOld : RawkoonException()

    /** The token was refused: the Rawkoon session ended, so the profile has to link its account again. */
    class Unauthorized : RawkoonException()
}

/** A code the user approves on another device, then [deviceCode] is exchanged for a token. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val interval: Int,
) {
    /** ABCD1234 shown as ABCD-1234. */
    val displayCode: String
        get() = if (userCode.length == 8 && '-' !in userCode) "${userCode.take(4)}-${userCode.drop(4)}" else userCode
}

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data object Expired : DevicePoll
    data object Denied : DevicePoll
    data class Granted(val token: String) : DevicePoll
}

/** What a signed-in device needs to set itself up. */
data class Bootstrap(
    val userId: String,
    val userName: String,
    val jellyfinUrl: String?,
    val jellyfinLinked: Boolean,
)
