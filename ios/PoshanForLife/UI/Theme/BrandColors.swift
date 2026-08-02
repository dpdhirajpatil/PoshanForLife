import SwiftUI

extension Color {
    /// `Color(hex: 0xDADF27)`. A few lines beats a dependency for this.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// Official "Poshan for Life" CI Guidelines (May 2025) palette. Values are
/// identical to the Android app's `ui/theme/Color.kt` — verified hex by hex.
/// If one side changes, change both.
///
/// HARD RULE: no black anywhere, in any theme, in either appearance. Where you
/// would reach for `.black`, use ``brandNavyDarkest``.
extension Color {

    // MARK: Primary — Green family (dominant brand colour)

    static let brandGreenDarkest = Color(hex: 0x565825)
    static let brandGreenDark = Color(hex: 0x959636)
    /// Core brand lime.
    static let brandGreen = Color(hex: 0xDADF27)
    static let brandGreenLight = Color(hex: 0xDDDE76)
    static let brandGreenLightest = Color(hex: 0xEAEBAA)

    // MARK: Primary — Navy family (headings, text, dark surfaces)

    /// Used for ALL headings in the brand guide, and as the stand-in for black.
    static let brandNavyDarkest = Color(hex: 0x262B64)
    static let brandNavyDark = Color(hex: 0x497197)
    static let brandNavy = Color(hex: 0x326EB6)
    static let brandNavyLight = Color(hex: 0x99BBDD)
    static let brandNavyLightest = Color(hex: 0xC1D7EC)

    // MARK: Secondary families — one shade at a time for depth, never dominant

    static let brandOliveDarkest = Color(hex: 0x3F4E22)
    static let brandOlive500 = Color(hex: 0x91BC3F)
    static let brandOliveLightest = Color(hex: 0xD3E4A9)
    static let brandBerry700 = Color(hex: 0xBD3E62)
    static let brandBerry500 = Color(hex: 0xF05685)
    static let brandRust500 = Color(hex: 0xF26C4C)
    static let brandGold700 = Color(hex: 0x6C5823)
    static let brandGold500 = Color(hex: 0xFFCF42)
    static let brandGold300 = Color(hex: 0xFEDD7E)
    /// Lead theme streak-chip background, light appearance.
    static let brandGoldTint = Color(hex: 0xFCEAAF)

    // MARK: Accent families — small decorative elements, Lead uses them most

    static let brandPlum500 = Color(hex: 0xE89CC4)
    static let brandPlumTint = Color(hex: 0xF8D5E5)
    static let brandIndigo500 = Color(hex: 0x8488C2)
    static let brandIndigoTint = Color(hex: 0xD1CFE8)

    // MARK: Dark-appearance tints
    //
    // Not in the CI guide's static swatches — derived to satisfy the no-black
    // rule against a brandNavyDarkest background. Same derivations as Android.

    /// Green-tinted off-white. Never `.white`, never `.black`.
    static let brandOffWhite = Color(hex: 0xF5F6EC)
    /// Staff's less-green off-white — a quieter foreground than Patient/Lead's.
    static let brandOffWhiteMuted = Color(hex: 0xF0F0EC)
    /// Patient/Lead dark surface, lightened from the background for elevation.
    static let brandNavySurfaceDark = Color(hex: 0x232864)
    static let brandNavySecondaryDark = Color(hex: 0x3D437A)
    /// Staff dark surface — darker and more desaturated than Patient/Lead's.
    static let brandStaffSurfaceDark = Color(hex: 0x1E2248)
    static let brandStaffSecondaryDark = Color(hex: 0x2C3060)
    /// Lead streak chip, dark appearance.
    static let brandStreakChipDark = Color(hex: 0x3F2A00)
    static let brandPlumTintDark = Color(hex: 0x4A2A3C)
    static let brandIndigoTintDark = Color(hex: 0x2E2F5C)

    // MARK: Light-appearance surfaces

    /// Patient/Lead light surface — faintly green-tinted, unlike Staff's white.
    static let brandSurfaceLight = Color(hex: 0xF7F8EF)
    /// Staff's near-white secondary. Deliberately not brandNavyLightest —
    /// Staff avoids Patient/Lead's tinted surfaces.
    static let brandStaffSecondaryLight = Color(hex: 0xF5F5F0)
}
