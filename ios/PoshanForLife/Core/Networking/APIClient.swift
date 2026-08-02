import Combine
import Foundation

/// Thin async wrapper over `URLSession`. Attaches the bearer token, unwraps the
/// backend's success envelope, refreshes an expired session once, and converts
/// every failure into a typed ``APIError`` so ViewModels never see
/// `URLSession` or raw status codes.
final class APIClient {

    static let defaultEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    static let defaultDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    /// Fires when the session is unrecoverable — refresh failed or there was
    /// nothing to refresh with. `AuthViewModel` subscribes and drops to the
    /// login screen; nothing else needs a "check my session" call.
    let sessionExpired = PassthroughSubject<Void, Never>()

    private let baseURL: URL
    private let session: URLSession
    private let tokenStore: TokenStore
    private let decoder: JSONDecoder
    private let refreshCoordinator = TokenRefreshCoordinator()

    init(
        baseURL: URL = BaseURL.current,
        session: URLSession = .shared,
        tokenStore: TokenStore,
        decoder: JSONDecoder = APIClient.defaultDecoder
    ) {
        self.baseURL = baseURL
        self.session = session
        self.tokenStore = tokenStore
        self.decoder = decoder
    }

    /// Throwing form — use when a caller genuinely wants to `try`.
    /// Repositories should prefer ``send(_:)``.
    func request<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        try await request(endpoint, allowRefresh: true)
    }

    /// Non-throwing form — mirrors the Android client's `Result` convention so
    /// both apps' ViewModels read the same way.
    func send<T: Decodable>(_ endpoint: Endpoint) async -> Result<T, APIError> {
        do {
            return .success(try await request(endpoint))
        } catch let error as APIError {
            return .failure(error)
        } catch {
            return .failure(.decoding)
        }
    }

    // MARK: - Core

    private func request<T: Decodable>(_ endpoint: Endpoint, allowRefresh: Bool) async throws -> T {
        // A missing or unreadable token isn't fatal here — the request goes out
        // unauthenticated and the backend answers 401, which lands in the same
        // refresh path as an expired one.
        let accessToken: String? = endpoint.requiresAuth ? (try? tokenStore.accessToken()) : nil
        let urlRequest = try endpoint.urlRequest(baseURL: baseURL, accessToken: accessToken)

        let (data, http) = try await perform(urlRequest)

        if http.statusCode == 401, endpoint.requiresAuth, allowRefresh {
            let refreshed = await refreshCoordinator.refresh { [weak self] in
                await self?.performRefresh() ?? false
            }
            if refreshed {
                // Rebuilt from scratch so it picks up the new access token.
                return try await request(endpoint, allowRefresh: false)
            }
            endSession()
            throw APIError(error: "Your session has expired. Please sign in again.", code: "AUTH_REQUIRED")
        }

        guard (200..<300).contains(http.statusCode) else {
            throw Self.decodeError(from: data, status: http.statusCode, decoder: decoder)
        }

        // 204 and friends carry no body; only EmptyResponse can absorb that.
        if data.isEmpty, let empty = EmptyResponse() as? T {
            return empty
        }

        do {
            let envelope = try decoder.decode(APIResponse<T>.self, from: data)
            guard let payload = envelope.data else {
                // 2xx with a null payload: fine for EmptyResponse, a contract
                // break for anything else.
                if let empty = EmptyResponse() as? T { return empty }
                throw APIError.decoding
            }
            return payload
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.decoding
        }
    }

    private func perform(_ urlRequest: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch let error as URLError where error.code == .cancelled {
            // A cancelled task is the caller going away (view dismissed, search
            // superseded), not a failure worth showing anyone.
            throw APIError.transport(code: "CANCELLED", message: "Request cancelled")
        } catch {
            throw APIError.network
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.decoding
        }
        return (data, http)
    }

    // MARK: - Refresh

    /// Talks to `/auth/refresh` with a hand-built request rather than going back
    /// through ``request(_:allowRefresh:)``. A refresh that 401s must never
    /// trigger another refresh — same reason Android gives the refresh call its
    /// own Retrofit instance with no Authenticator attached.
    private func performRefresh() async -> Bool {
        guard let refreshToken = try? tokenStore.refreshToken(), !refreshToken.isEmpty else {
            return false
        }

        do {
            let endpoint = try Endpoint.json(
                path: "auth/refresh",
                method: .post,
                body: RefreshRequest(refreshToken: refreshToken),
                requiresAuth: false
            )
            let urlRequest = try endpoint.urlRequest(baseURL: baseURL, accessToken: nil)
            let (data, http) = try await perform(urlRequest)
            guard (200..<300).contains(http.statusCode) else { return false }

            let envelope = try decoder.decode(APIResponse<AuthResponse>.self, from: data)
            guard let auth = envelope.data else { return false }
            try tokenStore.saveTokens(access: auth.accessToken, refresh: auth.refreshToken)
            return true
        } catch {
            return false
        }
    }

    /// Clears credentials and tells whoever is listening. Safe to call twice —
    /// two requests can lose the race to the same dead session.
    private func endSession() {
        try? tokenStore.clear()
        sessionExpired.send()
    }

    /// Parses the backend's error envelope, falling back to the bare HTTP status
    /// when the body isn't one (a proxy error page, an empty 502, …).
    private static func decodeError(from data: Data, status: Int, decoder: JSONDecoder) -> APIError {
        if let apiError = try? decoder.decode(APIError.self, from: data) {
            return apiError
        }
        return .http(status: status, message: HTTPURLResponse.localizedString(forStatusCode: status))
    }
}
