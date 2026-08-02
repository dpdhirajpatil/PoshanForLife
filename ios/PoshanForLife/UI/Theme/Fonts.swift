import SwiftUI

/// Font helpers, named for their *role* rather than the typeface — swapping the
/// licensed Gilroy / Alexander Lettering files in later means editing only the
/// three name constants below, not every call site.
///
/// Substitutions match Android's: Poppins stands in for Gilroy, Alex Brush for
/// Alexander Lettering.
///
/// NOTE: the .ttf files are not in the repo yet. `Font.custom` falls back to the
/// system font when a family is missing, so everything still renders — it just
/// renders off-brand. See UI/Theme/Fonts/README.md.
extension Font {

    private enum Family {
        static let displayRegular = "Poppins-Regular"
        static let displaySemiBold = "Poppins-SemiBold"
        static let displayExtraBold = "Poppins-ExtraBold"
        static let body = "Poppins-Regular"
        static let accent = "AlexBrush-Regular"
    }

    /// Headings and display text. Only three weights are bundled, so anything
    /// heavier than semibold resolves to ExtraBold.
    static func displayFont(_ weight: Font.Weight, size: CGFloat) -> Font {
        let name: String
        switch weight {
        case .heavy, .black, .bold:
            name = Family.displayExtraBold
        case .semibold, .medium:
            name = Family.displaySemiBold
        default:
            name = Family.displayRegular
        }
        return .custom(name, size: size)
    }

    static func bodyFont(size: CGFloat) -> Font {
        .custom(Family.body, size: size)
    }

    /// The script accent, for a single decorative word. Never uppercased — the
    /// brand guide forbids all-caps on the script face — and never used on
    /// Staff-theme screens at all.
    static func accentFont(size: CGFloat) -> Font {
        .custom(Family.accent, size: size)
    }
}
