package com.passmanager.ui.vault

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Higher = fling stops sooner (less “flying” after lift). Tweak if the list still feels too fast/slow.
 * Typical range: 1.2f (near default) … 2.0f (heavy braking).
 */
private const val VaultListFlingFrictionMultiplier = 1.55f

/** Scales down initial fling speed before decay (0f–1f). */
private const val VaultListFlingVelocityScale = 0.82f

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
    return remember {
        VaultListFlingBehavior(exponentialDecay(frictionMultiplier = VaultListFlingFrictionMultiplier))
    }
}
