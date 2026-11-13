package cloud.rawkoon.tv.rawkoon

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The shelf answers a profile last saw, so the Collections row and Home still show them when
 * Rawkoon can't be reached. One file for the list and one per shelf: a shelf page opened from the
 * row needs no list, and a stale shelf never outlives its list entry. Files are small and read off
 * the main thread.
 */
class ShelfCache(private val listFile: File, private val detailDir: File) {
    // filesDir, not DiskCache: that is an LRU of images trimmed by size, and Android may purge cacheDir;
    // offline, this is the only copy of the shelves.
    constructor(context: Context, profileId: String) :
        this(File(context.filesDir, "shelves-$profileId.json"), File(context.filesDir, "shelves-$profileId"))

    /** The saved list; null when none was saved, which is not the same as a saved empty list. */
    fun list(): List<ShelfSummary>? = runCatching {
        val array = JSONArray(listFile.readText())
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseShelfSummary) }
    }.getOrNull()

    /** Saves [shelves] and drops the copies of shelves no longer listed. */
    fun saveList(shelves: List<ShelfSummary>) {
        write(listFile, JSONArray().apply { shelves.forEach { put(it.toJson()) } }.toString())
        val keep = shelves.map { "${it.id}.json" }.toSet()
        detailDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    fun detail(id: Int): ShelfDetail? = runCatching {
        JSONObject(File(detailDir, "$id.json").readText())
    }.getOrNull()?.let(::parseShelfDetail)

    fun saveDetail(detail: ShelfDetail) = write(File(detailDir, "${detail.summary.id}.json"), detail.toJson().toString())

    /** Written aside then swapped in, so a reader never sees half a file. */
    private fun write(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val partial = File(file.path + ".tmp")
            partial.writeText(text)
            partial.renameTo(file)
        }
    }
}
