package com.passmanager.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object NavTransitions {
    private const val DURATION = 250

    val fadeEnter: EnterTransition = fadeIn(tween(DURATION))
    val fadeExit: ExitTransition = fadeOut(tween(DURATION))

    val slideInFromRight: EnterTransition = fadeIn(tween(DURATION)) + slideInHorizontally(tween(DURATION)) { it }
    val slideOutToLeft: ExitTransition = fadeOut(tween(DURATION)) + slideOutHorizontally(tween(DURATION)) { -it / 4 }
    val slideInFromLeft: EnterTransition = fadeIn(tween(DURATION)) + slideInHorizontally(tween(DURATION)) { -it / 4 }
    val slideOutToRight: ExitTransition = fadeOut(tween(DURATION)) + slideOutHorizontally(tween(DURATION)) { it }
}
