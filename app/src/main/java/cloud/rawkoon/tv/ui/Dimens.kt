package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

/** Layout params filling the parent, for the stacked layers of a [FrameLayout]. */
fun fill() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)


/**
 * Lets children draw past this view's bounds and padding. Focused things grow by a few percent
 * and draw their ring near their edge; every container that holds them calls this, so no ring
 * is ever cut.
 */
fun <T : ViewGroup> T.noClip(): T = apply {
    clipChildren = false
    clipToPadding = false
}
