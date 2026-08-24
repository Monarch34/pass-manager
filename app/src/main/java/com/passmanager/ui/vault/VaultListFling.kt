package com.passmanager.ui.vault

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Scales the initial fling velocity before the decay runs.
 *
 * `1f` is the platform feel: the list travels exactly as far as every other Android list does for
 * the same flick. Lowering it brakes the fling — which reads as "scrolling is slow" rather than as
 * "controlled", because the finger movement and the resulting travel stop matching. Only go below
 * 1f if you have a specific complaint that the list overshoots.
 */
private const val VaultListFlingVelocityScale = 1f

/**
 * Fling that decays on the same spline Android's own scrollers use, so the deceleration curve
 * matches the system. The previous spec was an exponential decay, which brakes hardest right after the
 * lift, which is the part of the gesture the eye reads as sluggishness.
 */
private class VaultListFlingBehavior(
    private val decaySpec: DecayAnimationSpec<Float>
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (kotlin.math.abs(initialVelocity) < 0.5f) return initialVelocity
        val scaledVelocity = initialVelocity * VaultListFlingVelocityScale
        var lastValue = 0f
        var remaining = scaledVelocity
        AnimationState(initialValue = 0f, initialVelocity = scaledVelocity).animateDecay(decaySpec) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            remaining = velocity
            if (kotlin.math.abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return remaining
    }
}

@Composable
internal fun rememberVaultListFlingBehavior(): FlingBehavior {
    // Density-aware: the spline spec is derived from the current density, so it must be read in
    // composition rather than captured in a density-free `remember { }` the way the old spec was.
    val decaySpec = rememberSplineBasedDecay<Float>()
    return remember(decaySpec) { VaultListFlingBehavior(decaySpec) }
}
