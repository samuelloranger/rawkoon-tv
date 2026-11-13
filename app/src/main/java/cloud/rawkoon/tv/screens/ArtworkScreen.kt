package cloud.rawkoon.tv.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.JellyfinSession
import cloud.rawkoon.tv.jellyfin.MediaItem
import cloud.rawkoon.tv.jellyfin.RemoteArt
import cloud.rawkoon.tv.rawkoon.RawkoonDiscover
import cloud.rawkoon.tv.rawkoon.rawkoonDiscover
import cloud.rawkoon.tv.ui.CaptionColumn
import cloud.rawkoon.tv.ui.FocusMotion
import cloud.rawkoon.tv.ui.Focusable
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.PosterFrame
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.label
import cloud.rawkoon.tv.ui.noClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.ImageType

/**
 * Picks [item]'s poster or background. A movie or show in Rawkoon's library gets Rawkoon's choices
 * and is saved through Rawkoon, which sets it in Jellyfin too, so Rawkoon's apps show it as well;
 * anything else gets what Jellyfin's image providers offer. The current
 * image comes first; choosing another has the server download it and keep it, then [onPicked]
 * gets the item with its new image URLs and the page goes back. Back waits while it saves, for
 * [SAVE_TIMEOUT_MS] at most.
 */
class ArtworkScreen(
    activity: MainActivity,
    private val session: JellyfinSession,
    private val item: MediaItem,
    private val type: ImageType,
    private val onPicked: (MediaItem) -> Unit,
) : Screen(activity) {
    override val view = FrameLayout(context)
    private val poster = type == ImageType.PRIMARY
    private val status = label(context, 15f, Tokens.textMuted, Fonts.medium(context))
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }.noClip()
    private val tiles = mutableListOf<ArtTile>()
    private var saving = false
    private val kind = if (poster) "poster" else "backdrop"

    /** Rawkoon and the title's id there, when the choices came from Rawkoon: a pick is then saved through it. */
    private var rawkoon: Pair<RawkoonDiscover, Int>? = null

    init {
        view.setBackgroundColor(Tokens.background)
        val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        header.addView(label(context, 28f, Tokens.textPrimary, Fonts.title(context)).apply {
            text = string(if (poster) R.string.art_title_poster else R.string.art_title_background)
        })
        header.addView(label(context, 15f, Tokens.textSoft, Fonts.medium(context)).apply {
            text = listOfNotNull(item.seriesName, item.title).distinct().joinToString(" · ")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
        header.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(14) })
        view.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(SIDE)
            topMargin = context.dp(36)
        })
        val room = context.dp(Tokens.RING_INSET * 3)
        val scroll = ScrollView(context).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(context.dp(SIDE) - room, room, 0, context.dp(40))
        }
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = context.dp(GRID_TOP) })
        load()
    }

    private fun load() {
        status.text = string(R.string.art_loading)
        val current = if (poster) item.posterUrl else item.backdropUrl
        launchLoading(onError = { status.text = string(R.string.art_error) }) {
            rawkoon = findInRawkoon()
            // Rawkoon's choices when it has the title; Jellyfin's when it doesn't or offers none
            val offered = rawkoon?.let { (api, id) -> runCatching { api.artwork(id, kind) }.getOrNull()?.takeIf { it.isNotEmpty() } }
                ?: session.remoteArt(item.id, type).also { rawkoon = null }
            status.text = if (offered.isEmpty()) string(R.string.art_none) else string(R.string.art_hint)
            val all = listOfNotNull(current?.let { null to it }) + offered.map { it to it.previewUrl(if (poster) "w342" else "w780") }
            all.chunked(if (poster) POSTERS_PER_ROW else BACKDROPS_PER_ROW).forEach { chunk ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }.noClip()
                chunk.forEach { (art, preview) ->
                    // The current image has no RemoteArt: it is what the server shows now, sized like a card
                    val src = if (art == null) "$preview&fillWidth=${context.dp(if (poster) Tokens.POSTER_W else Tokens.LAND_W)}" else preview
                    val caption = if (art == null) string(R.string.art_current) to null else (art.provider ?: "") to art.details()
                    val tile = ArtTile(context, app.images, src, poster, caption) { alive }
                    tile.setOnClickListener { if (art == null) activity.pop() else pick(art) }
                    tiles += tile
                    // The tile's own size, which leaves room for its caption under the art
                    row.addView(tile, (tile.layoutParams as LinearLayout.LayoutParams).apply { marginEnd = context.dp(GAP) })
                }
                grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(GAP) })
            }
            // The first offer, not the current image: it is what someone opening this page came to change
            (tiles.getOrNull(if (current != null) 1 else 0) ?: tiles.firstOrNull())?.requestFocus()
        }
    }

    private fun pick(art: RemoteArt) {
        if (saving) return
        saving = true
        status.text = string(R.string.art_saving)
        scope.launch {
            val updated = try {
                // The server fetches the image from the provider: a stalled provider must not hold the page
                withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                    save(art)
                    session.item(item.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (updated == null) {
                saving = false
                status.text = string(R.string.art_hint)
                Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            onPicked(updated)
            activity.pop()
        }
    }

    /** The title in Rawkoon's library, when the profile is linked and Rawkoon has it; null otherwise. */
    private suspend fun findInRawkoon(): Pair<RawkoonDiscover, Int>? {
        if (item.kind != MediaItem.Kind.MOVIE && item.kind != MediaItem.Kind.SERIES) return null
        val tmdbId = item.libraryTmdbId ?: return null
        val api = (app.profiles.get(session.profile.id) ?: session.profile).rawkoonDiscover(context) ?: return null
        val id = try {
            api.status(item.kind == MediaItem.Kind.MOVIE, tmdbId).library?.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return id?.let { api to it }
    }

    /** Through Rawkoon, which sets Jellyfin too, when the choices came from it; Jellyfin directly otherwise, or when Rawkoon couldn't. */
    private suspend fun save(art: RemoteArt) {
        val (api, id) = rawkoon ?: return session.setArt(item.id, type, art.url)
        val synced = try {
            api.setArtwork(id, kind, art.url) == "synced"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!synced) session.setArt(item.id, type, art.url)
    }

    override fun focusDefault() {
        tiles.firstOrNull()?.requestFocus()
    }

    /** Leaving while the server saves would drop the card's update: Back waits for it. */
    override fun onBack(): Boolean = saving

    /** One image to choose: the art in a card's frame, its provider and size under it. */
    @SuppressLint("ViewConstructor")
    private class ArtTile(context: Context, images: Images, url: String, poster: Boolean, caption: Pair<String, String?>, isCurrent: () -> Boolean) : Focusable(context) {
        private val artW = context.dp(if (poster) Tokens.POSTER_W else Tokens.LAND_W)
        private val artH = context.dp(if (poster) Tokens.POSTER_H else Tokens.LAND_H)
        private val frame = PosterFrame(context, artW, artH, context.dpf(Tokens.CARD_RADIUS))
        override val motion = FocusMotion(frame, frame.ring, raise = false)

        init {
            contentDescription = listOfNotNull(caption.first, caption.second).joinToString(", ")
            val captionH = context.dp(Tokens.CAPTION_H)
            layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH + context.dp(Tokens.CAPTION_GAP) + captionH)
            addView(frame, frame.frameParams())
            addView(CaptionColumn(context, caption.first, caption.second), LayoutParams(artW, captionH, Gravity.BOTTOM).apply { leftMargin = context.dp(Tokens.RING_INSET) })
            listenForFocus()
            images.load(url, artW, artH, context.dpf(Tokens.CARD_RADIUS), isCurrent = isCurrent) { frame.art.bitmap = it }
        }
    }

    private companion object {
        const val SIDE = 64
        const val GRID_TOP = 150
        const val GAP = 10
        const val POSTERS_PER_ROW = 7
        const val BACKDROPS_PER_ROW = 4
        const val SAVE_TIMEOUT_MS = 30_000L
    }
}
