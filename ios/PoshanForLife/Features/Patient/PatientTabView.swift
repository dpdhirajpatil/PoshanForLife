import SwiftUI

/// Patient bottom navigation — Home · Track · Programmes · Reports · More.
///
/// Android's patient nav carries more items than this in its bottom bar;
/// SwiftUI's `TabView` cannot. Past five items it silently collapses the
/// overflow into a **system-generated** "More" list that ignores the app theme
/// — adding Appointments as a sixth tab did exactly that, swallowing Profile
/// and the real More screen into an unthemed system list two levels deep.
///
/// So five is a hard ceiling, and the fifth is ours: Profile moves inside it,
/// alongside Appointments. Same shape `PractitionerTabView` already uses, and
/// where Badges and Products go when they arrive — never a sixth tab.
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
                    reminders: container.reminderScheduler,
                    healthKit: container.healthKitManager
                )
            }
            .tabItem { Label("Track", systemImage: "chart.xyaxis.line") }

            NavigationStack {
                ProgrammesListView(
                    repository: container.programmesRepository,
                    profile: container.dashboardRepository
                )
            }
            .tabItem { Label("Programmes", systemImage: "book.fill") }

            NavigationStack {
                ReportsListView(
                    repository: container.reportsRepository,
                    profile: container.dashboardRepository
                )
            }
            .tabItem { Label("Reports", systemImage: "doc.text.fill") }

            NavigationStack {
                PatientMoreScreen()
            }
            .tabItem { Label("More", systemImage: "ellipsis.circle") }
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

/// The patient's overflow destinations, built explicitly so they stay on-theme
/// — see this file's header for why SwiftUI's automatic version isn't usable.
/// Badges and Products join this list when they arrive.
struct PatientMoreScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        List {
            NavigationLink {
                AppointmentsListView(
                    role: .patient,
                    repository: container.appointmentsRepository
                )
            } label: {
                MenuRow(item: MenuRowItem(title: "Appointments", systemImage: "calendar"))
            }

            NavigationLink {
                ProfileView(healthKit: container.healthKitManager)
            } label: {
                MenuRow(item: MenuRowItem(title: "Profile", systemImage: "person.crop.circle.fill"))
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("More")
    }
}
