package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    private val art = ImageView(context)
    private val frame = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    private val badge = WatchedBadge(context)
    private val title = label(context, 12.5f, Tokens.textPrimary, Fonts.bold(context))
    private val subtitle = label(context, 11f, Tokens.textMuted, Fonts.medium(context))
    override val motion = FocusMotion(frame, ring)

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

    init {
        val inset = context.dp(Tokens.RING_INSET)
        val w = context.dp(Tokens.POSTER_W)
        val h = context.dp(Tokens.POSTER_H)
        val radius = context.dpf(Tokens.CARD_RADIUS)
        layoutParams = RecyclerView.LayoutParams(context.dp(CELL_W), context.dp(CELL_H))
        art.background = rounded(Tokens.surfaceHigh, radius)
        val inner = { FrameLayout.LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) } }
        frame.addView(art, inner())
        badge.visibility = View.GONE
        frame.addView(badge, inner())
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        frame.addView(ring, FrameLayout.LayoutParams(w + inset * 2, h + inset * 2))
        addView(frame, LayoutParams(w + inset * 2, h + inset * 2))
        val caption = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        caption.addView(title)
        caption.addView(subtitle)
        addView(caption, LayoutParams(w, context.dp(CAPTION_H)).apply {
            leftMargin = inset
            topMargin = h + inset * 2
        })
        listenForFocus { onFocused(this) }
        setOnClickListener { item?.let(onOpen) }
    }

    /** Shows [item] at grid [position], or an empty poster when it is not loaded yet. */
    fun bind(item: MediaItem?, position: Int) {
        this.item = item
        this.position = position
        boundId = item?.id
        art.setImageDrawable(null)
        contentDescription = item?.title
        title.text = item?.title.orEmpty()
        subtitle.text = item?.year?.toString().orEmpty()
        badge.visibility = if (item?.played == true) View.VISIBLE else View.GONE
        if (!isFocused) {
            // A recycled view may still be shrinking back from its last title
            frame.animate().cancel()
            ring.animate().cancel()
            frame.scaleX = 1f
            frame.scaleY = 1f
            frame.translationZ = 0f
            ring.alpha = 0f
        }
        val url = item?.posterUrl ?: return
        val w = context.dp(Tokens.POSTER_W)
        images.load("$url&fillWidth=$w", w, context.dp(Tokens.POSTER_H), context.dpf(Tokens.CARD_RADIUS), opaque = false, isCurrent = { boundId == item.id }) {
            art.setImageBitmap(it)
        }
    }

    companion object {
        /** Art plus the horizontal gap between posters. */
        const val CELL_W = Tokens.POSTER_W + 22
        private const val CAPTION_H = 38
        private const val ROW_GAP = 10
        const val CELL_H = Tokens.POSTER_H + Tokens.RING_INSET * 2 + CAPTION_H + ROW_GAP
    }
}
