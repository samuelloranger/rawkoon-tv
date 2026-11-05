# The Jellyfin SDK ships its own rules; nothing in the app is reached by reflection.

# kotlin-logging's unused SLF4J backend is omitted; JellyfinSdk selects native Android logging.
-dontwarn org.slf4j.**
