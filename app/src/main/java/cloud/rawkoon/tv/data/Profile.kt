package cloud.rawkoon.tv.data

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * One person on this TV: a Jellyfin session and, when they signed in through Rawkoon, the
 * Rawkoon session that handed it over. Profiles come from sign-ins (or the one-time import from
 * the first RawkoonTV), never from a list in the code.
 */
data class Profile(
    val id: String,
    val name: String,
    val jellyfinUrl: String,
    val jellyfinUserId: String,
    val jellyfinToken: String,
    val avatarTag: String? = null,
    val rawkoonUrl: String? = null,
    val rawkoonToken: String? = null,
    val rawkoonUserId: String? = null,
    val pinHash: String? = null,
    val lastUsed: Long = 0L,
) {
    val hasPin: Boolean get() = pinHash != null

    fun checkPin(pin: String) = pinHash != null && pinHash == hashPin(id, pin)

    fun withPin(pin: String?) = copy(pinHash = pin?.takeIf { it.isNotEmpty() }?.let { hashPin(id, it) })

    /** Same Jellyfin account on the same server: signing in again updates it instead of adding a twin. */
    fun sameAccount(other: Profile) =
        jellyfinUserId.equals(other.jellyfinUserId, ignoreCase = true) && sameUrl(jellyfinUrl, other.jellyfinUrl)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("jellyfinUrl", jellyfinUrl)
        .put("jellyfinUserId", jellyfinUserId)
        .put("jellyfinToken", jellyfinToken)
        .putOpt("avatarTag", avatarTag)
        .putOpt("rawkoonUrl", rawkoonUrl)
        .putOpt("rawkoonToken", rawkoonToken)
        .putOpt("rawkoonUserId", rawkoonUserId)
        .putOpt("pinHash", pinHash)
        .put("lastUsed", lastUsed)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(json: JSONObject) = Profile(
            id = json.getString("id"),
            name = json.getString("name"),
            jellyfinUrl = json.getString("jellyfinUrl"),
            jellyfinUserId = json.getString("jellyfinUserId"),
            jellyfinToken = json.getString("jellyfinToken"),
            avatarTag = json.optStringOrNull("avatarTag"),
            rawkoonUrl = json.optStringOrNull("rawkoonUrl"),
            rawkoonToken = json.optStringOrNull("rawkoonToken"),
            rawkoonUserId = json.optStringOrNull("rawkoonUserId"),
            pinHash = json.optStringOrNull("pinHash"),
            lastUsed = json.optLong("lastUsed"),
        )

        /** Salted with the profile id, so two profiles with the same PIN don't share a hash. */
        fun hashPin(profileId: String, pin: String): String =
            MessageDigest.getInstance("SHA-256").digest("rawkoon-pin:$profileId:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun sameUrl(a: String, b: String) = a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)
    }
}
