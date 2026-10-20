package cloud.rawkoon.tv.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Scales a focusable view up and fades its ring in; both are render properties, so focus never
 * redraws content. [raise] lifts the target above its siblings while focused: for views that
 * overlap their neighbours directly. Cards in a [FocusRow] leave it off, as the row orders its own drawing.
 */
class FocusMotion(
    private val target: View,
    private val ring: View?,
    private val scale: Float = Tokens.FOCUS_SCALE,
    private val raise: Boolean = true,
) {
    fun focus(focused: Boolean) {
        val duration = if (focused) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS
        val to = if (focused) scale else 1f
        target.animate().scaleX(to).scaleY(to).setDuration(duration).setInterpolator(Tokens.ease).start()
        ring?.animate()?.alpha(if (focused) 1f else 0f)?.setDuration(duration)?.setInterpolator(Tokens.ease)?.start()
        // The focused view draws above its neighbours (no outline, so no shadow)
        if (raise) target.translationZ = if (focused) 1f else 0f
    }

    fun press(down: Boolean) {
        val to = if (down) Tokens.PRESS_SCALE else scale
        target.animate().scaleX(to).scaleY(to).setDuration(if (down) Tokens.PRESS_MS else Tokens.FOCUS_IN_MS)
            .setInterpolator(Tokens.ease).start()
    }
}

fun isConfirmKey(keyCode: Int) =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * A focusable frame with the shared focus behaviour: it grows, its ring fades in, and OK presses
 * it down a little. [motionTarget] is what scales (the art, for cards with a caption under it).
 */
abstract class Focusable(context: Context) : FrameLayout(context) {
    protected abstract val motion: FocusMotion

    init {
        isFocusable = true
        isClickable = true
        noClip()
    }

    /**
     * Rings and backgrounds that match this view are sized to it here: FrameLayout only measures
     * a match-parent child again when it has more than one.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? FlatView ?: continue
            val params = child.layoutParams
            if (params.width != LayoutParams.MATCH_PARENT && params.height != LayoutParams.MATCH_PARENT) continue
            val w = if (params.width == LayoutParams.MATCH_PARENT) measuredWidth else child.measuredWidth
            val h = if (params.height == LayoutParams.MATCH_PARENT) measuredHeight else child.measuredHeight
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        }
    }

    protected fun listenForFocus(onFocused: () -> Unit = {}) {
        setOnFocusChangeListener { _, focused ->
            motion.focus(focused)
            if (focused) onFocused()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && event.repeatCount == 0) motion.press(true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && hasFocus()) motion.press(false)
        return super.onKeyUp(keyCode, event)
    }
}
