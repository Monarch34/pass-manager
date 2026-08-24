package com.passmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.passmanager.R

/**
 * The shield's share of the adaptive icon's guaranteed-visible region: 50.25 / 72.
 *
 * The launcher masks the 108x108 adaptive canvas down to the central 72x72, so that masked square
 * — not the full canvas — is what a user actually sees. Feeding the same ratio to
 * [ContentScale.Fit] on the 280:335 art reproduces the launcher framing exactly, and it is the same
 * constant the desktop composable uses, so both platforms show one lockup.
 */
private const val SHIELD_FILL_FRACTION = 0.697917f

/**
 * Brand shield mark: the fixed light-mint plate with the full-colour shield centred on it.
 *
 * The plate is `@color/ic_launcher_primary_container` (#FFCCFBF1) and deliberately *not*
 * `colorScheme.primaryContainer`. The shield art is fixed light-scheme colour, so on the dark
 * scheme's #0D9488 container the #21837D outer shield measured 1.22:1; against #FFCCFBF1 the same
 * ink measures 4.05:1. The shield is drawn in its own six LogoPalette colours and is never tinted.
 *
 * [RoundedCornerShape] is given the percent overload so the silhouette stays constant from the
 * 32.dp generator badge to the 112.dp lock-screen hero; an absolute radius read as a circle at the
 * small end.
 */
@Composable
fun AppShieldLogo(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(25),
        color = colorResource(id = R.color.ic_launcher_primary_container),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.ic_vault_shield),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(SHIELD_FILL_FRACTION)
            )
        }
    }
}
