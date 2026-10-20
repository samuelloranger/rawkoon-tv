package cloud.rawkoon.tv.update

import org.json.JSONObject

/**
 * A published release: its version, the APK built for this device's ABI and the SHA-256 file that
 * vouches for it. [size] is the APK's byte count, for download progress (0 when GitHub gave none).
 */
class Release(val version: String, val apkName: String, val apkUrl: String, val checksumUrl: String, val size: Long) {
    /** Downloads only ever come over https. */
    private val secure: Boolean get() = apkUrl.startsWith("https://") && checksumUrl.startsWith("https://")

    fun serialize(): String = JSONObject()
        .put("version", version).put("name", apkName).put("apk", apkUrl).put("sha256", checksumUrl).put("size", size)
        .toString()

    companion object {
        /** The universal APK, and the fallback for an ABI the release has no split for. */
        const val UNIVERSAL_APK = "RawkoonTV.apk"

        /** `RawkoonTV-arm64-v8a.apk`: the names the first RawkoonTV's updater looks for as well. */
        fun apkNameFor(abi: String) = "RawkoonTV-$abi.apk"

        /**
         * The asset to install: the first of the device's ABIs (most preferred first) the release
         * has an APK for, else the universal one; null when neither is attached.
         */
        fun pickApk(assetNames: Set<String>, abis: List<String>): String? =
            abis.map(::apkNameFor).firstOrNull { it in assetNames } ?: UNIVERSAL_APK.takeIf { it in assetNames }

        /**
         * GitHub's release JSON, or null for drafts, pre-releases and releases missing the APK or its
         * checksum: without a checksum there is nothing to verify, so nothing to offer.
         */
        fun fromGitHub(text: String, abis: List<String>): Release? {
            return try {
                val json = JSONObject(text)
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
                val tag = json.getString("tag_name")
                val assets = json.getJSONArray("assets")
                val byName = (0 until assets.length()).map { assets.getJSONObject(it) }.associateBy { it.getString("name") }
                val apkName = pickApk(byName.keys, abis) ?: return null
                val checksum = byName["$apkName.sha256"] ?: return null
                val apk = byName.getValue(apkName)
                Release(
                    tag.removePrefix("v"), apkName,
                    apk.getString("browser_download_url"), checksum.getString("browser_download_url"), apk.optLong("size", 0L),
                ).takeIf { it.secure }
            } catch (e: Exception) {
                null
            }
        }

        fun parse(text: String): Release? {
            return try {
                val json = JSONObject(text)
                Release(
                    json.getString("version"), json.getString("name"), json.getString("apk"),
                    json.getString("sha256"), json.optLong("size", 0L),
                ).takeIf { it.secure }
            } catch (e: Exception) {
                null
            }
        }
    }
}
