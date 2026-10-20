package cloud.rawkoon.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.rawkoon.tv.data.Images
import cloud.rawkoon.tv.jellyfin.Person

/** A cast member: a round photo (initials when there is none), the name and the role. */
@SuppressLint("ViewConstructor")
class PersonCard(context: Context, val person: Person, private val images: Images) : Focusable(context), RowItem {
    override val key: String get() = person.id
    private val photo = FrameLayout(context).noClip()
    private val ring = FlatView(context)
    override val motion = FocusMotion(photo, ring, 1.08f, raise = false)
    private val size = context.dp(SIZE)
    private val initials = label(context, 22f, Tokens.textSoft, Fonts.title(context))
    private val image = BitmapView(context)

    @Volatile
    private var alive = false

    init {
        contentDescription = person.name
        val gap = context.dp(4)
        layoutParams = LinearLayout.LayoutParams(context.dp(W), size + context.dp(48))
        photo.addView(initials.apply {
            text = person.name.take(1)
            gravity = Gravity.CENTER
            background = oval(Tokens.surfaceHigh)
        }, FrameLayout.LayoutParams(size, size))
        photo.addView(image, FrameLayout.LayoutParams(size, size))
        ring.background = ovalRing(context)
        ring.alpha = 0f
        photo.addView(ring, FrameLayout.LayoutParams(size + gap * 2, size + gap * 2).apply { setMargins(-gap, -gap, -gap, -gap) })
        addView(photo, LayoutParams(size, size, Gravity.CENTER_HORIZONTAL))
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(label(context, 11.5f, Tokens.textPrimary, Fonts.bold(context)).apply { this.text = person.name; gravity = Gravity.CENTER })
            person.role?.let { role -> addView(label(context, 10.5f, Tokens.textMuted, Fonts.medium(context)).apply { this.text = role; gravity = Gravity.CENTER }) }
        }
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        listenForFocus()
    }

    /** Requested on attach, like [Card]'s art. */
    private fun requestPhoto() {
        if (image.bitmap != null) return
        val url = person.imageUrl ?: return
        images.load("$url&fillWidth=$size", size, size, size / 2f, isCurrent = { alive }) {
            image.bitmap = it
            // Invisible, not gone: no layout, and nothing draws under the photo
            initials.visibility = View.INVISIBLE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        requestPhoto()
    }

    override fun onDetachedFromWindow() {
        alive = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SIZE = 66
        const val W = 92
    }
}
