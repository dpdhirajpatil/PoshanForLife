import SwiftUI

/// Practitioner navigation — four primary tabs plus a More tab we build
/// ourselves: Patients · Leads · Upload · Schedule · More.
///
/// The prompt notes SwiftUI's `TabView` auto-collapses overflow into a native
/// "More" list. That only happens beyond five items, and it lands you in a
/// system-styled list that ignores the app theme. With exactly five, the fifth
/// tab is ours, so this builds it explicitly.
///
/// Every label here says "Practitioner"; the wire role stays `DOCTOR`.
struct PractitionerTabView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        TabView {
            NavigationStack {
                PatientListView(
                    patientsRepository: container.patientsRepository,
                    reportsRepository: container.reportsRepository,
                    programmesRepository: container.programmesRepository
                )
            }
            .tabItem { Label("Patients", systemImage: "person.2.fill") }

            NavigationStack {
                LeadListView(repository: container.leadsRepository)
            }
            .tabItem { Label("Leads", systemImage: "person.badge.plus") }

            tab("Upload", systemImage: "arrow.up.doc.fill")

            NavigationStack {
                AppointmentsListView(
                    role: .practitioner,
                    repository: container.appointmentsRepository
                )
                // Composes with AppointmentsListView's own `.toolbar` (the
                // patient-only book button) rather than replacing it — that
                // block contributes nothing for `.practitioner`, so there's
                // nothing here to collide with.
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        NotificationBellButton(
                            repository: container.notificationsRepository,
                            deepLinkRouter: container.deepLinkRouter
                        )
                    }
                }
            }
            .tabItem { Label("Schedule", systemImage: "calendar") }

            NavigationStack {
                PractitionerMoreScreen()
            }
            .tabItem { Label("More", systemImage: "ellipsis.circle") }
        }
        .tint(theme.onBackground)
    }

    private func tab(_ title: String, systemImage: String) -> some View {
        NavigationStack {
            PlaceholderScreen(title: title)
        }
        .tabItem { Label(title, systemImage: systemImage) }
    }
}

/// The overflow destinations: Orders, Products, Settings.
struct PractitionerMoreScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    private static let items: [MenuRowItem] = [
        MenuRowItem(title: "Orders", systemImage: "shippingbox.fill"),
        MenuRowItem(title: "Products", systemImage: "bag.fill"),
        MenuRowItem(title: "Settings", systemImage: "gearshape.fill"),
    ]

    var body: some View {
        List(Self.items) { item in
            NavigationLink(value: item) {
                MenuRow(item: item)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("More")
        .navigationDestination(for: MenuRowItem.self) { item in
            if item.title == "Settings" {
                SettingsView(themePreferenceStore: container.themePreferenceStore)
            } else {
                PlaceholderScreen(title: item.title)
            }
        }
    }
}
