package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The art of a poster or landscape card: a placeholder-backed [art] view inset by the ring's room,
 * then whatever [addOverlay] puts over it, then the focus [ring] on top. It is the part that
 * scales on focus, so a card's caption stays put. [Card] and [PosterCell] share it, so the
 * poster design lives once.
 */
class PosterFrame(context: Context, val artW: Int, val artH: Int, radius: Float) : FrameLayout(context) {
    private val inset = context.dp(Tokens.RING_INSET)
    val art = BitmapView(context)
    val ring = FlatView(context)

    /** The size of the frame: the art and the ring's room around it. */
    val outerW = artW + inset * 2
    val outerH = artH + inset * 2

    init {
        noClip()
        art.placeholder = rounded(Tokens.surfaceHigh, radius)
        addView(art, artParams())
        ring.background = ringDrawable(context, radius)
        ring.alpha = 0f
        addView(ring, LayoutParams(outerW, outerH))
    }

    /** Where the art sits in the frame, and what covers it exactly. */
    fun artParams() = LayoutParams(artW, artH).apply { setMargins(inset, inset, inset, inset) }

    /** Puts [view] over the art and under the ring, covering the art unless [params] say otherwise. */
    fun addOverlay(view: View, params: LayoutParams = artParams()) {
        addView(view, childCount - 1, params)
    }

    /** The layout params that size the frame to the art and its ring's room. */
    fun frameParams() = LayoutParams(outerW, outerH)
}

/** The title and the subtitle under a poster's art; [subtitle] stays out of the column when built without one. */
class CaptionColumn(context: Context, titleText: String = "", subtitleText: String? = "") : LinearLayout(context) {
    val title = label(context, 12.5f, Tokens.textPrimary, Fonts.bold(context)).apply { text = titleText }
    val subtitle = label(context, 11f, Tokens.textMuted, Fonts.medium(context)).apply { text = subtitleText.orEmpty() }

    init {
        orientation = VERTICAL
        addView(title)
        if (subtitleText != null) addView(subtitle)
    }
}
