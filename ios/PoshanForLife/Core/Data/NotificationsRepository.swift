import Foundation

protocol NotificationsRepository: AnyObject {
    func list(limit: Int) async -> Result<NotificationListResponse, APIError>
    func markAllRead() async -> Result<Void, APIError>
}

/// `/notifications` — identical shape for every role; the backend scopes to
/// the caller's own rows server-side, so there is nothing role-specific here.
///
/// There is no per-notification mark-as-read: the backend's own doc comment
/// says so explicitly ("the original API only has mark all as read"), so the
/// UI must not imply otherwise — see `NotificationsListView`.
final class NotificationsRepositoryImpl: NotificationsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// The backend clamps to 50 server-side (`MAX_LIMIT`) regardless of what's
    /// requested, so passing more than that just wastes a query param.
    func list(limit: Int = 50) async -> Result<NotificationListResponse, APIError> {
        await client.send(
            Endpoint(
                path: "notifications",
                method: .get,
                queryItems: [URLQueryItem(name: "limit", value: String(limit))]
            )
        )
    }

    func markAllRead() async -> Result<Void, APIError> {
        let result: Result<[String: Bool], APIError> = await client.send(
            Endpoint(path: "notifications", method: .patch)
        )
        return result.map { _ in () }
    }
}
