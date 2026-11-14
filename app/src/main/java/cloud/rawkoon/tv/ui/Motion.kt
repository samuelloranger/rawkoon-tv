package cloud.rawkoon.tv.ui

import android.view.View
import android.view.animation.Interpolator

/*
 * The small entrances views make on their own. Each moves only render properties (alpha, scale,
 * translation) through a layer, so the view is drawn once and then only moved and blended.
 */

/**
 * Fades the view in while it rises [rise] dp into place, after [delay]. The delay is cleared from
 * the view's animator once started: it would hold back the view's next animation otherwise.
 */
fun View.riseIn(delay: Long = 0, rise: Float = Tokens.ARRIVE_RISE, duration: Long = Tokens.ARRIVE_MS) {
    alpha = 0f
    translationY = context.dpf(rise)
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration).setInterpolator(Tokens.ease).withLayer().start()
    animate().startDelay = 0
}

/** Makes a hidden view visible, rising into place; one already visible (its label changing) stays still. */
fun View.showRising() {
    if (visibility == View.VISIBLE) return
    visibility = View.VISIBLE
    riseIn(rise = Tokens.RISE, duration = Tokens.RISE_MS)
}

/** Fades a hidden view in as it becomes visible; one already visible stays as it is. */
fun View.showFading() {
    if (visibility == View.VISIBLE) return
    visibility = View.VISIBLE
    alpha = 0f
    animate().alpha(1f).setDuration(Tokens.SCREEN_FADE_MS).setInterpolator(Tokens.ease).withLayer().start()
}

/** Slides the view in from the side of [direction] (1 from the right, -1 from the left) while it fades in. */
fun View.slideIn(direction: Int) {
    alpha = 0f
    translationX = context.dpf(Tokens.SCREEN_SHIFT) * direction
    animate().alpha(1f).translationX(0f).setDuration(Tokens.SCREEN_SLIDE_MS).setInterpolator(Tokens.ease).withLayer().start()
}

/**
 * Nudges the view a few dp toward [direction] (a View.FOCUS_* direction) and back: a key that had
 * nowhere to go was heard. Nothing while the key repeats, so holding into a wall stays still.
 */
fun View.nudge(direction: Int) {
    if (KeyHold.held) return
    val d = context.dpf(Tokens.NUDGE)
    // One animation on one axis that goes out and comes back: no end action that a cancel could skip
    val motion = animate().setStartDelay(0).setDuration(Tokens.NUDGE_OUT_MS + Tokens.NUDGE_BACK_MS).setInterpolator(outAndBack)
    when (direction) {
        View.FOCUS_LEFT -> motion.translationX(-d)
        View.FOCUS_RIGHT -> motion.translationX(d)
        View.FOCUS_UP -> motion.translationY(-d)
        View.FOCUS_DOWN -> motion.translationY(d)
        else -> return
    }
    motion.start()
}

/** 0 → 1 over the first third (the nudge out), back to 0 over the rest; both legs settle. */
private val outAndBack = Interpolator { t ->
    val out = Tokens.NUDGE_OUT_MS.toFloat() / (Tokens.NUDGE_OUT_MS + Tokens.NUDGE_BACK_MS)
    if (t < out) Tokens.settle.getInterpolation(t / out) else 1f - Tokens.settle.getInterpolation((t - out) / (1f - out))
}

/** Grows the view from a little smaller as it fades in, after [delay]: a badge that just appeared. */
fun View.popIn(delay: Long = Tokens.CHANGE_DELAY_MS) {
    alpha = 0f
    scaleX = Tokens.POP_FROM
    scaleY = Tokens.POP_FROM
    animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(Tokens.POP_MS)
        .setInterpolator(Tokens.ease).withLayer().start()
    animate().startDelay = 0
}
