package com.poshanforlife.android.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — Green family (dominant brand color)
val BrandGreenDarkest = Color(0xFF565825)
val BrandGreenDark = Color(0xFF959636)
val BrandGreen = Color(0xFFDADF27) // core brand lime
val BrandGreenLight = Color(0xFFDDDE76)
val BrandGreenLightest = Color(0xFFEAEBAA)

// Primary — Navy family (headings, text, dark surfaces)
val BrandNavyDarkest = Color(0xFF262B64) // used for ALL headings in the brand guide
val BrandNavyDark = Color(0xFF497197)
val BrandNavy = Color(0xFF326EB6)
val BrandNavyLight = Color(0xFF99BBDD)
val BrandNavyLightest = Color(0xFFC1D7EC)

// Secondary families — one shade at a time for depth, never dominant
val BrandOlive500 = Color(0xFF91BC3F)
val BrandBerry700 = Color(0xFFBD3E62)
val BrandRust500 = Color(0xFFF26C4C)
val BrandGold500 = Color(0xFFFFCF42)

// Accent families — small decorative elements only
val BrandPlum500 = Color(0xFFE89CC4)
val BrandIndigo500 = Color(0xFF8488C2)

// Dark-theme-only tints (not in the CI guide's static swatches — derived to satisfy the
// no-black rule against a BrandNavyDarkest background; see Theme.kt's dark ColorScheme).
val BrandOffWhite = Color(0xFFF5F6EC) // green-tinted off-white, never Color.White or Color.Black
val BrandNavySurfaceDark = Color(0xFF323768) // BrandNavyDarkest, lightened for card elevation
val BrandNavySecondaryDark = Color(0xFF3D437A)
val BrandGold300 = Color(0xFFFEDD7E)
val BrandBerry500 = Color(0xFFF05685)
