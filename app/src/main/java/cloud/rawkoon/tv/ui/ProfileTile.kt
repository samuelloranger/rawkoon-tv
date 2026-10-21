package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.ImageUrls

/** A round avatar with the name under it; with no profile, the "Add" tile. */
@SuppressLint("ViewConstructor")
class ProfileTile(context: Context, val profile: Profile?, images: Images, managing: Boolean) : LinearLayout(context) {
    private val avatar = FrameLayout(context)
    private val ring = FlatView(context)
    private val motion = FocusMotion(avatar, ring)

    @Volatile
    private var alive = true

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isClickable = true
        clipChildren = false
        contentDescription = profile?.name ?: context.getString(R.string.add_profile)
        val size = context.dp(SIZE)
        val gap = context.dp(6)
        avatar.clipChildren = false
        if (profile != null) {
            val image = ImageView(context).apply { background = oval(Tokens.surfaceHigh) }
            avatar.addView(label(context, 40f, Tokens.textSoft, Fonts.title(context)).apply {
                text = profile.name.take(1).uppercase()
                gravity = Gravity.CENTER
                background = oval(Tokens.surfaceHigh)
            }, fill())
            avatar.addView(image, fill())
            profile.avatarTag?.let { tag ->
                val url = ImageUrls(profile.jellyfinUrl).user(profile.jellyfinUserId, tag)
                images.load(url, size, size, size / 2f, isCurrent = { alive }) { image.setImageBitmap(it) }
            } ?: run { image.visibility = View.GONE }
            val badge = when {
                managing -> Icon.PEN
                profile.hasPin -> Icon.LOCK
                else -> null
            }
            if (badge != null) {
                val b = context.dp(29)
                avatar.addView(icon(context, badge, 11.5f, Tokens.textSoft).apply {
                    background = oval(Tokens.surfaceHigh).apply { setStroke(context.dp(2), Tokens.background) }
                }, FrameLayout.LayoutParams(b, b, Gravity.BOTTOM or Gravity.END))
            }
        } else {
            avatar.addView(icon(context, Icon.PLUS, 32f, Tokens.textMuted).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(context.dp(2), 0x40FFFFFF, context.dpf(6f), context.dpf(5f))
                }
            }, fill())
        }
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(context.dp(3), Tokens.ring)
        }
        ring.alpha = 0f
        avatar.addView(ring, FrameLayout.LayoutParams(size + gap * 2, size + gap * 2, Gravity.CENTER).apply { setMargins(-gap, -gap, -gap, -gap) })
        addView(avatar, LayoutParams(size, size).apply { bottomMargin = context.dp(14) })
        addView(label(context, 16f, Tokens.textSoft, Fonts.bold(context)).apply {
            text = profile?.name ?: context.getString(R.string.add_profile)
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setOnFocusChangeListener { _, focused -> motion.focus(focused) }
    }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && event.repeatCount == 0) motion.press(true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && hasFocus()) motion.press(false)
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val SIZE = 110
        const val W = 130
    }
}
