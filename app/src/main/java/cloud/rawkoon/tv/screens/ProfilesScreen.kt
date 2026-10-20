package cloud.rawkoon.tv.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import cloud.rawkoon.tv.MainActivity
import cloud.rawkoon.tv.R
import cloud.rawkoon.tv.Screen
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.data.Prefs
import cloud.rawkoon.tv.data.Profile
import cloud.rawkoon.tv.jellyfin.ImageUrls
import cloud.rawkoon.tv.ui.ChoiceDialog
import cloud.rawkoon.tv.ui.FlatView
import cloud.rawkoon.tv.ui.FocusMotion
import cloud.rawkoon.tv.ui.Fonts
import cloud.rawkoon.tv.ui.Icon
import cloud.rawkoon.tv.ui.PillButton
import cloud.rawkoon.tv.ui.Tokens
import cloud.rawkoon.tv.ui.dp
import cloud.rawkoon.tv.ui.dpf
import cloud.rawkoon.tv.ui.fill
import cloud.rawkoon.tv.ui.icon
import cloud.rawkoon.tv.ui.isConfirmKey
import cloud.rawkoon.tv.ui.label

/**
 * "Who's watching?", the first screen on every launch. One tile per saved profile, the one used
 * last already focused, then "Add". In manage mode, picking a profile offers to remove it or to
 * set or clear its PIN.
 */
class ProfilesScreen(activity: MainActivity) : Screen(activity) {
    override val view = FrameLayout(context)
    private val tiles = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
    }
    private val manage = PillButton(context, string(R.string.manage_profiles), Icon.PEN)
    private var managing = false
    private var dialog: ChoiceDialog? = null

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        column.addView(label(context, 36f, Tokens.textPrimary, Fonts.title(context)).apply { text = string(R.string.who_watching) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = context.dp(34) })
        column.addView(tiles)
        view.clipChildren = false
        view.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.addView(manage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = context.dp(30)
        })
        view.addView(clock(context), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = context.dp(22)
            marginEnd = context.dp(32)
        })
        manage.setOnClickListener { setManaging(!managing) }
    }

    override fun onShown(first: Boolean) {
        rebuild()
        // Nobody signed in yet: straight to the sign-in, with this screen below for Back
        if (first && app.profiles.all().isEmpty()) {
            addProfile()
            return
        }
        super.onShown(first)
    }

    override fun focusDefault() {
        val last = app.profiles.lastUsed()?.id
        (tileFor(last) ?: tiles.getChildAt(0))?.requestFocus()
    }

    private fun tileFor(id: String?): View? =
        (0 until tiles.childCount).map { tiles.getChildAt(it) }.firstOrNull { (it as? ProfileTile)?.profile?.id == id }

    /** Rebuilds the tiles, keeping focus on the same profile when it is still there. */
    private fun refresh() {
        val focusedId = (view.findFocus() as? ProfileTile)?.profile?.id
        rebuild()
        if (focusedId != null) tileFor(focusedId)?.requestFocus()
    }

    private fun rebuild() {
        tiles.removeAllViews()
        val gap = context.dp(40)
        for (profile in app.profiles.all()) {
            val tile = ProfileTile(context, profile, app.images, managing)
            tile.setOnClickListener { pick(profile) }
            tiles.addView(tile, LinearLayout.LayoutParams(context.dp(ProfileTile.W), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
        }
        val add = ProfileTile(context, null, app.images, managing)
        add.setOnClickListener { addProfile() }
        tiles.addView(add, LinearLayout.LayoutParams(context.dp(ProfileTile.W), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun setManaging(value: Boolean) {
        managing = value
        manage.label.text = string(if (value) R.string.done else R.string.manage_profiles)
        val focusedId = (view.findFocus() as? ProfileTile)?.profile?.id
        rebuild()
        if (focusedId != null) {
            tileFor(focusedId)?.requestFocus()
        } else {
            manage.requestFocus()
        }
    }

    private fun pick(profile: Profile) {
        if (managing) {
            options(profile)
            return
        }
        if (profile.hasPin) {
            activity.push(PinScreen(activity, PinScreen.Mode.Verify(profile)) { open(profile) })
        } else {
            open(profile)
        }
    }

    private fun open(profile: Profile) {
        app.profiles.markUsed(profile.id)
        activity.resetTo(ProfilesScreen(activity), HomeScreen(activity, app.profiles.get(profile.id) ?: profile))
    }

    private fun addProfile() {
        val url = Prefs.rawkoonUrl(context)
        activity.push(if (url == null) ServerScreen(activity) else SignInScreen(activity, url))
    }

    private fun options(profile: Profile) {
        val pin = if (profile.hasPin) {
            string(R.string.remove_pin) to { app.profiles.save(profile.withPin(null)); rebuild() }
        } else {
            string(R.string.set_pin) to {
                activity.push(PinScreen(activity, PinScreen.Mode.Create(profile)) { pin ->
                    // The screen pops before this runs, so it has already rebuilt with the old tile
                    app.profiles.save(profile.withPin(pin))
                    refresh()
                })
            }
        }
        dialog = ChoiceDialog(
            context,
            profile.name,
            listOf(
                pin,
                string(R.string.remove_profile) to {
                    app.profiles.remove(profile.id)
                    rebuild()
                    focusDefault()
                },
                string(R.string.cancel) to {},
            ),
        ).also { it.open(view) }
    }

    override fun onBack(): Boolean {
        dialog?.takeIf { it.isAttachedToWindow }?.let {
            it.close()
            return true
        }
        if (managing) {
            setManaging(false)
            return true
        }
        return false
    }
}

/** The clock in the top-right corner, in the TV's format. */
fun clock(context: Context) = TextClock(context).apply {
    textSize = 17f
    setTextColor(Tokens.textPrimary)
    typeface = Fonts.medium(context)
}

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
                val url = ImageUrls(profile.jellyfinUrl).user(profile.jellyfinUserId, tag) + "&fillWidth=$size"
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
