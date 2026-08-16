import SwiftUI

/// Sheet-presented, not pushed: a notification inbox is something you dip
/// into from wherever you are and dismiss back to, not a place with a
/// meaningful position in the navigation stack — matching how iOS's own
/// Notification Center and Mail's inbox both behave as sheets/overlays
/// rather than pushed screens reachable via back-swipe.
struct NotificationsListView: View {

    /// Owned by `NotificationBellButton`, not this view: the button's own
    /// unread badge and this sheet's list have to agree the instant "mark all
    /// read" runs, which only works if they share one instance rather than
    /// each fetching independently.
    @ObservedObject var viewModel: NotificationsViewModel
    @ObservedObject var deepLinkRouter: DeepLinkRouter
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .background(theme.background.ignoresSafeArea())
                .navigationTitle("Notifications")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Close") { dismiss() }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        // Only ever "mark ALL read" — there's no per-row action
                        // to offer instead, see NotificationsRepository.
                        Button("Mark all read") {
                            Task { await viewModel.markAllRead() }
                        }
                        .disabled(viewModel.unreadCount == 0 || viewModel.isMarkingRead)
                    }
                }
                .task { await viewModel.load() }
                .refreshable { await viewModel.load() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.notifications {
        case .loading:
            VStack(spacing: 12) {
                ForEach(0..<4, id: \.self) { _ in SkeletonBlock(height: 64, cornerRadius: 12) }
                Spacer()
            }
            .padding(16)

        case .failure(let message):
            VStack {
                Text(message)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
            .frame(maxWidth: .infinity)
            .padding(24)

        case .success(let notifications):
            if notifications.isEmpty {
                EmptyNotificationsView()
            } else {
                List(notifications) { notification in
                    NotificationRow(notification: notification)
                        .listRowBackground(theme.surface)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            // Marking read is all-or-nothing server-side (see
                            // the repository), so tapping one row can't flip
                            // just that row — only navigate.
                            guard notification.relatedEntityType != nil else { return }
                            deepLinkRouter.pending = NotificationDeepLink(
                                relatedEntityType: notification.relatedEntityType,
                                relatedEntityId: notification.relatedEntityId
                            )
                            dismiss()
                        }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }
}

private struct NotificationRow: View {
    let notification: AppNotification
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(notification.read ? .clear : theme.primary)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(notification.title)
                    .font(.displayFont(notification.read ? .medium : .semibold, size: 15))
                    .foregroundStyle(theme.onSurface)

                Text(notification.message)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .fixedSize(horizontal: false, vertical: true)

                Text(notification.relativeTime)
                    .font(.bodyFont(size: 11))
                    .foregroundStyle(theme.onSurface.opacity(0.5))
            }

            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(notification.read ? "" : "Unread. ")\(notification.title). \(notification.message). \(notification.relativeTime)"
        )
    }
}

private struct EmptyNotificationsView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "bell.slash")
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.7))
            Text("No notifications yet")
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
    }
}
