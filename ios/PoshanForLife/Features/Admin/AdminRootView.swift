import SwiftUI

/// Admin navigation — deliberately NOT a TabView.
///
/// A single `NavigationStack` over a settings-style `List`. This is the
/// iOS-native answer to the "Admin needs a drawer" question the Android app
/// solved with `RoleScaffoldDrawer`: many destinations, each visited
/// occasionally, is what a pushed list is for. Porting a literal side drawer
/// would fight the platform, and seven tabs would not fit a tab bar anyway.
struct AdminRootView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var container: AppContainer

    private static let items: [MenuRowItem] = [
        MenuRowItem(title: "Dashboard", systemImage: "square.grid.2x2.fill"),
        MenuRowItem(title: "Patients", systemImage: "person.2.fill"),
        MenuRowItem(title: "Leads", systemImage: "person.badge.plus"),
        MenuRowItem(title: "Orders", systemImage: "shippingbox.fill"),
        MenuRowItem(title: "Transactions", systemImage: "creditcard.fill"),
        MenuRowItem(title: "Products", systemImage: "bag.fill"),
        MenuRowItem(title: "Settings", systemImage: "gearshape.fill"),
    ]

    var body: some View {
        NavigationStack {
            List(Self.items) { item in
                NavigationLink(value: item) {
                    MenuRow(item: item)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Admin")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NotificationBellButton(
                        repository: container.notificationsRepository,
                        deepLinkRouter: container.deepLinkRouter
                    )
                }
            }
            .navigationDestination(for: MenuRowItem.self) { item in
                if item.title == "Leads" {
                    LeadListView(repository: container.leadsRepository)
                } else if item.title == "Products" {
                    CatalogueView(repository: container.catalogueRepository, isAdmin: true)
                } else {
                    PlaceholderScreen(title: item.title, showsSignOut: item.title == "Settings")
                }
            }
        }
        .tint(theme.onBackground)
    }
}
