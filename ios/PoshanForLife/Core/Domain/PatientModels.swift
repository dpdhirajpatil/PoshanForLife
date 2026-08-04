import Foundation

/// `GET users/me`'s richer profile — distinct from the leaner ``User`` embedded
/// in `AuthResponse` (id/name/email/role only).
struct UserDetail: Decodable, Equatable, Identifiable {
    let id: String
    let name: String
    /// Nil for a phone-OTP account, which has a verified phone instead.
    let email: String?
    let role: UserRole?
    let phone: String?
    let phoneVerified: Bool
    let avatarUrl: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, email, role, phone, phoneVerified, avatarUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        email = try c.decodeIfPresent(String.self, forKey: .email)
        role = try c.decodeIfPresent(String.self, forKey: .role).flatMap(UserRole.init(wire:))
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        phoneVerified = try c.decodeIfPresent(Bool.self, forKey: .phoneVerified) ?? false
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
    }

    init(id: String, name: String, email: String? = nil, role: UserRole? = nil,
         phone: String? = nil, phoneVerified: Bool = false, avatarUrl: String? = nil) {
        self.id = id
        self.name = name
        self.email = email
        self.role = role
        self.phone = phone
        self.phoneVerified = phoneVerified
        self.avatarUrl = avatarUrl
    }

    /// "Good morning, {firstName}" — the greeting wants the first word only.
    var firstName: String {
        name.trimmingCharacters(in: .whitespaces)
            .split(separator: " ")
            .first
            .map(String.init) ?? name
    }

    /// Up to two initials for the avatar fallback.
    var initials: String {
        name.trimmingCharacters(in: .whitespaces)
            .split(separator: " ")
            .prefix(2)
            .compactMap { $0.first.map { String($0).uppercased() } }
            .joined()
    }
}

/// One InBody measurement. Every metric is optional — a record may be captured
/// from a partial scan or a manual weight-only entry.
struct HealthRecord: Decodable, Equatable, Identifiable {
    let id: String
    let recordDate: String?
    let weightKg: Double?
    let bmi: Double?
    let bodyFatPct: Double?
    let skeletalMuscleMassKg: Double?
}

struct ServiceRef: Decodable, Equatable {
    let id: String
    let name: String
    let serviceCode: String?
    let durationWeeks: Int?
}

struct UserRef: Decodable, Equatable {
    let id: String
    let name: String
}

/// `status` is the lowercase wire enum: "active" / "completed" / "cancelled".
struct PatientProgramme: Decodable, Equatable, Identifiable {
    let id: String
    let serviceType: String?
    let catalogueItem: ServiceRef?
    let startDate: String?
    let endDate: String?
    let priceInr: Double?
    let status: String
    let assignedDoctor: UserRef?

    static let activeStatus = "active"
}

struct DocumentPartyRef: Decodable, Equatable {
    let id: String
    let name: String
}

/// `status` is the lowercase wire enum: "draft" / "sent" / "paid".
struct DocumentListItem: Decodable, Equatable, Identifiable {
    let id: String
    let documentType: String
    let documentNumber: String
    let status: String
    let patient: DocumentPartyRef?
    let total: Double
    let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case id, documentType, documentNumber, status, patient, total, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        documentType = try c.decodeIfPresent(String.self, forKey: .documentType) ?? "invoice"
        documentNumber = try c.decode(String.self, forKey: .documentNumber)
        status = try c.decode(String.self, forKey: .status)
        patient = try c.decodeIfPresent(DocumentPartyRef.self, forKey: .patient)
        total = try c.decode(Double.self, forKey: .total)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
    }

    /// An invoice that has been issued but not paid. There is deliberately no
    /// "pending" status on the backend — the enum is draft/sent/paid, and an
    /// unpaid issued invoice is `sent`.
    static let unpaidStatus = "sent"
}
