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
 * A shelf's cover card: its 2×2 poster mosaic (or its chosen backdrop) with the shelf's name and
 * how many titles it holds drawn over the art, so the row needs no caption under it and the grid
 * below keeps its height. The art is decoded at the card's exact size, as [Card] does.
 */
@SuppressLint("ViewConstructor")
class ShelfCoverCard(
    context: Context,
    val shelf: ShelfSummary,
    private val images: Images,
    private val onFocused: (ShelfCoverCard) -> Unit,
    private val onClick: (ShelfCoverCard) -> Unit,
) : Focusable(context), RowItem {
    override val key: String get() = "shelf-${shelf.id}"

    private val artW = context.dp(Tokens.SHELF_W)
    private val artH = context.dp(Tokens.SHELF_H)
    private val radius = context.dpf(Tokens.CARD_RADIUS)
    private val frame = PosterFrame(context, artW, artH, radius)
    override val motion = FocusMotion(frame, frame.ring, raise = false)

    /** The mosaic's tiles and the pixel size each decodes at; empty for a backdrop cover. */
    private val tiles = ArrayList<Tile>()
    private var shown: String? = null

    @Volatile
    private var alive = false

    init {
        contentDescription = shelf.name
        layoutParams = LinearLayout.LayoutParams(frame.outerW, frame.outerH)
        if (shelf.cover is ShelfCover.Mosaic) frame.addOverlay(mosaic(), frame.artParams())
        frame.addOverlay(FlatView(context).apply { background = bottomScrim(radius) }, frame.artParams())
        frame.addOverlay(texts(), FrameLayout.LayoutParams(
            artW - context.dp(TEXT_PAD) * 2,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START,
        ).apply { setMargins(context.dp(TEXT_PAD), 0, context.dp(TEXT_PAD), context.dp(TEXT_PAD)) })
        addView(frame, frame.frameParams())
        listenForFocus { onFocused(this) }
        setOnClickListener { onClick(this) }
    }

    /** The name and the shelf's size, in a column over the bottom of the art. */
    private fun texts() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(context, 15f, Tokens.textPrimary, Fonts.title(context)).apply { text = shelf.name }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        addView(label(context, 11f, Tokens.textSoft, Fonts.medium(context)).apply {
            text = context.resources.getQuantityString(R.plurals.rk_shelf_titles, shelf.entryCount, shelf.entryCount)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(2)
        })
    }

    /** Four posters in a 2×2, or the first one alone when the shelf has fewer; clipped to the card's corners. */
    private fun mosaic(): View {
        val posters = (shelf.cover as? ShelfCover.Mosaic)?.posters.orEmpty().take(4)
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        if (posters.size < 4) {
            val only = Tile(context, artW, artH).also { tiles += it }
            grid.addView(only.view, LinearLayout.LayoutParams(artW, artH))
            return grid
        }
        val halfW = artW / 2
        val halfH = artH / 2
        repeat(2) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(2) {
                val tile = Tile(context, halfW, halfH).also { tiles += it }
                row.addView(tile.view, LinearLayout.LayoutParams(halfW, halfH))
            }
            grid.addView(row, LinearLayout.LayoutParams(artW, halfH))
        }
        return grid
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
    }
}

/**
 * A shelf's art URL as [Images] loads it: TMDB art needs the query its proxy expects, and library
 * art is asked at the width it will be drawn (a library override may be either).
 */
private fun art(url: String, width: Int): String =
    if (url.contains("image.tmdb.org")) tmdbImage(url) ?: url else "$url&fillWidth=$width"
