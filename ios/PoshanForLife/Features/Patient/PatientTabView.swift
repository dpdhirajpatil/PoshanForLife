import SwiftUI

/// Patient bottom navigation — five tabs, matching the Android app's
/// `PatientNavGraph` exactly: Home · Track · Programmes · Reports · Profile.
/// Everything else (Appointments, Badges, Products) lives behind a More screen
/// there; when those arrive here they go the same way, not into a sixth tab.
struct PatientTabView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        TabView {
            NavigationStack {
                DashboardView(
                    repository: container.dashboardRepository,
                    reminders: container.reminderScheduler
                )
            }
            .tabItem { Label("Home", systemImage: "house.fill") }

            NavigationStack {
                TrackView(
                    repository: container.healthTracking,
                    goalsStore: container.goalsStore,
                    reminders: container.reminderScheduler
                )
            }
            .tabItem { Label("Track", systemImage: "chart.xyaxis.line") }

            tab("Programmes", systemImage: "book.fill")
            tab("Reports", systemImage: "doc.text.fill")
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
