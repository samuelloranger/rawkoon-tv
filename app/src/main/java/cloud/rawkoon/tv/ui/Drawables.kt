package cloud.rawkoon.tv.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** The focus ring: an apricot stroke, a small gap away from the art. */
fun ringDrawable(context: Context, radius: Float) = GradientDrawable().apply {
    cornerRadius = radius + context.dpf(Tokens.RING_INSET.toFloat())
    setStroke(context.dp(2), Tokens.ring)
}

/** An oval ring stroke, the round counterpart of [ringDrawable]. */
fun ovalRing(context: Context) = oval(0).apply { setStroke(context.dp(2), Tokens.ring) }


fun oval(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}
