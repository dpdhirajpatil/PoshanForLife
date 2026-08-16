import Foundation

@MainActor
final class NotificationsViewModel: ObservableObject {

    @Published private(set) var notifications: CardState<[AppNotification]> = .loading
    @Published private(set) var unreadCount: Int = 0
    @Published private(set) var isMarkingRead = false

    private let repository: NotificationsRepository

    init(repository: NotificationsRepository) {
        self.repository = repository
    }

    func load() async {
        switch await repository.list(limit: 50) {
        case .success(let response):
            notifications = .success(response.notifications)
            unreadCount = response.unreadCount
        case .failure(let error):
            notifications = .failure(error.message)
        }
    }

    /// The backend has no per-notification mark-read, so this is genuinely
    /// "mark everything read" — no per-row action exists to call instead.
    func markAllRead() async {
        guard unreadCount > 0, !isMarkingRead else { return }
        isMarkingRead = true

        if case .success = await repository.markAllRead() {
            unreadCount = 0
            if case .success(let list) = notifications {
                notifications = .success(list.map { markRead($0) })
            }
        }

        isMarkingRead = false
    }

    private func markRead(_ notification: AppNotification) -> AppNotification {
        AppNotification(
            id: notification.id, type: notification.type, title: notification.title,
            message: notification.message, read: true,
            relatedEntityType: notification.relatedEntityType,
            relatedEntityId: notification.relatedEntityId,
            createdAtRaw: notification.createdAtRaw
        )
    }
}
