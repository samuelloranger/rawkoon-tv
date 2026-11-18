package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.rawkoon.ShelfCover
import cloud.rawkoon.tv.rawkoon.ShelfSummary
import cloud.rawkoon.tv.rawkoon.tmdbImage

/**
 * A shelf's cover card: up to four of its posters standing side by side (or its chosen backdrop),
 * with the shelf's name and how many titles it holds. In a row on Home they are drawn over the art,
 * so the row keeps its height; a [large] card on the Shelves page puts them under it. The art is
 * decoded at the card's exact size, as [Card] does.
 */
@SuppressLint("ViewConstructor")
class ShelfCoverCard(
    context: Context,
    val shelf: ShelfSummary,
    private val images: Images,
    private val onFocused: (ShelfCoverCard) -> Unit,
    private val onClick: (ShelfCoverCard) -> Unit,
    large: Boolean = false,
) : Focusable(context), RowItem {
    override val key: String get() = "shelf-${shelf.id}"

    private val artW = context.dp(if (large) Tokens.SHELF_PAGE_W else Tokens.SHELF_W)
    private val artH = context.dp(if (large) Tokens.SHELF_PAGE_H else Tokens.SHELF_H)
    private val radius = context.dpf(Tokens.CARD_RADIUS)
    private val frame = PosterFrame(context, artW, artH, radius)
    override val motion = FocusMotion(frame, frame.ring, raise = false)

    /** The mosaic's tiles and the pixel size each decodes at; empty for a backdrop cover. */
    private val tiles = ArrayList<Tile>()
    private var shown: String? = null

    @Volatile
    private var alive = false

    init {
        frame.art.reload = ::reloadArt
        contentDescription = shelf.name
        if (shelf.cover is ShelfCover.Mosaic) frame.addOverlay(mosaic(), frame.artParams())
        if (large) {
            layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH + context.dp(Tokens.CAPTION_GAP + Tokens.CAPTION_H))
            // Under the frame, not over the art: the art stays whole and the name gets room to read
            addView(texts(16f, 12f), FrameLayout.LayoutParams(artW, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = context.dp(Tokens.RING_INSET)
                topMargin = frame.outerH + context.dp(Tokens.CAPTION_GAP)
            })
        } else {
            layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH)
            frame.addOverlay(FlatView(context).apply { background = bottomScrim(radius) }, frame.artParams())
            frame.addOverlay(texts(15f, 11f), FrameLayout.LayoutParams(
                artW - context.dp(TEXT_PAD) * 2,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(context.dp(TEXT_PAD), 0, context.dp(TEXT_PAD), context.dp(TEXT_PAD)) })
        }
        addView(frame, frame.frameParams())
        listenForFocus { onFocused(this) }
        setOnClickListener { onClick(this) }
    }

    /** The name and the shelf's size, in a column. */
    private fun texts(nameSize: Float, countSize: Float) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(context, nameSize, Tokens.textPrimary, Fonts.title(context)).apply { text = shelf.name }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        addView(label(context, countSize, Tokens.textSoft, Fonts.medium(context)).apply {
            text = context.resources.getQuantityString(R.plurals.rk_shelf_titles, shelf.entryCount, shelf.entryCount)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(3)
        })
    }

    /**
     * Up to four posters standing side by side at full height, like titles on a shelf, each
     * trimmed only at its sides; clipped to the card's corners.
     */
    private fun mosaic(): View {
        val count = (shelf.cover as? ShelfCover.Mosaic)?.posters.orEmpty().take(4).size.coerceAtLeast(1)
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Tokens.background)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        val gap = context.dp(SPINE_GAP)
        val tileW = (artW - gap * (count - 1)) / count
        repeat(count) { i ->
            // The last strip takes the rounding left over, so the art reaches the card's right edge
            val w = if (i == count - 1) artW - (tileW + gap) * (count - 1) else tileW
            val tile = Tile(context, w, artH).also { tiles += it }
            // Released with the cover; the frame's art asks for the whole cover again (reloadArt)
            tile.view.reload = {}
            strip.addView(tile.view, LinearLayout.LayoutParams(w, artH).apply { if (i > 0) marginStart = gap })
        }
        return strip
    }

    /** One poster of the mosaic, with the size it is decoded at. */
    private class Tile(context: Context, val w: Int, val h: Int) {
        val view = BitmapView(context).apply { placeholder = rounded(Tokens.surfaceHigh, 0f) }
    }

    /** Requests the cover once the card is on a window: a page builds every card up front. */
    private fun requestArt() {
        val cover = shelf.cover
        val key = when (cover) {
            is ShelfCover.Mosaic -> cover.posters.take(4).joinToString("|")
            is ShelfCover.Backdrop -> cover.url.orEmpty()
        }
        if (key == shown) return
        shown = key
        when (cover) {
            is ShelfCover.Mosaic -> cover.posters.take(4).forEachIndexed { i, url ->
                val tile = tiles.getOrNull(i) ?: return@forEachIndexed
                images.load(art(url, tile.w), tile.w, tile.h, 0f, opaque = true, isCurrent = { alive && shown == key }) {
                    tile.view.bitmap = it
                }
            }
            is ShelfCover.Backdrop -> cover.url?.let { url ->
                images.load(art(url, artW), artW, artH, radius, opaque = true, isCurrent = { alive && shown == key }) {
                    frame.art.bitmap = it
                }
            }
        }
    }

    /** Asks for the cover again when [BitmapView.release] dropped it. */
    private fun reloadArt() {
        val missing = when (shelf.cover) {
            is ShelfCover.Mosaic -> tiles.any { it.view.bitmap == null }
            is ShelfCover.Backdrop -> frame.art.bitmap == null
        }
        if (!missing) return
        shown = null
        requestArt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        requestArt()
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TEXT_PAD = 9

        /** The dark seam between two standing posters. */
        const val SPINE_GAP = 2
    }
}

/**
 * A shelf's art URL as [Images] loads it: TMDB art needs the query its proxy expects, library art
 * is asked at the width it will be drawn (a library override may be either), and an uploaded
 * cover has no query to extend, so it starts one.
 */
private fun art(url: String, width: Int): String = when {
    url.contains("image.tmdb.org") -> tmdbImage(url) ?: url
    '?' in url -> "$url&fillWidth=$width"
    else -> "$url?fillWidth=$width"
}
