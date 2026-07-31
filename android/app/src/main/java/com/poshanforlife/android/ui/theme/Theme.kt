package com.poshanforlife.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// CI Guidelines prohibit black anywhere in the design — every slot below (in all three themes'
// dark schemes) is a real brand color or a tint derived from one, never Color.Black/near-black
// gray. A "calm/minimal" dark mode (Staff) is the one most tempting to default to near-black —
// resist that here too.

// ---------------------------------------------------------------------------------------------
// THEME 1 — PoshanPatientTheme (brand-forward: navy header blocks, loud lime CTAs, trapezium
// motif used prominently). Also the base every other theme's ColorScheme is compared against.
// ---------------------------------------------------------------------------------------------

private val PatientLightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = BrandNavyDarkest,
    background = Color.White,
    onBackground = BrandNavyDarkest,
    surface = Color(0xFFF7F8EF),
    onSurface = BrandNavyDarkest,
    secondary = BrandNavyLightest,
    onSecondary = BrandNavyDarkest,
    tertiary = BrandGold500,
    onTertiary = BrandNavyDarkest,
    error = BrandBerry700,
)

private val PatientDarkColorScheme = darkColorScheme(
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

// ---------------------------------------------------------------------------------------------
// THEME 2 — PoshanLeadTheme (gamified). Identical ColorScheme to Patient in both light and dark
// — the visual difference is which components Lead's screens reach for (streak chips, badge
// rows), not the palette itself. See the streakChip*/badgeEarnedBackground extensions below.
// ---------------------------------------------------------------------------------------------

private val LeadLightColorScheme = PatientLightColorScheme
private val LeadDarkColorScheme = PatientDarkColorScheme

// ---------------------------------------------------------------------------------------------
// THEME 3 — PoshanStaffTheme (calm — Admin + Practitioner share this one theme, not two).
// Same accent color as Patient but used far more sparingly (outline over filled at call sites);
// no tinted surfaces, no navy header blocks, no TrapeziumBar, no accent font — see Type.kt.
// ---------------------------------------------------------------------------------------------

private val StaffLightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = BrandNavyDarkest,
    background = Color.White,
    onBackground = BrandNavyDarkest,
    surface = Color.White,
    onSurface = BrandNavyDarkest,
    secondary = Color(0xFFF5F5F0), // near-white, not BrandNavyLightest — Staff avoids Patient/Lead's tinted surfaces
    onSecondary = BrandNavyDarkest,
    tertiary = BrandOlive500,
    error = BrandBerry700,
)

private val StaffDarkColorScheme = darkColorScheme(
    // On-brand, not the near-black a "calm" theme might default to — still a real brand color.
    background = BrandNavyDarkest,
    onBackground = BrandOffWhiteMuted,
    // A darker, more desaturated navy than Patient/Lead's surface — muted, less "branded" feel
    // while still respecting the no-black rule.
    surface = BrandStaffSurfaceDark,
    onSurface = BrandOffWhiteMuted,
    primary = BrandGreen,
    onPrimary = BrandNavyDarkest,
    secondary = BrandStaffSecondaryDark,
    tertiary = BrandOlive500,
    error = BrandBerry500,
)

// Patient/Lead: generously rounded, matching the CI guide's soft, clean, minimal visual language.
val PoshanRoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Staff: subtly less rounded — not a dramatically different shape language, just enough to read
// as "more restrained" alongside the type/color changes.
val PoshanStaffShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Both Patient's and Staff's light backgrounds are Color.White, dark are BrandNavyDarkest — reliable across all three themes. */
private val ColorScheme.isLight: Boolean get() = background == Color.White

/** Lead-only semantic colors — Compose's ColorScheme has no built-in "streak" or "badge" slot. Patient/Staff screens never reference these. */
val ColorScheme.streakChipBackground: Color
    get() = if (isLight) BrandGoldTint else BrandStreakChipDark

val ColorScheme.streakChipText: Color
    get() = if (isLight) BrandGold700 else BrandGold300

/** Rotates through the Plum/Indigo tint pair per badge index — used for AN-22's badge row on Lead's home screen. */
fun ColorScheme.badgeEarnedBackground(index: Int): Color = if (index % 2 == 0) {
    if (isLight) BrandPlumTint else BrandPlumTintDark
} else {
    if (isLight) BrandIndigoTint else BrandIndigoTintDark
}

/**
 * Brand-forward Patient theme — solid BrandNavyDarkest header blocks are this theme's default
 * pattern for top-level screens (Home, Programmes, Reports), unlike Lead/Staff.
 */
@Composable
fun PoshanPatientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PatientDarkColorScheme else PatientLightColorScheme,
        typography = PoshanPatientTypography,
        shapes = PoshanRoundedShapes,
        content = content,
    )
}

/** Gamified Lead theme — same base palette as Patient; reach for [streakChipBackground]/[badgeEarnedBackground] on this screen's ColorScheme. */
@Composable
fun PoshanLeadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LeadDarkColorScheme else LeadLightColorScheme,
        typography = PoshanLeadTypography,
        shapes = PoshanRoundedShapes,
        content = content,
    )
}

/**
 * Calm Staff theme — shared by BOTH Admin and Practitioner, not two separate themes. No
 * TrapeziumBar, no accent font, no navy header blocks at call sites using this theme.
 */
@Composable
fun PoshanStaffTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) StaffDarkColorScheme else StaffLightColorScheme,
        typography = PoshanStaffTypography,
        shapes = PoshanStaffShapes,
        content = content,
    )
}
