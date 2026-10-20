# RawkoonTV

An Android TV client for [Rawkoon](https://github.com/samuelloranger/rawkoon) and Jellyfin, built
for low-end TVs: plain Android Views, no Compose, so focus moves and backdrop changes hold 60 fps.

- **Profiles.** "Who's watching?" on every launch. Each profile is a Rawkoon account signed in
  with a device code, and the Jellyfin account Rawkoon links to it, signed in through Quick
  Connect. Optional PIN per profile.
- **Library.** Home rows (continue watching, next up, the latest of each library), movie and show
  pages, seasons and episodes, in the layout of [Wholphin](https://github.com/damontecres/Wholphin).
- **Player.** media3 with the Jellyfin ffmpeg decoders, the server's own choice of direct play or
  transcode, intro and credits skipping from media segments, trickplay thumbnails, and progress
  reported back to Jellyfin.
- **Languages.** French and English, following the TV, with a setting to force either.

## Building

```sh
./gradlew assemblePreview   # release code, debug key, installs next to the real app as cloud.rawkoon.tv.next
./gradlew assembleRelease   # signed with -Prawkoontv.signingDir=<dir with release.jks and signing.env>
```

The version comes from the latest `vX.Y.Z` tag.

## Credits

The device profile and codec checks in `app/src/main/java/cloud/rawkoon/tv/playback/profile/`
are adapted from [jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) (GPL-2.0),
and the ExoPlayer setup follows its video player. The base app structure (focus rows, the image
pipeline) comes from [Couch](https://github.com/samuelloranger/couch). Fonts: Fraunces and Hanken
Grotesk (SIL OFL), icons from Font Awesome Free (SIL OFL).

## License

GPL-2.0, see [LICENSE](LICENSE).
