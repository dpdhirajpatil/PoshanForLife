import Foundation

protocol UserRepository: AnyObject {
    func updateFcmToken(userId: String, token: String) async -> Result<Void, APIError>
}

/// Just the one call this app needs from `/users/{id}` beyond auth/profile,
/// which `AuthRepository`/`DashboardRepository` already own — a dedicated type
/// rather than growing either of those with an unrelated push-specific method.
///
/// **There is no `PATCH /users/me`.** The prompt's exact spec — the backend
/// only exposes `PATCH /users/{id}`, and a literal `.../users/me` PATCH is a
/// 400 (verified live: `id` fails UUID path-variable binding). The caller's
/// own id has to be supplied explicitly, same as every other self-scoped
/// mutation in this backend.
///
/// `fcmToken` is write-only: `UserDetailDto` never echoes it back, so there is
/// nothing to read after a successful update — a bare success is all this
/// call can report.
final class UserRepositoryImpl: UserRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func updateFcmToken(userId: String, token: String) async -> Result<Void, APIError> {
        let body = try? JSONSerialization.data(withJSONObject: ["fcmToken": token])
        let result: Result<UserDetail, APIError> = await client.send(
            Endpoint(path: "users/\(userId)", method: .patch, body: body)
        )
        return result.map { _ in () }
    }
}
