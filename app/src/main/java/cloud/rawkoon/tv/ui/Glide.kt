package cloud.rawkoon.tv.ui

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Moves one render property of [view] (a strip's translation) to wherever it is asked to go, as a
 * critically damped spring: a new target while it is moving carries the velocity it has, so a held
 * D-pad is one continuous motion instead of a curve restarted on every key repeat, and a single
 * step settles without a jump at its start. [stiffness] sets the pace: 900 settles in about 220 ms.
 */
class Glide(private val view: View, private val property: FloatPropertyCompat<View>, stiffness: Float = ROW_STIFFNESS) {
    private val spring = SpringAnimation(view, property).setSpring(
        SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(stiffness),
    )

    /** Where the view is heading (or sits), to tell a repeat of the same move from a new one. */
    var target = property.getValue(view)
        private set

    fun to(value: Float) {
        if (target == value) return
        target = value
        spring.animateToFinalPosition(value)
    }

    /** Puts the view at [value] at once, dropping any motion. */
    fun snap(value: Float) {
        spring.cancel()
        property.setValue(view, value)
        target = value
    }

    companion object {
        const val ROW_STIFFNESS = 900f
        val X: FloatPropertyCompat<View> = DynamicAnimation.TRANSLATION_X
        val Y: FloatPropertyCompat<View> = DynamicAnimation.TRANSLATION_Y
    }
}
