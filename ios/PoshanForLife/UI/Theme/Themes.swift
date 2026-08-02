import SwiftUI

// MARK: - Theme 1: Patient (brand-forward)

/// Loud lime CTAs, navy header blocks, trapezium motif used prominently.
/// Also the base the other two are described against.
struct PatientTheme: AppTheme {
    let colorScheme: ColorScheme

    init(colorScheme: ColorScheme) {
        self.colorScheme = colorScheme
    }

    var primary: Color { .brandGreen }
    var onPrimary: Color { .brandNavyDarkest }
    var background: Color { isLight ? .white : .brandNavyDarkest }
    var onBackground: Color { isLight ? .brandNavyDarkest : .brandOffWhite }
    var surface: Color { isLight ? .brandSurfaceLight : .brandNavySurfaceDark }
    var onSurface: Color { isLight ? .brandNavyDarkest : .brandOffWhite }
    var secondary: Color { isLight ? .brandNavyLightest : .brandNavySecondaryDark }
    var error: Color { isLight ? .brandBerry700 : .brandBerry500 }

    var useTrapeziumMotif: Bool { true }
    var headingWeight: Font.Weight { .heavy }
    var headingUppercase: Bool { true }
}

// MARK: - Theme 2: Lead (gamified)

/// Identical base palette to Patient in both appearances. The difference is the
/// gamification-only accents below, plus which components Lead's screens reach
/// for — not the colours themselves.
struct LeadTheme: AppTheme {
    let colorScheme: ColorScheme

    init(colorScheme: ColorScheme) {
        self.colorScheme = colorScheme
    }

    private var base: PatientTheme { PatientTheme(colorScheme: colorScheme) }

    var primary: Color { base.primary }
    var onPrimary: Color { base.onPrimary }
    var background: Color { base.background }
    var onBackground: Color { base.onBackground }
    var surface: Color { base.surface }
    var onSurface: Color { base.onSurface }
    var secondary: Color { base.secondary }
    var error: Color { base.error }

    var useTrapeziumMotif: Bool { true }
    var headingWeight: Font.Weight { .heavy }
    var headingUppercase: Bool { true }
}

/// Lead-only semantics, deliberately outside ``AppTheme`` so Patient and Staff
/// screens can't reach them. IOS-22's screens get at these by downcasting the
/// environment theme — see ``EnvironmentValues/leadTheme``.
extension LeadTheme {
    var streakChipBackground: Color { isLight ? .brandGoldTint : .brandStreakChipDark }
    var streakChipText: Color { isLight ? .brandGold700 : .brandGold300 }

    /// Rotates the Plum/Indigo tint pair per badge index, matching Android's
    /// `badgeEarnedBackground`. (Missing from the IOS-03 spec, which lists the
    /// tints but never uses them.)
    func badgeEarnedBackground(_ index: Int) -> Color {
        if index.isMultiple(of: 2) {
            return isLight ? .brandPlumTint : .brandPlumTintDark
        }
        return isLight ? .brandIndigoTint : .brandIndigoTintDark
    }
}

extension EnvironmentValues {
    /// Non-nil only inside a Lead-themed subtree.
    var leadTheme: LeadTheme? { appTheme as? LeadTheme }
}

// MARK: - Theme 3: Staff (calm — Admin AND Practitioner, one theme not two)

/// The accent colour is the same lime, used far more sparingly (outline over
/// filled at call sites). No tinted surfaces, no navy header blocks, no
/// trapezium, no accent font.
struct StaffTheme: AppTheme {
    let colorScheme: ColorScheme

    init(colorScheme: ColorScheme) {
        self.colorScheme = colorScheme
    }

    var primary: Color { .brandGreen }
    var onPrimary: Color { .brandNavyDarkest }
    var background: Color { isLight ? .white : .brandNavyDarkest }
    var onBackground: Color { isLight ? .brandNavyDarkest : .brandOffWhiteMuted }
    var surface: Color { isLight ? .white : .brandStaffSurfaceDark }
    var onSurface: Color { isLight ? .brandNavyDarkest : .brandOffWhiteMuted }
    var secondary: Color { isLight ? .brandStaffSecondaryLight : .brandStaffSecondaryDark }
    var error: Color { isLight ? .brandBerry700 : .brandBerry500 }

    var useTrapeziumMotif: Bool { false }
    /// NOT .heavy, and NOT uppercase — this pair, more than any colour choice,
    /// is what makes Staff read as cleaner.
    var headingWeight: Font.Weight { .medium }
    var headingUppercase: Bool { false }
}
