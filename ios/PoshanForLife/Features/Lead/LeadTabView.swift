import SwiftUI

/// Lead bottom navigation — four tabs only: Home · Track · Goals · Profile.
/// Matching the Android `LeadNavGraph`, where everything else is deliberately
/// disabled for this role rather than hidden behind More; a Lead is a
/// pre-conversion account with minimal permissions.
struct LeadTabView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        TabView {
            NavigationStack {
                PlaceholderScreen(title: "Home")
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            NotificationBellButton(
                                repository: container.notificationsRepository,
                                deepLinkRouter: container.deepLinkRouter
                            )
                        }
                    }
            }
            .tabItem { Label("Home", systemImage: "house.fill") }

            tab("Track", systemImage: "chart.xyaxis.line")
            tab("Goals", systemImage: "target")
            tab("Profile", systemImage: "person.crop.circle.fill", showsSignOut: true)
        }
        .tint(theme.onBackground)
    }

    private func tab(_ title: String, systemImage: String, showsSignOut: Bool = false) -> some View {
        NavigationStack {
            PlaceholderScreen(title: title, showsSignOut: showsSignOut)
        }
        .tabItem { Label(title, systemImage: systemImage) }
    }
}
