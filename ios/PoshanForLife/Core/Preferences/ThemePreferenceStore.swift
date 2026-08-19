import Combine
import SwiftUI

/// System/Light/Dark — mirrors Android's `ThemeMode`. App-wide rather than
/// per-role: the three brand themes (Patient/Lead/Staff) each already carry
/// their own light and dark palette, and light-or-dark is a device-level
/// preference, not a property of which role happens to be signed in.
enum ThemePreference: String, CaseIterable, Codable {
    case system
    case light
    case dark

    var label: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }

    var description: String {
        switch self {
        case .system: return "Follow the device setting"
        case .light: return "Always light"
        case .dark: return "Always dark"
        }
    }

    /// `nil` for `.system` — `.preferredColorScheme(nil)` restores whatever
    /// the device is set to, same as never applying the modifier at all.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// `UserDefaults`, same reasoning as `GoalsStore`: a single plain preference
/// value, no secrecy requirement, device-level rather than account data — so
/// it isn't synced to the backend and applies before sign-in too (`RootView`
/// reads it for the login screen, not just the signed-in tabs).
@MainActor
final class ThemePreferenceStore: ObservableObject {

    @Published var mode: ThemePreference {
        didSet { defaults.set(mode.rawValue, forKey: Self.key) }
    }

    private let defaults: UserDefaults
    private static let key = "theme_preference"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.mode = ThemePreference(rawValue: defaults.string(forKey: Self.key) ?? "") ?? .system
    }
}
