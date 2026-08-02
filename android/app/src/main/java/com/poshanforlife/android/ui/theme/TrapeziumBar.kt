package com.poshanforlife.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    minHeight: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .background(color = color, shape = TrapeziumShape)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        // Without this the content pins to the bar's top-left corner instead of
        // sitting on its centre line. A minimum height rather than a fixed one so
        // a long label at a large font scale wraps and grows the bar, rather than
        // wrapping inside a fixed box and being clipped.
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/**
 * The canonical use of the motif: a short ALL-CAPS label chip sitting immediately above the
 * card it introduces — the "ACTIVE PROGRAMME" pattern from the agreed Patient mockup
 * (Direction A), and the same treatment Lead's home sections use (Direction C).
 *
 * A section-header accent, NOT a container style. Don't wrap ordinary cards in it.
 *
 * The `.uppercase()` here is safe to hard-code despite [brandHeading] existing for shared
 * screens: this component is Patient/Lead-only by contract, so there's no theme under which
 * it should render mixed case.
 */
@Composable
fun TrapeziumSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BrandNavy,
) {
    TrapeziumBar(modifier = modifier, color = color, minHeight = 40.dp) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            // The bar is always a solid navy, in both light and dark, so the label
            // needs the off-white foreground rather than the scheme's onSurface —
            // which would be near-navy in light mode and vanish.
            color = BrandOffWhite,
        )
    }
}
