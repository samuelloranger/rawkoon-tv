<p align="center"><img src=".github/rawkoontv-icon.png" width="128" alt="RawkoonTV icon"></p>
<h1 align="center">RawkoonTV</h1>
<p align="center">The Android TV app for <a href="https://github.com/samuelloranger/rawkoon">Rawkoon</a>: sign in once, then browse, request and watch.</p>
<p align="center">
  <a href="https://github.com/samuelloranger/rawkoon-tv/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/samuelloranger/rawkoon-tv"></a>
  <img alt="Platform: Android TV and Fire TV" src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-C45A2F">
  <img alt="License: GPL-2.0" src="https://img.shields.io/badge/license-GPL--2.0-2ea44f">
</p>

RawkoonTV is an Android TV client for a self-hosted [Rawkoon](https://github.com/samuelloranger/rawkoon)
server. Rawkoon manages your movie and TV library and its downloads; Jellyfin plays it. RawkoonTV
puts both on the TV: you sign in to Rawkoon once, and it sets up everything else.

RawkoonTV is a fork of [Wholphin](https://github.com/damontecres/Wholphin), an open-source Jellyfin
client for Android TV. Library browsing and playback are Wholphin's; RawkoonTV adds the Rawkoon
sign-in, discovery and requests. It tracks Wholphin closely and merges its releases.

## What it does

- **One sign-in.** Enter your Rawkoon address, then approve the code shown on the TV from your
  phone (or sign in with email and password). Rawkoon hands the TV its Jellyfin server and signs it
  in to Jellyfin through Quick Connect. No Jellyfin password, no server address to type twice.
- **Discover and request.** Browse trending, popular and upcoming titles from Rawkoon, open any
  movie or show, and request it in one click. The title page shows whether it is pending, approved,
  downloading or ready.
- **Home rows.** *Coming soon* lists the next episodes and movies due in your library, with their
  episode numbers; *My requests* lists your requests with their status.
- **Owned titles open in the library.** A title you already have shows **Open** and goes straight to
  its Jellyfin page instead of the request page.
- **Rawkoon look.** The Rawkoon theme and fonts are the default, and **Interface size** in the
  Rawkoon settings scales the whole interface (90% by default).
- **Everything Wholphin does** for playing your library: a customizable home page, ExoPlayer and
  MPV playback, subtitles, trickplay previews, live TV, music, and automatic refresh rate switching.
  See [Wholphin's feature list](https://github.com/damontecres/Wholphin#features).

## Requirements

- An Android TV or Fire TV device with Android 6 or later.
- A Rawkoon server v1.43.0 or later, with its Jellyfin integration set up.
- A Jellyfin server (`10.10` or later) with **Quick Connect** enabled
  (Dashboard › General › Quick Connect).
- Your Rawkoon account linked to your Jellyfin user. A Rawkoon admin does this in
  **Settings › Users**, in the Jellyfin column.

## Installation

1. Allow your TV to install apps from unknown sources
   ([Android TV](https://www.xda-developers.com/how-to-sideload-apps-android-tv/),
   [Fire TV](https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/)).
2. Download the APK from the [latest release](https://github.com/samuelloranger/rawkoon-tv/releases/latest)
   and install it, for example with a browser app such as Downloader, `Send files to TV`, or
   `adb install`.
3. Open RawkoonTV, enter your Rawkoon address, and approve the code on your phone.

Release APKs are signed with the certificate SHA-256
`A2:71:9E:DC:5C:9D:1A:75:51:B0:A4:1D:02:7D:40:55:1E:14:DA:F1:2F:35:9F:F5:FA:1A:E3:8A:A9:83:AD:8B`
(check with `apksigner verify --print-certs RawkoonTV.apk`).

### Updates

RawkoonTV checks this repository for new releases and offers to install them. The first update
asks Android for permission to install apps; after that, an update is one click and keeps your
settings and sign-in.

Full documentation, including what each sign-in error means:
[Rawkoon docs › TV app](https://samlo.cloud/rawkoon/tv-app).

## Building

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

JDK 21 and the Android SDK. MPV and the extra ExoPlayer decoders come from
[wholphin-extensions](https://github.com/damontecres/wholphin-extensions); without them the app
still builds, with those features off. See [DEVELOPMENT.md](DEVELOPMENT.md).

Rawkoon-specific code lives in its own package so Wholphin releases merge cleanly. The Rawkoon API
it uses is described in [RAWKOON.md](RAWKOON.md).

Releases are built and published with `scripts/release.sh vX.Y.Z`, which signs the APKs, checks
that MPV is included, and uploads them under the names the in-app updater looks for. RawkoonTV
versions start at 2.0.0 so they always sort above the Wholphin releases this fork is based on.

## Credits

- [Wholphin](https://github.com/damontecres/Wholphin) by damontecres and its contributors. Most of
  this app is theirs. Please report bugs that also happen in Wholphin to
  [Wholphin](https://github.com/damontecres/Wholphin/issues), and RawkoonTV-specific ones
  [here](https://github.com/samuelloranger/rawkoon-tv/issues).
- [Jellyfin](https://jellyfin.org) and the official
  [Jellyfin Android TV client](https://github.com/jellyfin/jellyfin-androidtv).
- [TMDB](https://www.themoviedb.org) for titles, images and metadata, through Rawkoon. This product
  uses the TMDB API but is not endorsed or certified by TMDB.

## License

GPL-2.0, see [LICENSE](LICENSE). RawkoonTV is modified from Wholphin (GPL-2.0).

RawkoonTV is an independent project, not affiliated with or endorsed by the Wholphin or Jellyfin
projects. Jellyfin, Android TV and Fire TV are trademarks of their respective owners.
