package com.poshanforlife.android.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.poshanforlife.android.R

/**
 * Sora — the web app's heading font — bundled directly (variable font, single
 * file, weight axis) rather than substituted with a similar system font, so
 * headings match the web app exactly. Licensed OFL; see
 * assets/fonts/SORA_OFL.txt. Requires API 26+ for variable-axis rendering,
 * well under this app's minSdk 29.
 */
@OptIn(ExperimentalTextApi::class)
val SoraFontFamily = FontFamily(
    Font(R.font.sora_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.sora_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.sora_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.sora_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
