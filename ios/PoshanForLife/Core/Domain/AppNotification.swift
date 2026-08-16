import Foundation

/// One in-app notification. `type` is a free-form string the backend defines
/// (`APPOINTMENT_BOOKED`, `INBODY_REPORT`, `BADGE_EARNED`, …) — there's no
/// enum on the wire, so it stays a string here too rather than an
/// optimistically-decoded case set that breaks the moment the backend adds one.
///
/// `relatedEntityType`/`relatedEntityId` are the deep-link target, when there
/// is one (both nil for a type with nothing to link to, e.g. a system
/// announcement). See `DeepLinkRouter` for what actually consumes these.
struct AppNotification: Decodable, Identifiable, Equatable {
    let id: String
    let type: String
    let title: String
    let message: String
    let read: Bool
    let relatedEntityType: String?
    let relatedEntityId: String?
    let createdAtRaw: String?

    private enum CodingKeys: String, CodingKey {
        case id, type, title, message, read, relatedEntityType, relatedEntityId
        case createdAtRaw = "createdAt"
    }

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }

    /// "2h ago" / "3d ago" — `RelativeDateTimeFormatter` handles the
    /// singular/plural and unit-choice work, so nothing bespoke here.
    var relativeTime: String {
        guard let createdAt else { return "" }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: createdAt, relativeTo: Date())
    }
}

/// `GET /notifications` wraps its page in an object, same convention as
/// reports and programmes — never a bare array.
struct NotificationListResponse: Decodable {
    let notifications: [AppNotification]
    let unreadCount: Int
}

/// What a tapped notification — from the in-app list or a delivered push —
/// resolves to. Both `NotificationsListView`'s row tap and
/// `PushCoordinator.userNotificationCenter(_:didReceive:)` produce one of
/// these through the same `DeepLinkRouter`, so there is exactly one place
/// that turns a target into actual navigation.
struct NotificationDeepLink: Equatable {
    let relatedEntityType: String?
    let relatedEntityId: String?
}
