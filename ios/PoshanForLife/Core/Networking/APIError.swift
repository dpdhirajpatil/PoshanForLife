import Foundation

/// Error envelope returned by every non-2xx backend response, plus the
/// client-side transport/decoding failures that never reach the server.
///
/// `code` is the stable, machine-readable half — ViewModels branch on it
/// (`VALIDATION_ERROR`, `AUTH_REQUIRED`, `RATE_LIMIT_EXCEEDED`, …) exactly as
/// the Android app does. `error` is the human-readable half and is usually
/// already written for the user, so prefer showing it over inventing new copy.
struct APIError: Error, Decodable, Equatable {
    let success: Bool
    let error: String
    let code: String
    /// Field-level messages on validation failures, keyed by field name.
    let details: [String: String]?

    var message: String { error }

    init(success: Bool = false, error: String, code: String, details: [String: String]? = nil) {
        self.success = success
        self.error = error
        self.code = code
        self.details = details
    }

    private enum CodingKeys: String, CodingKey {
        case success, error, code, details
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        success = try container.decodeIfPresent(Bool.self, forKey: .success) ?? false
        // `error` and `code` are decoded strictly: if they're missing this isn't
        // an error envelope at all, and APIClient should fall back to the raw
        // HTTP status rather than surface a half-decoded message.
        error = try container.decode(String.self, forKey: .error)
        code = try container.decode(String.self, forKey: .code)

        // The backend types `details` as Object, not String: bean-validation
        // failures put a field -> message map there (see GlobalExceptionHandler),
        // while other errors omit it. Decoding it as a plain String would throw
        // and cost us the field messages, so accept either shape.
        if let map = try? container.decodeIfPresent([String: String].self, forKey: .details) {
            details = map
        } else if let text = try? container.decodeIfPresent(String.self, forKey: .details) {
            details = ["detail": text]
        } else {
            details = nil
        }
    }
}

extension APIError {
    /// Failures that happen before or instead of a backend response.
    /// Codes match the Android client's so shared behaviour stays comparable.
    static func transport(code: String, message: String) -> APIError {
        APIError(error: message, code: code)
    }

    static func http(status: Int, message: String) -> APIError {
        APIError(error: message, code: "HTTP_\(status)")
    }

    static let network = APIError.transport(
        code: "NETWORK_ERROR",
        message: "Can't reach the server. Check your connection."
    )

    static let decoding = APIError.transport(
        code: "DECODING_ERROR",
        message: "The server sent something unexpected."
    )

    /// True when the session is gone and the user must sign in again.
    var isUnauthorized: Bool {
        code == "AUTH_REQUIRED" || code == "HTTP_401"
    }
}
