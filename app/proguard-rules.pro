# The Jellyfin SDK ships its own rules; nothing in the app is reached by reflection.

# libass's Matroska extractor reads these private fields of Media3's by name.
-keepclassmembers class androidx.media3.extractor.mkv.MatroskaExtractor {
    androidx.media3.extractor.ExtractorOutput extractorOutput;
    androidx.media3.common.util.ParsableByteArray subtitleSample;
}

# kotlin-logging's unused SLF4J backend is omitted; JellyfinSdk selects native Android logging.
-dontwarn org.slf4j.**
