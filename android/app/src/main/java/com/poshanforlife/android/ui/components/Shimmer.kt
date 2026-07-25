package com.poshanforlife.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver

/**
 * Generic shimmering-placeholder modifier — not tied to any one screen.
 * Wrap the space a piece of not-yet-loaded content will occupy (a Box with a
 * fixed size, typically) and apply this instead of the real content.
 */
@Composable
fun Modifier.shimmerPlaceholder(visible: Boolean, shape: Shape = RectangleShape): Modifier {
    if (!visible) return this

    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        .compositeOver(MaterialTheme.colorScheme.surface)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 300f, 300f),
    )

    return this
        .clip(shape)
        .background(brush)
}
