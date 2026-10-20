package cloud.rawkoon.tv.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import cloud.rawkoon.tv.data.Images

/**
 * A title's logo, or its name in Fraunces when it has none. Long names shrink and wrap instead of
 * being cut. The logo is requested at [logoW] x [logoH] px and drawn at the bottom-left of
 * whatever box the caller gives this view, so a new logo never changes a layout.
 */
class TitleLogo(
    context: Context,
    private val images: Images,
    private val logoW: Int,
    private val logoH: Int,
    titleSp: Float,
    minSp: Int,
    private val isCurrent: () -> Boolean,
) : FrameLayout(context) {
    private val logo = LogoView(context)
    private val title = label(context, titleSp, Tokens.textPrimary, Fonts.title(context), lines = 2).apply {
        gravity = Gravity.BOTTOM
        setAutoSizeTextTypeUniformWithConfiguration(minSp, titleSp.toInt(), 2, TypedValue.COMPLEX_UNIT_SP)
    }

    init {
        addView(logo, fill())
        addView(title, fill())
    }

    /** Shows [logoUrl] once it has loaded; with none, [name] stands in for it. */
    fun bind(logoUrl: String?, name: String) {
        if (logoUrl == null) {
            title.text = name
            return
        }
        // The name stays empty while the logo loads, so the two never show together
        title.text = ""
        images.load("$logoUrl&maxWidth=$logoW", logoW, logoH, 0f, contain = true, isCurrent = isCurrent) { logo.bitmap = it }
    }
}
