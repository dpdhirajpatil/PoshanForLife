import Foundation

/// The wire values are the backend's `Role` enum verbatim. Note `doctor` —
/// the API says DOCTOR while the UI (and this project's folder names) say
/// "Practitioner"; the translation happens here and nowhere else.
enum UserRole: String, Decodable, CaseIterable {
    case admin = "ADMIN"
    case doctor = "DOCTOR"
    case patient = "PATIENT"
    case lead = "LEAD"

    /// Unknown roles decode to `nil` rather than throwing, so a backend that
    /// adds a role can't hard-fail an older build's login.
    init?(wire: String) {
        self.init(rawValue: wire.uppercased())
    }

    var displayName: String {
        switch self {
        case .admin: return "Admin"
        case .doctor: return "Practitioner"
        case .patient: return "Patient"
        case .lead: return "Lead"
        }
    }
}

/// `email` is optional: a phone-OTP account has a verified phone instead. The
/// backend guarantees every user has at least one of the two, never neither.
/// (Android learned this the hard way — a non-optional email crashed the first
/// phone signup.)
struct User: Decodable, Equatable, Identifiable {
    let id: String
    let name: String
    let email: String?
    let role: UserRole?

    private enum CodingKeys: String, CodingKey {
        case id, name, email, role
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        email = try container.decodeIfPresent(String.self, forKey: .email)
        role = try container.decodeIfPresent(String.self, forKey: .role).flatMap(UserRole.init(wire:))
    }

    init(id: String, name: String, email: String?, role: UserRole?) {
        self.id = id
        self.name = name
        self.email = email
        self.role = role
    }
}

/// Login/signup/refresh all return this shape.
struct AuthResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let user: User
}
