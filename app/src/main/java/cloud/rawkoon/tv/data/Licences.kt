package cloud.rawkoon.tv.data

/** One thing the app uses or adapts, and the licence it comes under; [licence] is null when it is not known. */
data class Credit(val name: String, val licence: String?)

/**
 * What the licences screen lists: the app itself, code adapted from other projects, fonts and
 * icons, and the libraries in `app/build.gradle.kts`. The library licences are those declared in
 * their published POMs; the rest are as stated in the README.
 */
object Licences {
    const val APP = "GPL-2.0"

    val credits = listOf(
        Credit("jellyfin-androidtv", "GPL-2.0"),
        Credit("Couch", null),
        Credit("Fraunces", "SIL OFL 1.1"),
        Credit("Hanken Grotesk", "SIL OFL 1.1"),
        // The font is under the OFL, the icon designs in it under CC BY
        Credit("Font Awesome Free", "SIL OFL 1.1, CC BY 4.0"),
        Credit("AndroidX media3", "Apache-2.0"),
        Credit("AndroidX", "Apache-2.0"),
        Credit("Jellyfin SDK for Kotlin", "LGPL-3.0"),
        Credit("Jellyfin media3 FFmpeg decoder", "GPL-3.0"),
        Credit("ZXing", "Apache-2.0"),
        Credit("kotlinx.coroutines", "Apache-2.0"),
    )
}
