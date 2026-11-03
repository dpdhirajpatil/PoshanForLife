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
                LeadHomeView(repository: container.leadSelfRepository)
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

            // Track and Goals are the patient screens, reused as on Android:
            // the backend lets a LEAD log and read its own health records.
            NavigationStack {
                TrackView(
                    repository: container.healthTracking,
                    goalsStore: container.goalsStore,
                    reminders: container.reminderScheduler,
                    healthKit: container.healthKitManager
                )
            }
            .tabItem { Label("Track", systemImage: "chart.xyaxis.line") }

            NavigationStack {
                GoalsView(store: container.goalsStore)
            }
            .tabItem { Label("Goals", systemImage: "target") }

            NavigationStack {
                ProfileView(healthKit: container.healthKitManager, themePreferenceStore: container.themePreferenceStore)
            }
            .tabItem { Label("Profile", systemImage: "person.crop.circle.fill") }
        }
        .tint(theme.onBackground)
    }
}
