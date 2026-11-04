# The Jellyfin SDK ships its own rules; nothing in the app is reached by reflection.

# kotlin-logging (via the Jellyfin SDK) references SLF4J, which isn't on Android; it logs to logcat instead.
-dontwarn org.slf4j.**
