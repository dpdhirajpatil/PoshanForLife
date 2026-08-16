import SwiftUI

/// The bell icon + unread badge, shared by every role's toolbar. One
/// `NotificationsViewModel` per bell instance, so its badge and the sheet it
/// presents always agree — see `NotificationsListView`'s doc comment.
struct NotificationBellButton: View {

    @StateObject private var viewModel: NotificationsViewModel
    private let deepLinkRouter: DeepLinkRouter
    @State private var isPresented = false

    init(repository: NotificationsRepository, deepLinkRouter: DeepLinkRouter) {
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(repository: repository))
        self.deepLinkRouter = deepLinkRouter
    }

    var body: some View {
        Button {
            isPresented = true
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                if viewModel.unreadCount > 0 {
                    UnreadBadge(count: viewModel.unreadCount)
                        .offset(x: 8, y: -6)
                }
            }
        }
        .accessibilityLabel(
            viewModel.unreadCount > 0 ? "Notifications, \(viewModel.unreadCount) unread" : "Notifications"
        )
        .accessibilityIdentifier("notification-bell")
        .task { await viewModel.load() }
        .sheet(isPresented: $isPresented) {
            NotificationsListView(viewModel: viewModel, deepLinkRouter: deepLinkRouter)
                // Refresh on close too: a push may have arrived, or another
                // bell instance elsewhere may have changed the read state,
                // while this sheet was open.
                .onDisappear { Task { await viewModel.load() } }
        }
    }
}

private struct UnreadBadge: View {
    let count: Int
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(count > 9 ? "9+" : "\(count)")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, count > 9 ? 4 : 5)
            .padding(.vertical, 2)
            .background(theme.error, in: Capsule())
            .accessibilityHidden(true)
    }
}
