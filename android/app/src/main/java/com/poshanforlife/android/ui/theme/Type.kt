package com.poshanforlife.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Display/headline/title slots use [displayFontFamily] at ExtraBold per the CI Guidelines. The
 * guide mandates ALL CAPS for the display weight (Gilroy Heavy is always-caps in the brand
 * guide) — Compose has no text-transform, so callers must `.uppercase()` the string themselves
 * wherever a displayLarge/displayMedium/displaySmall/headlineLarge/headlineMedium/headlineSmall
 * style is used; this Typography only sets font/weight/size, not casing.
 *
 * Shared by [PoshanPatientTheme] and [PoshanLeadTheme] — Lead's "gamified" direction changes
 * which components a screen reaches for (streak chips, badge rows), not the type scale itself.
 */
val PoshanPatientTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Identical type scale to Patient — Lead's gamified feel comes from theme color + components, not typography. */
val PoshanLeadTypography = PoshanPatientTypography

/**
 * Staff theme (Admin + Practitioner, "calm" direction): headline/title slots drop from ExtraBold
 * to Medium and are NOT forced uppercase by callers — this is the single biggest driver of
 * Staff's cleaner feel, more than the color changes. Body/label slots are unchanged from Patient.
 */
val PoshanStaffTypography = PoshanPatientTypography.copy(
    displayLarge = PoshanPatientTypography.displayLarge.copy(fontWeight = FontWeight.Medium),
    displayMedium = PoshanPatientTypography.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmall = PoshanPatientTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = PoshanPatientTypography.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = PoshanPatientTypography.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = PoshanPatientTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = PoshanPatientTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
)

/**
 * The script accent font, for a single decorative word/flourish — never wired into a default
 * Material typography slot, and never `.uppercase()`d (the brand guide explicitly forbids
 * all-caps on the script font). Patient/Lead screens may use it sparingly; Staff-theme
 * (Practitioner/Admin) screens should never use it at all — omit entirely, don't just avoid
 * uppercasing it.
 */
val AccentStyle = TextStyle(
    fontFamily = accentFontFamily,
    fontSize = 28.sp,
)
