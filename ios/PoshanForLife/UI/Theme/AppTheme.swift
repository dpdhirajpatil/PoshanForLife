import SwiftUI

/// SwiftUI has no `MaterialTheme` equivalent, so this is it: the same
/// "wrap this role's screens in this theme" contract the Compose app gets from
/// `MaterialTheme.colorScheme`, delivered through the environment.
///
/// Views read it with `@Environment(\.appTheme) private var theme`.
protocol AppTheme {
    /// Themes are appearance-aware, so they're built from the current scheme
    /// rather than being singletons. See ``SwiftUI/View/appTheme(_:)``.
    init(colorScheme: ColorScheme)
    var colorScheme: ColorScheme { get }

    var primary: Color { get }
    var onPrimary: Color { get }
    var background: Color { get }
    var onBackground: Color { get }
    var surface: Color { get }
    var onSurface: Color { get }
    var secondary: Color { get }
    /// Not in the original spec, but every theme needs one and error red is a
    /// brand decision, not a system one.
    var error: Color { get }

    var useTrapeziumMotif: Bool { get }
    var headingWeight: Font.Weight { get }
    var headingUppercase: Bool { get }
}

extension AppTheme {
    var isLight: Bool { colorScheme == .light }
}

private struct AppThemeKey: EnvironmentKey {
    static let defaultValue: AppTheme = PatientTheme(colorScheme: .light)
}

extension EnvironmentValues {
    var appTheme: AppTheme {
        get { self[AppThemeKey.self] }
        set { self[AppThemeKey.self] = newValue }
    }
}

/// Injects a theme built from the *current* appearance.
///
/// The obvious `.environment(\.appTheme, PatientTheme())` can't work: it
/// captures one appearance at construction and then never updates, so the whole
/// theme would freeze in light mode when the user switches to dark. This
/// modifier re-reads `colorScheme` and rebuilds, which is what makes each
/// theme's dark palette actually reachable.
private struct AppThemeModifier<T: AppTheme>: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let themeType: T.Type

    func body(content: Content) -> some View {
        content.environment(\.appTheme, T(colorScheme: colorScheme))
    }
}

extension View {
    /// `PatientTabView().appTheme(PatientTheme.self)`
    func appTheme<T: AppTheme>(_ themeType: T.Type) -> some View {
        modifier(AppThemeModifier(themeType: themeType))
    }
}

// MARK: - Heading helper

/// Applies the theme's heading treatment in one place, so no screen has to
/// remember that Patient/Lead headings are uppercase-and-heavy while Staff's
/// are neither. That contrast is the single biggest driver of Staff's calmer
/// feel, and it drifts the moment call sites reimplement it.
struct ThemedHeading: ViewModifier {
    @Environment(\.appTheme) private var theme
    let size: CGFloat

    func body(content: Content) -> some View {
        content
            .font(.displayFont(theme.headingWeight, size: size))
            .textCase(theme.headingUppercase ? .uppercase : nil)
            .foregroundStyle(theme.onBackground)
    }
}

extension View {
    func themedHeading(size: CGFloat = 24) -> some View {
        modifier(ThemedHeading(size: size))
    }
}
