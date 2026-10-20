package cloud.rawkoon.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private val hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    private fun asset(name: String, size: Long = 1234) =
        """{"name":"$name","size":$size,"state":"uploaded","browser_download_url":"https://github.com/samuelloranger/rawkoon-tv/releases/download/v3.1.0/$name"}"""

    /** What GitHub returns for a release built by the release workflow. */
    private fun payload(
        tag: String = "v3.1.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        names: List<String> = ALL_ASSETS,
    ) = """{"id":1,"tag_name":"$tag","name":"$tag","draft":$draft,"prerelease":$prerelease,
        "body":"notes","assets":[${names.joinToString(",") { asset(it) }}]}"""

    @Test
    fun newerVersionsCompareNumerically() {
        assertTrue(Versions.isNewer("3.10.0", "3.2.0"))
        assertTrue(Versions.isNewer("v3.0.1", "3.0.0"))
        assertTrue(Versions.isNewer("3.0.0", "2.7.0"))
        assertFalse(Versions.isNewer("3.0.0", "3.0.0"))
        assertFalse(Versions.isNewer("2.9.9", "3.0.0"))
    }

    @Test
    fun unparsableVersionsAreNeverNewer() {
        assertNull(Versions.parse("3.1.0-beta"))
        assertNull(Versions.parse("3.1.0.1"))
        assertNull(Versions.parse(""))
        assertFalse(Versions.isNewer("3.1.0-beta", "3.0.0"))
        assertFalse(Versions.isNewer("3.1.0", "dev"))
        assertEquals(listOf(3, 1, 0), Versions.parse("v3.1"))
    }

    @Test
    fun parsesAGitHubReleaseForTheFirstSupportedAbi() {
        val release = Release.fromGitHub(payload(), listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("3.1.0", release.version)
        assertEquals("RawkoonTV-arm64-v8a.apk", release.apkName)
        assertTrue(release.apkUrl.endsWith("/v3.1.0/RawkoonTV-arm64-v8a.apk"))
        assertTrue(release.checksumUrl.endsWith("/v3.1.0/RawkoonTV-arm64-v8a.apk.sha256"))
        assertEquals(1234L, release.size)
    }

    @Test
    fun abiOrderDecidesTheAsset() {
        val names = ALL_ASSETS.toSet()
        assertEquals("RawkoonTV-armeabi-v7a.apk", Release.pickApk(names, listOf("armeabi-v7a")))
        assertEquals("RawkoonTV-x86_64.apk", Release.pickApk(names, listOf("x86_64", "x86")))
        // A 64-bit TV running a 32-bit-only release still gets the next ABI it supports
        assertEquals("RawkoonTV-armeabi-v7a.apk", Release.pickApk(names - "RawkoonTV-arm64-v8a.apk", listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test
    fun fallsBackToTheUniversalApk() {
        assertEquals("RawkoonTV.apk", Release.pickApk(ALL_ASSETS.toSet(), listOf("mips")))
        assertEquals("RawkoonTV.apk", Release.pickApk(ALL_ASSETS.toSet(), emptyList()))
        assertNull(Release.pickApk(setOf("notes.txt"), listOf("arm64-v8a")))
        val release = Release.fromGitHub(payload(), listOf("riscv64"))!!
        assertEquals("RawkoonTV.apk", release.apkName)
        assertTrue(release.checksumUrl.endsWith("RawkoonTV.apk.sha256"))
    }

    @Test
    fun skipsDraftsPrereleasesAndReleasesWithoutAChecksum() {
        val abis = listOf("arm64-v8a")
        assertNull(Release.fromGitHub(payload(draft = true), abis))
        assertNull(Release.fromGitHub(payload(prerelease = true), abis))
        assertNull(Release.fromGitHub(payload(names = ALL_ASSETS - "RawkoonTV-arm64-v8a.apk.sha256"), abis))
        assertNull(Release.fromGitHub(payload(names = listOf("RawkoonTV-arm64-v8a.apk.sha256")), abis))
        assertNull(Release.fromGitHub("""{"message":"Not Found"}""", abis))
        assertNull(Release.fromGitHub("not json", abis))
    }

    @Test
    fun refusesDownloadsOverPlainHttp() {
        val insecure = payload().replace("https://", "http://")
        assertNull(Release.fromGitHub(insecure, listOf("arm64-v8a")))
    }

    @Test
    fun aReleaseSurvivesTheCache() {
        val release = Release.fromGitHub(payload(), listOf("x86_64"))!!
        val cached = Release.parse(release.serialize())!!
        assertEquals(release.version, cached.version)
        assertEquals(release.apkName, cached.apkName)
        assertEquals(release.apkUrl, cached.apkUrl)
        assertEquals(release.checksumUrl, cached.checksumUrl)
        assertEquals(release.size, cached.size)
        assertNull(Release.parse("{}"))
    }

    @Test
    fun readsSha256sumOutput() {
        val name = "RawkoonTV-arm64-v8a.apk"
        assertEquals(hash, Checksum.expected("$hash  $name\n", name))
        assertEquals(hash, Checksum.expected("$hash *$name", name))
        assertEquals(hash, Checksum.expected("$hash  ./$name", name))
        assertEquals(hash, Checksum.expected(hash.uppercase() + "  $name", name))
        assertEquals(hash, Checksum.expected("$hash\n", name))
    }

    @Test
    fun rejectsChecksumFilesThatDoNotVouchForTheApk() {
        val name = "RawkoonTV-arm64-v8a.apk"
        assertNull(Checksum.expected("", name))
        assertNull(Checksum.expected("<html>rate limited</html>", name))
        assertNull(Checksum.expected("${hash.dropLast(1)}  $name", name))
        assertNull(Checksum.expected("$hash  RawkoonTV-x86_64.apk", name))
        val other = "a".repeat(64)
        assertEquals(hash, Checksum.expected("$other  RawkoonTV.apk\n$hash  $name\n", name))
    }

    @Test
    fun checksumIsComputedWhileCopying() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        val seen = mutableListOf<Long>()
        val (count, hash) = Checksum.copy(bytes.inputStream(), out) { seen += it }
        assertEquals(bytes.size.toLong(), count)
        assertEquals(bytes.size.toLong(), seen.last())
        assertTrue(bytes.contentEquals(out.toByteArray()))
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, hash)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Checksum.copy("abc".toByteArray().inputStream(), java.io.ByteArrayOutputStream()).second,
        )
    }

    private companion object {
        val ALL_ASSETS = listOf("RawkoonTV.apk", "RawkoonTV-arm64-v8a.apk", "RawkoonTV-armeabi-v7a.apk", "RawkoonTV-x86_64.apk")
            .flatMap { listOf(it, "$it.sha256") }
    }
}
