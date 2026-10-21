package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cloud.rawkoon.tv.data.Images

/** One rail entry: a small pill behind the icon when closed, a wide one when open. */
@SuppressLint("ViewConstructor")
class RailItem(
    context: Context,
    destination: Destination,
    title: String,
    subtitle: String?,
    private val avatarUrl: (() -> String?)?,
    private val images: Images,
    val tall: Boolean,
) : FrameLayout(context) {
    private val radius = context.dpf(9f)
    private val chip = FlatView(context)
    private val wide = FlatView(context)
    private val glyph: TextView? = if (avatarUrl == null && !tall) icon(context, destination.glyph, 15f, Tokens.textSoft) else null
    val labels: View
    private val avatarArt = BitmapView(context)
    private val avatarSize = context.dp(30)
    private var active = false
    private var open = false

    // Built once: paint() only picks between them
    private val idleWide = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Tokens.surfaceHigh)
    }
    private val focusedWide = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Tokens.surfaceFocused)
        setStroke(context.dp(2), Tokens.ring)
    }
    private var wideFocused: Boolean? = null
    private var wideAlpha = 0f
    private var chipAlpha = 0f

    @Volatile
    private var alive = false

    /** The URL the avatar shows, once loaded: a later call loads it again only when the picture changed. */
    private var avatarShown: String? = null

    init {
        isFocusable = true
        isClickable = true
        contentDescription = title
        val boxW = context.dp(40)
        addView(wide, fill())
        addView(chip, LayoutParams(boxW, ViewGroup.LayoutParams.MATCH_PARENT))
        wide.alpha = 0f
        chip.alpha = 0f
        chip.background = rounded(Tokens.surfaceHigh, radius)
        if (glyph != null) {
            addView(glyph, LayoutParams(boxW, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            avatarArt.placeholder = oval(Tokens.surfaceHigh)
            addView(avatarArt, LayoutParams(avatarSize, avatarSize, Gravity.CENTER_VERTICAL).apply { marginStart = (boxW - avatarSize) / 2 })
        }
        labels = FlatLinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(context, 14.5f, Tokens.textPrimary, Fonts.medium(context)).apply { text = title })
            if (subtitle != null) {
                addView(label(context, 10.5f, Tokens.textMuted, Fonts.medium(context)).apply { text = subtitle })
            }
        }
        addView(labels, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = boxW + context.dp(12) })
        setOnFocusChangeListener { _, _ -> paint() }
        paint()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        loadAvatar()
    }

    /**
     * Loads the avatar, unless the current one is already shown: called when attached and each time
     * the page comes back, so a picture that failed to load or changed since gets another try.
     */
    fun loadAvatar() {
        val url = avatarUrl?.invoke() ?: return
        if (url == avatarShown && avatarArt.bitmap != null) return
        images.load(url, avatarSize, avatarSize, avatarSize / 2f, isCurrent = { alive }) {
            avatarArt.bitmap = it
            avatarShown = url
        }
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    fun setActive(value: Boolean) {
        active = value
        paint()
    }

    fun setOpen(value: Boolean) {
        open = value
        paint()
    }

    /** The focused entry gets the ring, the current one a quiet pill and an apricot icon. */
    private fun paint() {
        val focused = isFocused
        if (wideFocused != focused) {
            wideFocused = focused
            wide.background = if (focused) focusedWide else idleWide
        }
        val showWide = if (open && (focused || active)) 1f else 0f
        val showChip = if (!open && active) 1f else 0f
        fade(wide, showWide, wideAlpha)
        fade(chip, showChip, chipAlpha)
        wideAlpha = showWide
        chipAlpha = showChip
        glyph?.setTextColor(if (active) Tokens.accent else if (focused) Tokens.textPrimary else Tokens.textSoft)
    }

    /** Animates [view] to [to] only when that is a change; before the first frame it just sets it. */
    private fun fade(view: View, to: Float, from: Float) {
        if (to == from) return
        if (isAttachedToWindow) view.animate().alpha(to).setDuration(Tokens.FOCUS_IN_MS).start() else view.alpha = to
    }
}
