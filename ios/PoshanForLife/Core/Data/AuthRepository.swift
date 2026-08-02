import Combine
import Foundation

/// Repository seam for authentication.
///
/// Token refresh deliberately does *not* live here — it belongs to
/// ``APIClient``, which is the only thing that sees a 401. Putting it here too
/// would mean two components racing to rotate the same refresh token.
protocol AuthRepository: AnyObject {
    /// Latest known user, or nil when signed out. Emits on login, logout, and
    /// whenever the profile is re-read.
    var currentUserPublisher: AnyPublisher<User?, Never> { get }
    var currentUser: User? { get }

    /// Persists both tokens on success.
    func login(email: String, password: String) async -> Result<AuthResponse, APIError>
    /// Re-reads the signed-in user from the backend.
    func loadCurrentUser() async -> Result<User, APIError>
    func logout()
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
}

struct RefreshRequest: Encodable {
    let refreshToken: String
}

final class AuthRepositoryImpl: AuthRepository {

    private let client: APIClient
    private let tokenStore: TokenStore
    private let userSubject = CurrentValueSubject<User?, Never>(nil)

    init(client: APIClient, tokenStore: TokenStore) {
        self.client = client
        self.tokenStore = tokenStore
    }

    var currentUserPublisher: AnyPublisher<User?, Never> {
        userSubject.eraseToAnyPublisher()
    }

    var currentUser: User? { userSubject.value }

    func login(email: String, password: String) async -> Result<AuthResponse, APIError> {
        let endpoint: Endpoint
        do {
            endpoint = try Endpoint.json(
                path: "auth/login",
                method: .post,
                body: LoginRequest(email: email, password: password),
                requiresAuth: false
            )
        } catch {
            return .failure(.transport(code: "ENCODING_ERROR", message: "Could not build the request"))
        }

        let result: Result<AuthResponse, APIError> = await client.send(endpoint)
        guard case .success(let auth) = result else { return result }

        do {
            try tokenStore.saveTokens(access: auth.accessToken, refresh: auth.refreshToken)
        } catch {
            // Signing in but being unable to persist would look like a
            // successful login that evaporates on next launch — surface it
            // rather than let the user discover it later.
            return .failure(.transport(
                code: "KEYCHAIN_ERROR",
                message: "Couldn't save your session on this device."
            ))
        }
        userSubject.send(auth.user)
        return result
    }

    /// `users/me`, not `auth/me` — the backend has no `auth/me` route.
    func loadCurrentUser() async -> Result<User, APIError> {
        let result: Result<User, APIError> = await client.send(Endpoint(path: "users/me", method: .get))
        if case .success(let user) = result {
            userSubject.send(user)
        }
        return result
    }

    func logout() {
        try? tokenStore.clear()
        userSubject.send(nil)
    }
}
