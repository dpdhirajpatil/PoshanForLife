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
    /// Overrides the default `application/json` header — set by
    /// ``multipart(path:fields:fileField:fileName:fileData:fileContentType:requiresAuth:)``
    /// to the multipart boundary content type instead.
    var contentType: String?

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
        if let contentType {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        } else if body != nil {
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

    /// Builds a `multipart/form-data` POST — the report-upload endpoint is the
    /// only caller today, so this doesn't try to generalise beyond one file
    /// plus a handful of plain-text fields.
    static func multipart(
        path: String,
        fields: [String: String],
        fileField: String,
        fileName: String,
        fileData: Data,
        fileContentType: String,
        requiresAuth: Bool = true
    ) -> Endpoint {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()

        for (key, value) in fields {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }

        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append(
            "Content-Disposition: form-data; name=\"\(fileField)\"; filename=\"\(fileName)\"\r\n"
                .data(using: .utf8)!
        )
        body.append("Content-Type: \(fileContentType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        return Endpoint(
            path: path,
            method: .post,
            body: body,
            requiresAuth: requiresAuth,
            contentType: "multipart/form-data; boundary=\(boundary)"
        )
    }
}
