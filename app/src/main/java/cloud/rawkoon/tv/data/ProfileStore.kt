package cloud.rawkoon.tv.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * The saved profiles, in the order they were added. Used from the main thread; the file is tiny.
 * An unreadable file is moved aside to `<name>.bad` rather than overwritten by the next save.
 */
class ProfileStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "profiles.json"))

    private var cache: List<Profile>? = null

    @Synchronized
    fun all(): List<Profile> = cache ?: read().also { cache = it }

    fun get(id: String): Profile? = all().firstOrNull { it.id == id }

    /** The one used most recently; it gets the focus on "Who's watching?". */
    fun lastUsed(): Profile? = all().maxByOrNull { it.lastUsed }?.takeIf { it.lastUsed > 0 }

    /** Adds [profile], or updates the profile for the same account, keeping its id, place and PIN. */
    @Synchronized
    fun save(profile: Profile): Profile {
        val list = all()
        val existing = list.firstOrNull { it.id == profile.id || it.sameAccount(profile) }
        val saved = if (existing == null) profile else profile.copy(id = existing.id, pinHash = profile.pinHash ?: existing.pinHash)
        write(if (existing == null) list + saved else list.map { if (it.id == existing.id) saved else it })
        return saved
    }

    @Synchronized
    fun markUsed(id: String) = write(all().map { if (it.id == id) it.copy(lastUsed = System.currentTimeMillis()) else it })

    @Synchronized
    fun remove(id: String) = write(all().filterNot { it.id == id })

    private fun read(): List<Profile> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            val (profiles, dropped) = decodeCounting(text)
            // Entries that no longer parse would vanish on the next write: keep the original
            if (dropped > 0) file.copyTo(File(file.path + ".bad"), overwrite = true)
            profiles
        } catch (e: Exception) {
            val bad = File(file.path + ".bad")
            bad.delete()
            file.renameTo(bad)
            emptyList()
        }
    }

    private fun write(list: List<Profile>) {
        cache = list
        val text = encode(list)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun encode(list: List<Profile>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun decode(text: String): List<Profile> = decodeCounting(text).first

        /** The profiles that parse, and how many elements did not. */
        fun decodeCounting(text: String): Pair<List<Profile>, Int> {
            val array = JSONArray(text)
            val profiles = (0 until array.length()).mapNotNull { runCatching { Profile.fromJson(array.getJSONObject(it)) }.getOrNull() }
            return profiles to array.length() - profiles.size
        }
    }
}
