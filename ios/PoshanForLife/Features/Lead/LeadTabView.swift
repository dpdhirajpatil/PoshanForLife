import SwiftUI

/// Lead bottom navigation — four tabs only: Home · Track · Goals · Profile.
/// Matching the Android `LeadNavGraph`, where everything else is deliberately
/// disabled for this role rather than hidden behind More; a Lead is a
/// pre-conversion account with minimal permissions.
///
/// Track/Goals/Profile reuse the exact same views/repositories Patient's tabs
/// do — `HealthTrackingRepository`/`GoalsStore` have no PATIENT-only
/// assumption (the backend's `/health-records` explicitly allows LEAD too),
/// and `ProfileView` is role-agnostic (appearance + HealthKit + sign-out).
/// Home is new — see `LeadHomeView`'s doc comment for why it isn't
/// `DashboardView` reused. Home and Track each own an independent
/// `TrackViewModel` instance (same relationship `DashboardView`/`TrackView`
/// already have on the Patient side) — both read the same on-disk
/// repository and singleton `HealthKitManager` underneath, so they never
/// disagree about what "today" logged.
struct LeadTabView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        TabView {
            NavigationStack {
                LeadHomeView(
                    leadSelfRepository: container.leadSelfRepository,
                    healthTracking: container.healthTracking,
                    goalsStore: container.goalsStore,
                    reminders: container.reminderScheduler,
                    healthKit: container.healthKitManager
                )
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
