package com.poshanforlife.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// CI Guidelines prohibit black anywhere in the design — every slot below is a real brand color
// (or a tint derived from one), never Color.Black/near-black gray.

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = BrandNavyDarkest,
    background = Color.White,
    onBackground = BrandNavyDarkest,
    surface = Color.White,
    onSurface = BrandNavyDarkest,
    secondary = BrandNavyLightest,
    onSecondary = BrandNavyDarkest,
    tertiary = BrandGold500,
    onTertiary = BrandNavyDarkest,
    error = BrandBerry700,
)

// Not specified in the CI guide — derived from its "logo on dark background" variant (gray
// wordmark becomes white, lime stays lime) plus the no-black rule.
private val DarkColorScheme = darkColorScheme(
    background = BrandNavyDarkest, // a real brand color, never black
    onBackground = BrandOffWhite,
    surface = BrandNavySurfaceDark,
    onSurface = BrandOffWhite,
    primary = BrandGreen, // stays exactly as bright — matches the logo's dark-bg treatment
    onPrimary = BrandNavyDarkest,
    secondary = BrandNavySecondaryDark,
    tertiary = BrandGold300,
    error = BrandBerry500,
)

val PoshanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The CI Guidelines specify one fixed brand identity, not a Material You adaptive one — no
 * dynamic color here, unlike a default Compose theme scaffold.
 */
@Composable
fun PoshanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PoshanShapes,
        content = content,
    )
}
