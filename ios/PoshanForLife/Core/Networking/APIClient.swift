import Foundation

/// Thin async wrapper over `URLSession`. Attaches the bearer token, unwraps the
/// backend's success envelope, and converts every failure into a typed
/// ``APIError`` so ViewModels never see `URLSession` or raw status codes.
///
/// Deliberately not an actor: it holds no mutable state of its own, and every
/// method is already `async`. Token reads go to the Keychain, which is
/// thread-safe on its own.
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

    private let baseURL: URL
    private let session: URLSession
    private let tokenStore: TokenStore
    private let decoder: JSONDecoder

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
        // A missing or unreadable token isn't fatal here — the request goes out
        // unauthenticated and the backend answers 401, which IOS-02's refresh
        // seam handles uniformly with an expired one.
        let accessToken: String? = endpoint.requiresAuth ? (try? tokenStore.accessToken()) : nil
        let urlRequest = try endpoint.urlRequest(baseURL: baseURL, accessToken: accessToken)

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

    /// Parses the backend's error envelope, falling back to the bare HTTP status
    /// when the body isn't one (a proxy error page, an empty 502, …).
    private static func decodeError(from data: Data, status: Int, decoder: JSONDecoder) -> APIError {
        if let apiError = try? decoder.decode(APIError.self, from: data) {
            return apiError
        }
        return .http(status: status, message: HTTPURLResponse.localizedString(forStatusCode: status))
    }
}
