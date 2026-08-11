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
val BrandOliveDarkest = Color(0xFF3F4E22)
val BrandOlive500 = Color(0xFF91BC3F)
val BrandOliveLightest = Color(0xFFD3E4A9)
val BrandBerry700 = Color(0xFFBD3E62)
val BrandRust500 = Color(0xFFF26C4C)
val BrandGold700 = Color(0xFF6C5823)
val BrandGold500 = Color(0xFFFFCF42)
val BrandGoldTint = Color(0xFFFCEAAF) // Lead theme streak-chip background

// Accent families — small decorative elements; Lead theme uses these more liberally than Patient/Staff
val BrandPlum500 = Color(0xFFE89CC4)
val BrandPlumTint = Color(0xFFF8D5E5) // Lead theme badge-chip background
val BrandIndigo500 = Color(0xFF8488C2)
val BrandIndigoTint = Color(0xFFD1CFE8) // Lead theme badge-chip background

// Dark-theme-only tints (not in the CI guide's static swatches — derived to satisfy the
// no-black rule against a BrandNavyDarkest background; see Theme.kt's dark ColorSchemes).
val BrandOffWhite = Color(0xFFF5F6EC) // green-tinted off-white, never Color.White or Color.Black
val BrandNavySurfaceDark = Color(0xFF232864) // Patient/Lead dark surface, lightened for card elevation
val BrandNavySecondaryDark = Color(0xFF3D437A)
val BrandGold300 = Color(0xFFFEDD7E)
val BrandBerry500 = Color(0xFFF05685)
val BrandOffWhiteMuted = Color(0xFFF0F0EC) // Staff theme's less-green-tinted off-white
val BrandStaffSurfaceDark = Color(0xFF1E2248) // Staff dark surface — darker/more desaturated than Patient/Lead's
val BrandStaffSecondaryDark = Color(0xFF2C3060)
val BrandStreakChipDark = Color(0xFF3F2A00) // Lead theme streak-chip background, dark mode
val BrandPlumTintDark = Color(0xFF4A2A3C) // Lead theme badge-chip background, dark mode
val BrandIndigoTintDark = Color(0xFF2E2F5C) // Lead theme badge-chip background, dark mode

// ---------------------------------------------------------------------------------------------
// Dark-mode container ladder.
//
// Material's surfaceContainer* / *Container / onSurfaceVariant slots are NOT derived from the
// slots a darkColorScheme(...) call sets — anything left unspecified falls back to M3's own
// baseline palette, which in dark mode is near-black grey. That matters here because the app
// reads those slots constantly (onSurfaceVariant ~162 call sites, surfaceContainerLow ~39 — every
// Card), so leaving them unset put a near-black card on every screen in dark mode, in direct
// violation of the no-black rule the palette above exists to enforce.
//
// In dark mode a "higher" container reads as nearer the light source, so these ascend.
// ---------------------------------------------------------------------------------------------

val BrandNavyContainerLowestDark = Color(0xFF1E2358)
val BrandNavyContainerLowDark = Color(0xFF2D3370)
val BrandNavyContainerDark = Color(0xFF333A7C)
val BrandNavyContainerHighDark = Color(0xFF3A4189)
val BrandNavyContainerHighestDark = Color(0xFF414996)
val BrandNavyOutlineDark = Color(0xFF6B72A8)

// Staff's ladder is darker and more desaturated than Patient/Lead's, matching how its light
// scheme avoids their tinted surfaces.
val BrandStaffContainerLowestDark = Color(0xFF191D3E)
val BrandStaffContainerLowDark = Color(0xFF232858)
val BrandStaffContainerDark = Color(0xFF282D62)
val BrandStaffContainerHighDark = Color(0xFF2E336C)
val BrandStaffContainerHighestDark = Color(0xFF343976)
val BrandStaffOutlineDark = Color(0xFF5A5F8C)
val BrandStaffOnVariantDark = Color(0xFFB9BCD4)

// Error container pair — M3's baseline dark error container is a desaturated maroon that clashes
// with BrandBerry; these are derived from BrandBerry instead.
val BrandBerryContainerDark = Color(0xFF5A1E30)
val BrandBerryOnContainerDark = Color(0xFFFFD9E2)
