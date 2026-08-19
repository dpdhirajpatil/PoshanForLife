import SwiftUI

/// The patient's real Profile screen — was a `PlaceholderScreen` until
/// IOS-11 needed somewhere to put the "Connect Apple Health" row.
struct ProfileView: View {

    @ObservedObject var healthKit: HealthKitManager
    @ObservedObject var themePreferenceStore: ThemePreferenceStore
    @Environment(\.appTheme) private var theme
    @State private var isConnecting = false

    var body: some View {
        List {
            Section {
                AppearancePicker(store: themePreferenceStore)
            } header: {
                Text("Appearance")
            } footer: {
                Text("Choose how Poshan for Life looks on this device.")
            }

            if HealthKitManager.isAvailable {
                Section {
                    healthRow
                } header: {
                    Text("Connected apps")
                } footer: {
                    // iOS never lets an app revoke its own HealthKit access —
                    // only the Settings app can fully turn it off, so "Manage"
                    // below links out rather than pretending to disconnect.
                    if healthKit.connectionStatus == .connected {
                        Text("To fully disconnect, turn off access from the iOS Settings app's Health section — apps can't revoke their own HealthKit access.")
                    }
                }
            }

            Section {
                SignOutButton()
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Profile")
    }

    private var healthRow: some View {
        HStack {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Apple Health")
                        .font(.bodyFont(size: 16))
                        .foregroundStyle(theme.onSurface)
                    Text(statusText)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                }
            } icon: {
                Image(systemName: "heart.fill")
                    .foregroundStyle(theme.primary)
                    .frame(width: 24)
            }
            Spacer()
            actionButton
        }
        .padding(.vertical, 4)
        .listRowBackground(theme.surface)
    }

    private var statusText: String {
        switch healthKit.connectionStatus {
        case .unavailable: return "Not available on this device"
        case .notConnected: return "Not connected"
        case .connected: return "Connected"
        }
    }

    @ViewBuilder
    private var actionButton: some View {
        switch healthKit.connectionStatus {
        case .unavailable:
            EmptyView()
        case .notConnected:
            Button {
                Task {
                    isConnecting = true
                    await healthKit.requestAuthorization()
                    isConnecting = false
                }
            } label: {
                if isConnecting {
                    ProgressView()
                } else {
                    Text("Connect")
                        .font(.displayFont(.semibold, size: 14))
                        .foregroundStyle(theme.primary)
                }
            }
            .buttonStyle(.plain)
            .disabled(isConnecting)
        case .connected:
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text("Manage")
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.primary)
            }
            .buttonStyle(.plain)
        }
    }
}
