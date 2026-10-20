package cloud.rawkoon.tv.update

import java.io.File
import java.security.MessageDigest

/** SHA-256, as `sha256sum` writes and reads it. */
object Checksum {
    private val HASH = Regex("[0-9a-fA-F]{64}")

    /**
     * The hash a checksum file gives for [assetName]: lines read `<sha256>  <name>`, with `*` or `./`
     * before the name depending on how it was made. A bare hash counts too. Null when no line holds
     * a valid hash for that file, so a garbled or foreign file never verifies anything.
     */
    fun expected(text: String, assetName: String): String? = text.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { parts ->
            HASH.matches(parts[0]) && parts.getOrNull(1)?.trim()?.removePrefix("*")?.removePrefix("./").let { it == null || it == assetName }
        }
        ?.get(0)?.lowercase()

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
