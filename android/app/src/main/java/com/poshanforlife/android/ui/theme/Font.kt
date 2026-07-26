package com.poshanforlife.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.poshanforlife.android.R

/**
 * Named generically rather than after the font itself: Gilroy and Alexander Lettering are the
 * real CI-mandated fonts but are licensed, not available on Google Fonts. Poppins/Alex Brush are
 * free substitutes standing in until the real .ttf files are sourced — swapping them in later
 * should only mean changing what's in res/font/ and the Font() calls below, so no composable
 * elsewhere may reference "Poppins"/"Gilroy" by name.
 */
val displayFontFamily = FontFamily(
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
    Font(R.font.poppins_medium, FontWeight.Medium),
)

val bodyFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
)

val accentFontFamily = FontFamily(
    Font(R.font.alex_brush_regular, FontWeight.Normal),
)
