# RawkoonTV

An Android TV client for [Rawkoon](https://github.com/samuelloranger/rawkoon) and Jellyfin, built
for low-end TVs: plain Android Views, no Compose, so focus moves and backdrop changes hold 60 fps.

- **Profiles.** "Who's watching?" on every launch, or straight to the only profile when it has no
  PIN and the setting is on. Each profile is a Rawkoon account signed in with a device code, and
  the Jellyfin account Rawkoon links to it, signed in through Quick Connect. Optional PIN per
  profile. Updating from 2.x keeps its Jellyfin users as profiles; those without the old Rawkoon
  sign-in link their account when Discover first needs it.
- **Home.** Continue watching, next up, the latest of each library, and from Rawkoon the upcoming
  episodes and movies and your requests. Each profile can reorder or hide the rows.
- **Library.** Movies, Shows and Collections as poster grids with sorting, filters and a letter
  index; movie and show pages, seasons and episodes, in the layout of
  [Wholphin](https://github.com/damontecres/Wholphin). Search and Favorites.
- **Discover and requests.** Rawkoon's trending, popular and upcoming titles; open one and request
  it, then follow its state. A title already in the library opens there.
- **Player.** media3 with the Jellyfin ffmpeg decoders, the server's own choice of direct play or
  transcode, trickplay thumbnails, and progress reported back to Jellyfin. Intros, recaps, previews
  and credits can show a skip button, skip by themselves or be left alone; the next episode plays
  after a countdown, with an optional "Are you still watching?".
- **Settings.** Skip amounts, subtitle style, audio passthrough and downmix, HDR format overrides,
  max bitrate and resolution, matching the TV's refresh rate to the video, the clock, and clearing
  the image cache.
- **Launcher.** Watch Next and a Recently added channel on the TV's home screen.
- **Updates.** The app checks the releases once a day and installs a new one after Android's
  confirmation.
- **Languages.** French and English, following the TV, with a setting to force either.

Needs Android 8 or later (Android TV or Fire TV) and a Rawkoon server with its Jellyfin integration
set up; see the [TV app guide](https://samlo.cloud/rawkoon/tv-app).

## Building

```sh
./gradlew assemblePreview   # release code, debug key, installs next to the real app as cloud.rawkoon.tv.next
./gradlew assembleRelease   # signed with -Prawkoontv.signingDir=<dir with release.jks and signing.env>
```

The version comes from the latest `vX.Y.Z` tag.

## Releasing

Tag the commit on `main` and push the tag: `git tag -a v3.1.0 -m "RawkoonTV v3.1.0" && git push origin v3.1.0`.
Only strict `vX.Y.Z` tags release: both updaters fail to parse anything else. The release workflow
is the only publisher: it tests, builds the signed APKs from the `SIGNING_KEY`, `KEY_ALIAS`,
`KEY_PASSWORD` and `KEY_STORE_PASSWORD` secrets, checks they carry the first RawkoonTV's
certificate, and publishes `RawkoonTV-<abi>.apk`, `RawkoonTV.apk` and a `.sha256` for each.
`RAWKOONTV_SIGNING_DIR=<dir> scripts/release.sh v3.1.0` builds and verifies locally, then pushes
the same annotated tag (`--dry-run` stops before that). The release title is the bare tag and
the signing key must be the first RawkoonTV's: installed copies of both generations update from
these releases.

The app checks the latest release once a day and offers it in Settings, verifying the APK against
its `.sha256` before handing it to Android's installer.

## Credits

The device profile and codec checks in `app/src/main/java/cloud/rawkoon/tv/playback/profile/`
are adapted from [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) (GPL-2.0),
and the ExoPlayer setup follows its video player. The base app structure (focus rows, the image
pipeline) comes from [Couch](https://github.com/samuelloranger/couch). Fonts: Fraunces and Hanken
Grotesk (SIL OFL), icons from Font Awesome Free (SIL OFL).

## License

GPL-2.0, see [LICENSE](LICENSE).
