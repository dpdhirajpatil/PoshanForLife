import Foundation

/// Repository seam for authentication. IOS-01 defines the shape and the
/// token-persistence side effects; IOS-02 builds the screens that call it.
protocol AuthRepository {
    /// Persists both tokens on success — callers only observe the returned user.
    func login(email: String, password: String) async -> Result<AuthResponse, APIError>
    func refresh() async -> Result<AuthResponse, APIError>
    func currentUser() async -> Result<User, APIError>
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

    init(client: APIClient, tokenStore: TokenStore) {
        self.client = client
        self.tokenStore = tokenStore
    }

    func login(email: String, password: String) async -> Result<AuthResponse, APIError> {
        await authenticating {
            try Endpoint.json(
                path: "auth/login",
                method: .post,
                body: LoginRequest(email: email, password: password),
                requiresAuth: false
            )
        }
    }

    func refresh() async -> Result<AuthResponse, APIError> {
        guard let refreshToken = try? tokenStore.refreshToken(), !refreshToken.isEmpty else {
            return .failure(.transport(code: "AUTH_REQUIRED", message: "Signed out"))
        }
        return await authenticating {
            try Endpoint.json(
                path: "auth/refresh",
                method: .post,
                body: RefreshRequest(refreshToken: refreshToken),
                // Sent without the (expired) access token on purpose — the
                // refresh token in the body is the only credential this call needs.
                requiresAuth: false
            )
        }
    }

    /// `users/me`, not `auth/me` — the backend has no `auth/me` route.
    func currentUser() async -> Result<User, APIError> {
        await client.send(Endpoint(path: "users/me", method: .get))
    }

    func logout() {
        try? tokenStore.clear()
    }

    /// Shared tail for the two calls that mint tokens: build the endpoint, send
    /// it, and persist the pair before handing the response back, so no caller
    /// can forget to store them.
    private func authenticating(
        _ makeEndpoint: () throws -> Endpoint
    ) async -> Result<AuthResponse, APIError> {
        let endpoint: Endpoint
        do {
            endpoint = try makeEndpoint()
        } catch {
            return .failure(.transport(code: "ENCODING_ERROR", message: "Could not build the request"))
        }

        let result: Result<AuthResponse, APIError> = await client.send(endpoint)
        if case .success(let auth) = result {
            do {
                try tokenStore.saveTokens(access: auth.accessToken, refresh: auth.refreshToken)
            } catch {
                // Signing in but being unable to persist would look like a
                // successful login that evaporates on next launch — surface it.
                return .failure(.transport(
                    code: "KEYCHAIN_ERROR",
                    message: "Couldn't save your session on this device."
                ))
            }
        }
        return result
    }
}
