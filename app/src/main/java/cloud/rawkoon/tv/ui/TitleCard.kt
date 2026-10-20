package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.Badge
import cloud.rawkoon.tv.jellyfin.MediaItem

/**
 * A poster for a TMDB title: the art with its status badge on it, and the title and its year (or
 * air date) under it. Same focus behaviour and exact-size art as [Card], which only knows library
 * items; the badge is a static label, drawn once.
 */
@SuppressLint("ViewConstructor")
class TitleCard(
    context: Context,
    val item: MediaItem,
    private val images: Images,
    private val onFocused: (TitleCard) -> Unit,
    private val onClick: (TitleCard) -> Unit,
) : Focusable(context), RowItem {
    override val key: String get() = item.id
    private val art = BitmapView(context)
    private val frame = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(frame, ring, raise = false)
    private val artW = context.dp(Tokens.POSTER_W)
    private val artH = context.dp(Tokens.POSTER_H)
    private val artRadius = context.dpf(Tokens.CARD_RADIUS)

    @Volatile
    private var alive = false

    init {
        contentDescription = item.title
        val inset = context.dp(Tokens.RING_INSET)
        val w = artW
        val h = artH
        val radius = artRadius
        val captionH = context.dp(CAPTION_DP)
        layoutParams = LinearLayout.LayoutParams(w + inset * 2, h + inset * 2 + captionH)
        art.placeholder = rounded(Tokens.surfaceHigh, radius)
        frame.addView(art, FrameLayout.LayoutParams(w, h).apply { setMargins(inset, inset, inset, inset) })
        item.tmdb?.badge?.let { badge ->
            frame.addView(badgeLabel(badge, w - context.dp(12)), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START,
            ).apply { setMargins(inset + context.dp(6), 0, 0, inset + context.dp(6)) })
        }
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        frame.addView(ring, FrameLayout.LayoutParams(w + inset * 2, h + inset * 2))
        addView(frame, LayoutParams(w + inset * 2, h + inset * 2))
        val caption = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        caption.addView(label(context, 12.5f, Tokens.textPrimary, Fonts.bold(context)).apply { text = item.title })
        (item.tmdb?.subtitle ?: item.year?.toString())?.let { sub ->
            caption.addView(label(context, 11f, Tokens.textMuted, Fonts.medium(context)).apply { text = sub })
        }
        addView(caption, LayoutParams(w, captionH, Gravity.BOTTOM).apply { leftMargin = inset })

        listenForFocus { onFocused(this) }
        setOnClickListener { onClick(this) }
    }

    private fun badgeLabel(badge: Badge, maxWidth: Int): View {
        val (background, text) = badgeColors(badge.tone)
        return label(context, 10f, text, Fonts.bold(context)).apply {
            this.text = badge.text
            this.maxWidth = maxWidth
            this.background = rounded(background, context.dpf(4f))
            setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
        }
    }

    /** Requested on attach, like [Card]'s art. */
    private fun requestArt() {
        if (art.bitmap != null) return
        val url = item.posterUrl ?: return
        images.load(url, artW, artH, artRadius, opaque = false, isCurrent = { alive }) { art.bitmap = it }
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
        const val CAPTION_DP = 38
    }
}

/** Background and text colour of a badge, by tone. */
fun badgeColors(tone: Badge.Tone): Pair<Int, Int> = when (tone) {
    Badge.Tone.NEUTRAL -> 0xCC000000.toInt() to Tokens.textPrimary
    Badge.Tone.WAITING -> 0xFFE0A93B.toInt() to Tokens.background
    Badge.Tone.ACTIVE -> 0xFF5BA8D8.toInt() to Tokens.background
    Badge.Tone.READY -> 0xFF52B788.toInt() to Tokens.background
    Badge.Tone.REFUSED -> 0xFFC8564D.toInt() to Tokens.textPrimary
}

/** The item a focused or clicked card shows, whichever card it is. */
fun View.shownItem(): MediaItem? = (this as? Card)?.item ?: (this as? TitleCard)?.item
