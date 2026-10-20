package cloud.rawkoon.tv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.ZonedDateTime

/**
 * The first RawkoonTV (same app id, same signing key) kept its Jellyfin users in a Room
 * database and its one Rawkoon session in a DataStore file. On the first launch after the
 * update, those become profiles, so nobody has to sign in again. Runs once; the old files are
 * left in place and never read again.
 */
object LegacyImport {
    /** A user row from the old database. */
    data class OldUser(
        val id: String,
        val name: String,
        val token: String,
        val pin: String?,
        val serverUrl: String,
        val lastUsed: Long,
    )

    /** The fields of the old Rawkoon settings that matter here. */
    data class OldRawkoon(val url: String, val token: String, val userName: String, val userId: String)

    /**
     * Marked done once the old data was read, or turned out unreadable: a damaged old file would
     * otherwise be retried on every launch, and signing in again is the fallback. A failure writing
     * the new profiles leaves it unmarked, so the next launch tries again.
     */
    fun run(context: Context, store: ProfileStore): Int {
        val prefs = Prefs.of(context)
        if (prefs.getBoolean(DONE, false)) return 0
        val old = runCatching { readOld(context) }.getOrNull()
        val profiles = old?.let { (users, rawkoon) -> toProfiles(users, rawkoon) }.orEmpty()
        val saved = runCatching {
            old?.second?.url?.takeIf { it.isNotBlank() }?.let { Prefs.setRawkoonUrl(context, it) }
            profiles.forEach { store.save(it) }
        }.isSuccess
        if (saved) prefs.edit().putBoolean(DONE, true).apply()
        return if (saved) profiles.size else 0
    }

    private fun readOld(context: Context): Pair<List<OldUser>, OldRawkoon?> {
        val database = context.getDatabasePath("wholphin")
        val users = if (database.isFile) readUsers(database) else emptyList()
        val rawkoon = File(context.filesDir, "datastore/rawkoon_settings.pb").takeIf { it.isFile }
            ?.let { runCatching { parseRawkoon(it.readBytes()) }.getOrNull() }
        return users to rawkoon
    }

    private fun readUsers(file: File): List<OldUser> = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "select u.id, u.name, u.accessToken, u.pin, u.lastUsed, s.url from users u " +
                    "join servers s on s.id = u.serverId where u.accessToken is not null and u.accessToken != ''",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            OldUser(
                                id = c.getString(0),
                                name = c.getString(1) ?: "",
                                token = c.getString(2),
                                pin = c.getString(3)?.takeIf { it.isNotBlank() },
                                serverUrl = c.getString(5),
                                lastUsed = parseTime(c.getString(4)),
                            ),
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * One profile per old user. The Rawkoon session goes to the user with the same name, or to the
     * only user when there is one; with several users and no name match it is left out rather than
     * handed to the wrong person.
     */
    fun toProfiles(users: List<OldUser>, rawkoon: OldRawkoon?): List<Profile> {
        val session = rawkoon?.takeIf { it.token.isNotBlank() && it.url.isNotBlank() }
        val owner = session?.let { s ->
            users.firstOrNull { it.name.equals(s.userName, ignoreCase = true) } ?: users.singleOrNull()
        }
        return users.sortedByDescending { it.lastUsed }.map { user ->
            val id = Profile.newId()
            val base = Profile(
                id = id,
                name = user.name,
                jellyfinUrl = user.serverUrl.trimEnd('/'),
                jellyfinUserId = user.id.replace("-", "").lowercase(),
                jellyfinToken = user.token,
                lastUsed = user.lastUsed,
            )
            val withRawkoon = if (user === owner && session != null) {
                base.copy(rawkoonUrl = session.url, rawkoonToken = session.token, rawkoonUserId = session.userId.ifBlank { null })
            } else {
                base
            }
            withRawkoon.withPin(user.pin)
        }
    }

    private fun parseTime(text: String?): Long =
        text?.let { runCatching { ZonedDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

    /**
     * Reads the old settings' protobuf by hand (fields 1 url, 2 token, 3 user name, 5 user id), so
     * the app needs no protobuf library for a file it reads once.
     */
    fun parseRawkoon(bytes: ByteArray): OldRawkoon {
        val strings = HashMap<Int, String>()
        var i = 0
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (i < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> varint()
                1 -> i += 8
                2 -> {
                    val length = varint().toInt()
                    if (field !in strings) strings[field] = String(bytes, i, length, Charsets.UTF_8)
                    i += length
                }
                5 -> i += 4
                else -> break
            }
        }
        return OldRawkoon(strings[1].orEmpty(), strings[2].orEmpty(), strings[3].orEmpty(), strings[5].orEmpty())
    }

    private const val DONE = "legacy:imported"
}
