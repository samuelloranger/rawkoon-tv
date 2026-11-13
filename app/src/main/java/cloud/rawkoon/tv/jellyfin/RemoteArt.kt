package cloud.rawkoon.tv.jellyfin

/** One image a provider offers for an item: what the artwork picker lists. */
data class RemoteArt(
    val url: String,
    val thumbnailUrl: String?,
    val provider: String?,
    val width: Int?,
    val height: Int?,
    val language: String?,
) {
    /**
     * A small copy to show in the picker: the provider's thumbnail, or for TMDB a sized copy of the
     * original, which is several MB. [tmdbSize] is a TMDB size such as "w342".
     */
    fun previewUrl(tmdbSize: String): String = thumbnailUrl ?: url.replace("/t/p/original/", "/t/p/$tmdbSize/")

    /** "2000 × 3000 · EN": what tells two offers from one provider apart. */
    fun details(): String = listOfNotNull(
        if (width != null && height != null) "$width × $height" else null,
        language?.uppercase(),
    ).joinToString(" · ")
}
