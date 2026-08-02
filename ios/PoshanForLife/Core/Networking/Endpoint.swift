import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case patch = "PATCH"
    case put = "PUT"
    case delete = "DELETE"
}

/// Describes one backend call. Keeping `URLRequest` construction here means
/// repositories declare *what* they want, never how it's assembled — the same
/// role Retrofit's annotated interfaces play on Android.
struct Endpoint {
    let path: String
    let method: HTTPMethod
    var queryItems: [URLQueryItem] = []
    var body: Data?
    /// `false` only for the handful of public routes (login, signup, OTP).
    var requiresAuth: Bool = true

    /// Every backend route sits under this prefix; callers pass the part after it.
    private static let apiPrefix = "api/v1"

    func urlRequest(baseURL: URL, accessToken: String?) throws -> URLRequest {
        let full = baseURL
            .appendingPathComponent(Self.apiPrefix)
            .appendingPathComponent(path)

        guard var components = URLComponents(url: full, resolvingAgainstBaseURL: false) else {
            throw APIError.transport(code: "INVALID_URL", message: "Could not build a URL for \(path)")
        }
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        guard let url = components.url else {
            throw APIError.transport(code: "INVALID_URL", message: "Could not build a URL for \(path)")
        }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.httpBody = body
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if requiresAuth, let accessToken, !accessToken.isEmpty {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        return request
    }
}

extension Endpoint {
    /// Convenience for the common case of a JSON body encoded from a `Encodable`.
    static func json<Body: Encodable>(
        path: String,
        method: HTTPMethod,
        body: Body,
        requiresAuth: Bool = true,
        encoder: JSONEncoder = APIClient.defaultEncoder
    ) throws -> Endpoint {
        Endpoint(
            path: path,
            method: method,
            body: try encoder.encode(body),
            requiresAuth: requiresAuth
        )
    }
}
