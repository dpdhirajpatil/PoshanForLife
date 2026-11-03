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
    /// Self-signup with email + password. Creates a LEAD account; persists tokens on success.
    func signup(_ request: SignupRequest) async -> Result<AuthResponse, APIError>
    /// Sends a 6-digit code to `phone` (any local format; the backend normalises to E.164).
    func requestOtp(phone: String, purpose: OtpPurpose) async -> Result<OtpRequestResponse, APIError>
    /// Verifies a code. SIGNUP needs `name`; LOGIN ignores it. Persists tokens on success.
    func verifyOtp(phone: String, otp: String, purpose: OtpPurpose, name: String?) async -> Result<AuthResponse, APIError>
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

/// `POST /auth/signup`. Optional fields are omitted from the JSON when nil.
struct SignupRequest: Encodable {
    let name: String
    let email: String
    let password: String
    let phone: String?
    let city: String?
    let healthGoal: String?
}

/// Mirrors the backend's `OtpPurpose`. ADD_PHONE (linking a number from
/// settings) isn't offered on iOS yet, so only the two sign-in purposes exist.
enum OtpPurpose: String, Encodable {
    case signup = "SIGNUP"
    case login = "LOGIN"
}

private struct OtpRequestBody: Encodable {
    let phone: String
    let purpose: OtpPurpose
}

private struct OtpVerifyBody: Encodable {
    let phone: String
    let otp: String
    let purpose: OtpPurpose
    let name: String?
}

/// The code itself is never echoed back, not even on dev.
struct OtpRequestResponse: Decodable {
    let sent: Bool
    let expiresInSeconds: Int
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
        await authenticate(path: "auth/login", body: LoginRequest(email: email, password: password))
    }

    func signup(_ request: SignupRequest) async -> Result<AuthResponse, APIError> {
        await authenticate(path: "auth/signup", body: request)
    }

    func requestOtp(phone: String, purpose: OtpPurpose) async -> Result<OtpRequestResponse, APIError> {
        guard let endpoint = Self.publicPost("auth/otp/request", OtpRequestBody(phone: phone, purpose: purpose)) else {
            return .failure(.transport(code: "ENCODING_ERROR", message: "Could not build the request"))
        }
        return await client.send(endpoint)
    }

    func verifyOtp(phone: String, otp: String, purpose: OtpPurpose, name: String?) async -> Result<AuthResponse, APIError> {
        await authenticate(
            path: "auth/otp/verify",
            body: OtpVerifyBody(phone: phone, otp: otp, purpose: purpose, name: name)
        )
    }

    private static func publicPost<Body: Encodable>(_ path: String, _ body: Body) -> Endpoint? {
        try? Endpoint.json(path: path, method: .post, body: body, requiresAuth: false)
    }

    /// Shared by every call that ends in a token pair (password login, signup,
    /// OTP verify), so all of them persist the session the same way.
    private func authenticate<Body: Encodable>(path: String, body: Body) async -> Result<AuthResponse, APIError> {
        guard let endpoint = Self.publicPost(path, body) else {
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
