package com.poshanforlife.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.poshanforlife.android.ui.theme.BrandGreen
import com.poshanforlife.android.ui.theme.BrandNavyLight
import com.poshanforlife.android.ui.theme.BrandNavySecondaryDark
import kotlin.math.min

/**
 * Hand-rolled Canvas ring (not Material3's `CircularProgressIndicator`) so
 * the arc/track colors are exact brand values rather than theme-default
 * muted ones — BrandGreen arc over a BrandNavyLight (dark: a desaturated
 * navy) track, center-labeled with the percentage. Built for AN-22's Lead
 * streak ring; reuse this verbatim for any future patient-side upgrade
 * rather than adding a second ring component.
 */
@Composable
fun ProgressRing(
    percentComplete: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
) {
    val trackColor = if (isSystemInDarkTheme()) BrandNavySecondaryDark else BrandNavyLight
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val diameter = min(this.size.width, this.size.height) - stroke.width
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = BrandGreen,
                startAngle = -90f,
                sweepAngle = 360f * (percentComplete.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = "$percentComplete%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
