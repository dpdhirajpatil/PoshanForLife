package com.poshanforlife.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** How far each top corner is inset from its bottom counterpart, as a fraction of width. */
private const val SlantFraction = 0.06f

/**
 * A slightly slanted parallelogram — the recurring bar-behind-headline/CTA motif visible
 * throughout the CI guide. Distinct from a plain RoundedCornerShape button; use behind section
 * headers and primary CTAs where the brand guide shows this shape, not everywhere.
 *
 * Belongs to [PoshanPatientTheme] and [PoshanLeadTheme] only — [PoshanStaffTheme] (Admin,
 * Practitioner) screens must never use this, matching the calmer staff mockup's plain rounded
 * cards throughout. Enforced at each call site, not by disabling the component itself.
 */
val TrapeziumShape = GenericShape { size, _ ->
    val slant = size.width * SlantFraction
    moveTo(slant, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - slant, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun TrapeziumBar(
    modifier: Modifier = Modifier,
    color: Color = BrandNavy,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(color = color, shape = TrapeziumShape)
            .padding(horizontal = 20.dp),
    ) {
        content()
    }
}
