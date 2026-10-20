package cloud.rawkoon.tv.update

import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
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

    /**
     * Copies [input] to [output], hashing the bytes as they pass: no second read of what was written.
     * [onBytes] gets the running byte count. Returns the byte count and the lowercase hex SHA-256.
     */
    fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit = {}): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        DigestInputStream(input, digest).use { hashed ->
            while (true) {
                val read = hashed.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                onBytes(copied)
            }
        }
        return copied to digest.digest().joinToString("") { "%02x".format(it) }
    }
}
