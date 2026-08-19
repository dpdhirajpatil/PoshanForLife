import SwiftUI

/// Staff's real Settings screen — was a `PlaceholderScreen`. Shared by
/// Practitioner and Admin, same as `StaffTheme` itself.
struct SettingsView: View {

    @ObservedObject var themePreferenceStore: ThemePreferenceStore
    @Environment(\.appTheme) private var theme

    var body: some View {
        List {
            Section {
                AppearancePicker(store: themePreferenceStore)
            } header: {
                Text("Appearance")
            } footer: {
                Text("Choose how Poshan for Life looks on this device.")
            }

            Section {
                SignOutButton()
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Settings")
    }
}
