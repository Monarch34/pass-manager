package com.passmanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

fun Modifier.shakeOnTrigger(triggerCount: Int): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            offsetX.snapTo(0f)
            repeat(4) { i ->
                val sign = if (i % 2 == 0) 1f else -1f
                offsetX.animateTo(
                    targetValue = sign * 12f,
                    animationSpec = tween(50)
                )
            }
            offsetX.animateTo(0f, animationSpec = tween(50))
        }
    }
    this.offset { IntOffset(offsetX.value.roundToInt(), 0) }
}
