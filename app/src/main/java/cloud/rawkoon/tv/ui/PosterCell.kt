package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.MediaItem

/**
 * A captioned poster for a grid. Unlike [Card] it is built once and rebound to other titles as the
 * grid scrolls, so thousands of titles cost a few dozen views. Bound to nothing it is an empty
 * poster, shown while its page is on the way. Focus is the same grow and ring, render properties only.
 */
@SuppressLint("ViewConstructor")
class PosterCell(context: Context, private val images: Images) : Focusable(context) {
    private val posterW = context.dp(Tokens.POSTER_W)
    private val posterH = context.dp(Tokens.POSTER_H)
    private val radius = context.dpf(Tokens.CARD_RADIUS)
    private val frame = PosterFrame(context, posterW, posterH, radius)
    private val art = frame.art
    private val ring = frame.ring
    private val badge = WatchedBadge(context)
    private val caption = CaptionColumn(context)
    override val motion = FocusMotion(frame, ring, raise = false)

    /** The title shown, or null while its page loads. */
    var item: MediaItem? = null
        private set
    var position = -1
        private set
    var onFocused: (PosterCell) -> Unit = {}
    var onOpen: (MediaItem) -> Unit = {}

    /** The id the art is being loaded for, so a bitmap arriving after a rebind is dropped. */
    @Volatile
    private var boundId: String? = null

    /** The title whose art is on its way, so rebinding it does not start a second load. */
    private var loadingId: String? = null

    init {
        val inset = context.dp(Tokens.RING_INSET)
        layoutParams = RecyclerView.LayoutParams(context.dp(CELL_W), context.dp(CELL_H))
        badge.visibility = View.GONE
        frame.addOverlay(badge)
        addView(frame, frame.frameParams())
        addView(caption, LayoutParams(posterW, context.dp(Tokens.CAPTION_H)).apply {
            leftMargin = inset
            topMargin = frame.outerH + context.dp(Tokens.CAPTION_GAP)
        })
        listenForFocus { onFocused(this) }
        setOnClickListener { item?.let(onOpen) }
    }

    /** Shows [item] at grid [position], or an empty poster when it is not loaded yet. */
    fun bind(item: MediaItem?, position: Int) {
        // Rebinding the same title (its page was refreshed) keeps its art: clearing it would flash the poster empty
        val keepArt = item != null && item.id == this.item?.id && art.bitmap != null
        this.item = item
        this.position = position
        boundId = item?.id
        if (boundId != loadingId) loadingId = null
        if (!keepArt) art.bitmap = null
        contentDescription = item?.title
        caption.title.text = item?.title.orEmpty()
        caption.subtitle.text = item?.year?.toString().orEmpty()
        badge.visibility = if (item?.played == true) View.VISIBLE else View.GONE
        if (!isFocused) {
            // A recycled view may still be shrinking back from its last title
            frame.animate().cancel()
            ring.animate().cancel()
            frame.scaleX = 1f
            frame.scaleY = 1f
            ring.alpha = 0f
        }
        val url = item?.posterUrl ?: return
        if (keepArt || loadingId == item.id) return
        loadingId = item.id
        images.load("$url&fillWidth=$posterW", posterW, posterH, radius, opaque = false, isCurrent = { boundId == item.id }) {
            loadingId = null
            art.bitmap = it
        }
    }

    companion object {
        /** Art, the ring's room on both sides, and the gap to the next poster. */
        const val CELL_W = Tokens.POSTER_W + Tokens.RING_INSET * 2 + Tokens.POSTER_GAP

        /** The vertical gap between the caption and the next row. */
        private const val ROW_GAP = 10
        const val CELL_H = Tokens.POSTER_H + Tokens.RING_INSET * 2 + Tokens.CAPTION_GAP + Tokens.CAPTION_H + ROW_GAP
    }
}
