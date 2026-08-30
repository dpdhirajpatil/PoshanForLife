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
///
/// The `*Delta` values are this record minus the previous one for the same
/// patient, **computed server-side**, and nil when either side is missing.
/// Trend tooltips use them rather than recomputing: the backend knows what the
/// true previous record was even when the client is only holding a windowed
/// slice, where the record before the first one on screen isn't loaded at all.
struct HealthRecord: Decodable, Equatable, Identifiable {
    let id: String
    let recordDate: String?
    let weightKg: Double?
    let weightKgDelta: Double?
    let bmi: Double?
    let bmiDelta: Double?
    let bodyFatPct: Double?
    let bodyFatPctDelta: Double?
    let skeletalMuscleMassKg: Double?
    let skeletalMuscleMassKgDelta: Double?

    var date: Date? { ISO8601.date(from: recordDate) }

    func value(for metric: TrendMetric) -> Double? {
        switch metric {
        case .weight: return weightKg
        case .bodyFat: return bodyFatPct
        case .bmi: return bmi
        case .skeletalMuscleMass: return skeletalMuscleMassKg
        }
    }

    func delta(for metric: TrendMetric) -> Double? {
        switch metric {
        case .weight: return weightKgDelta
        case .bodyFat: return bodyFatPctDelta
        case .bmi: return bmiDelta
        case .skeletalMuscleMass: return skeletalMuscleMassKgDelta
        }
    }
}

/// The four metrics the trend charts plot.
///
/// `wireField` is the name the `fields` query param expects — and it is NOT the
/// same as the JSON property. Body fat is `bodyFat` on the query but
/// `bodyFatPct` in the response. Passing the wrong name is silently ignored by
/// the backend and nulls that metric out, producing an empty chart with no
/// error anywhere, so these two spellings must stay in sync deliberately.
enum TrendMetric: String, CaseIterable, Identifiable {
    case weight, bodyFat, bmi, skeletalMuscleMass

    var id: String { rawValue }

    var wireField: String { rawValue }

    var title: String {
        switch self {
        case .weight: return "Weight"
        case .bodyFat: return "Body fat"
        case .bmi: return "BMI"
        case .skeletalMuscleMass: return "Skeletal muscle"
        }
    }

    var unit: String {
        switch self {
        case .weight, .skeletalMuscleMass: return "kg"
        case .bodyFat: return "%"
        case .bmi: return ""
        }
    }

    /// For weight and body fat a fall is progress; for muscle a rise is.
    var lowerIsBetter: Bool {
        switch self {
        case .weight, .bodyFat, .bmi: return true
        case .skeletalMuscleMass: return false
        }
    }
}

/// Exactly one of the three durations is populated, chosen by the item's type.
///
/// There is deliberately **no `description`** here: `ServiceRefDto` doesn't
/// carry one, and the catalogue endpoint that does is `@AdminOrDoctor` — a
/// patient gets 403. See `ProgrammesRepository` for what that costs the
/// detail screen.
struct ServiceRef: Decodable, Equatable, Hashable {
    let id: String
    let name: String
    let serviceCode: String?
    let durationWeeks: Int?
    let durationMinutes: Int?
    let durationDays: Int?

    /// "12 weeks" / "45 min" / "30 days" — whichever this item actually has.
    var durationLabel: String? {
        if let durationWeeks { return durationWeeks == 1 ? "1 week" : "\(durationWeeks) weeks" }
        if let durationDays { return durationDays == 1 ? "1 day" : "\(durationDays) days" }
        if let durationMinutes { return "\(durationMinutes) min" }
        return nil
    }
}

struct UserRef: Decodable, Equatable, Hashable {
    let id: String
    let name: String
}

/// `status` is the lowercase wire enum: "active" / "completed" / "cancelled".
///
/// `Hashable` so it can travel as a `NavigationLink` value: the list response
/// already contains every field the detail screen shows, so pushing the model
/// itself avoids a re-fetch that would buy nothing.
struct PatientProgramme: Decodable, Equatable, Identifiable, Hashable {
    let id: String
    let serviceType: String?
    let catalogueItem: ServiceRef?
    let startDate: String?
    /// For a session this equals `startDate` — the backend stores a single-day
    /// appointment as a zero-length range rather than leaving the end null.
    let endDate: String?
    let priceInr: Double?
    let status: String
    let notes: String?
    let assignedDoctor: UserRef?

    static let activeStatus = "active"

    var type: ServiceType? { serviceType.flatMap(ServiceType.init(rawValue:)) }
    var parsedStatus: AssignmentStatus? { AssignmentStatus(rawValue: status) }
    var displayName: String { catalogueItem?.name ?? "Service" }
}

struct DocumentPartyRef: Decodable, Equatable, Hashable {
    let id: String
    let name: String
}

/// `status` is the lowercase wire enum: "draft" / "sent" / "paid".
struct DocumentListItem: Decodable, Equatable, Identifiable, Hashable {
    let id: String
    let documentType: String
    let documentNumber: String
    let status: String
    let lead: DocumentPartyRef?
    let patient: DocumentPartyRef?
    let total: Double
    let createdAt: String?

    private enum CodingKeys: String, CodingKey {
        case id, documentType, documentNumber, status, lead, patient, total, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        documentType = try c.decodeIfPresent(String.self, forKey: .documentType) ?? "invoice"
        documentNumber = try c.decode(String.self, forKey: .documentNumber)
        status = try c.decode(String.self, forKey: .status)
        lead = try c.decodeIfPresent(DocumentPartyRef.self, forKey: .lead)
        patient = try c.decodeIfPresent(DocumentPartyRef.self, forKey: .patient)
        total = try c.decode(Double.self, forKey: .total)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
    }

    /// An invoice that has been issued but not paid. There is deliberately no
    /// "pending" status on the backend — the enum is draft/sent/paid, and an
    /// unpaid issued invoice is `sent`.
    static let unpaidStatus = "sent"

    var parsedStatus: DocumentStatus? { DocumentStatus(rawValue: status) }

    /// A document points at exactly one of `lead`/`patient` — whichever it is.
    var partyName: String { patient?.name ?? lead?.name ?? "—" }
}
