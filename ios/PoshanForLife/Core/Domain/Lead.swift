import Foundation

/// Pipeline order = declaration order, mirroring the backend's `LeadStage`
/// enum (`new` → `lost`). Wire values are lowercase.
enum LeadStage: String, Codable, CaseIterable, Identifiable {
    case new, contacted, qualified, proposed, converted, lost

    var id: String { rawValue }

    var label: String {
        switch self {
        case .new: return "New"
        case .contacted: return "Contacted"
        case .qualified: return "Qualified"
        case .proposed: return "Proposed"
        case .converted: return "Converted"
        case .lost: return "Lost"
        }
    }
}

/// Wire values are lowercase snake_case, same convention as
/// `BadgeCriteriaType`. `stageChange`/`converted` are service-auto-created
/// only — the "Log activity" UI offers just `loggable`.
enum LeadActivityType: String, Codable, CaseIterable, Identifiable {
    case note, call, whatsapp
    case estimateSent = "estimate_sent"
    case stageChange = "stage_change"
    case converted

    var id: String { rawValue }

    static let loggable: [LeadActivityType] = [.note, .call, .whatsapp, .estimateSent]

    var label: String {
        switch self {
        case .note: return "Note"
        case .call: return "Call"
        case .whatsapp: return "WhatsApp"
        case .estimateSent: return "Estimate sent"
        case .stageChange: return "Stage change"
        case .converted: return "Converted"
        }
    }

    var symbolName: String {
        switch self {
        case .note: return "note.text"
        case .call: return "phone.fill"
        case .whatsapp: return "message.fill"
        case .estimateSent: return "doc.text.fill"
        case .stageChange: return "arrow.left.arrow.right"
        case .converted: return "checkmark.circle.fill"
        }
    }
}

enum LeadSource: String, Codable, CaseIterable {
    case referral
    case socialMedia = "social_media"
    case website
    case walkIn = "walk_in"
    case phone
    case whatsapp
    case mobileApp = "mobile_app"
    case other

    var label: String {
        switch self {
        case .referral: return "Referral"
        case .socialMedia: return "Social media"
        case .website: return "Website"
        case .walkIn: return "Walk-in"
        case .phone: return "Phone"
        case .whatsapp: return "WhatsApp"
        case .mobileApp: return "Mobile app"
        case .other: return "Other"
        }
    }
}

/// Row shape for `GET /leads`.
struct LeadListItem: Decodable, Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    let phone: String?
    let email: String?
    let city: String?
    let age: Int?
    let source: LeadSource?
    let stage: LeadStage
    let assignedPractitioner: UserRef?
    let interestedProgramme: ServiceRef?
    private let nextFollowupAtRaw: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, phone, email, city, age, source, stage, assignedPractitioner, interestedProgramme
        case nextFollowupAtRaw = "nextFollowupAt"
    }

    var nextFollowupAt: Date? { ISO8601.date(from: nextFollowupAtRaw) }
}

/// One timeline entry — `oldStage`/`newStage` are only populated when
/// `activityType == .stageChange`.
struct LeadActivity: Decodable, Identifiable, Equatable {
    let id: String
    let activityType: LeadActivityType
    let description: String
    let oldStage: LeadStage?
    let newStage: LeadStage?
    let createdBy: UserRef?
    private let createdAtRaw: String?

    private enum CodingKeys: String, CodingKey {
        case id, activityType, description, oldStage, newStage, createdBy
        case createdAtRaw = "createdAt"
    }

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
}

/// Full record for `GET /leads/{id}` — a superset of `LeadListItem` plus
/// `healthGoal`/`notes`/`convertedPatientId`/the activity timeline.
struct LeadDetail: Decodable, Identifiable, Equatable {
    let id: String
    let name: String
    let phone: String?
    let email: String?
    let city: String?
    let age: Int?
    let gender: String?
    let source: LeadSource?
    let healthGoal: String?
    let notes: String?
    let stage: LeadStage
    let assignedPractitioner: UserRef?
    let interestedProgramme: ServiceRef?
    private let nextFollowupAtRaw: String?
    let convertedPatientId: String?
    let createdBy: UserRef?
    let activities: [LeadActivity]

    private enum CodingKeys: String, CodingKey {
        case id, name, phone, email, city, age, gender, source, healthGoal, notes, stage
        case assignedPractitioner, interestedProgramme, convertedPatientId, createdBy, activities
        case nextFollowupAtRaw = "nextFollowupAt"
    }

    var nextFollowupAt: Date? { ISO8601.date(from: nextFollowupAtRaw) }
}

/// Reflects whatever `search`/`practitionerId`/`source` filter is active on
/// the list request, but never the list's own `stage` filter — a
/// stage-filtered stage breakdown would be meaningless. Keyed by
/// `LeadStage.rawValue`.
struct LeadSummary: Decodable, Equatable {
    let stageCounts: [String: Int]
    let followupToday: Int
    let newThisWeek: Int
    let converted: Int
    let conversionRate: Double

    func count(for stage: LeadStage) -> Int { stageCounts[stage.rawValue] ?? 0 }
}

/// `GET /leads`'s `data` payload — summary stats travel inline with the page
/// of leads rather than as a separate endpoint.
struct LeadListResponse: Decodable {
    let leads: [LeadListItem]
    let summary: LeadSummary
}

struct ConvertLeadResponse: Decodable {
    let patientId: String
    let message: String?
}

// MARK: - Request bodies

struct UpdateLeadStageRequest: Encodable {
    let stage: String
}

struct CreateLeadActivityRequest: Encodable {
    let activityType: String
    let description: String
}

struct ScheduleFollowupRequest: Encodable {
    let followupAt: String
    let message: String?
}

/// All fields optional — only assigning a service sends the last four.
struct ConvertLeadRequest: Encodable {
    let name: String?
    let phone: String?
    let email: String?
    let dateOfBirth: String?
    let bloodGroup: String?
    let heightCm: Double?
    let assignServiceId: String?
    let serviceType: String?
    let startDate: String?
    let price: Double?
}

/// Wire-format helpers for the two date shapes this feature sends, neither
/// of which is the UTC-anchored convention `ISO8601`/`AppointmentTime` use
/// for `Instant` fields — these are genuinely local.
enum LeadDateFormat {
    /// `OffsetDateTime` for `followupAt` — the device's own offset, matching
    /// Android's `ZonedDateTime.of(date, time, ZoneId.systemDefault())`: a
    /// follow-up is scheduled in local wall-clock time, not converted to UTC.
    static func offsetDateTime(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = .current
        return formatter.string(from: date)
    }

    /// Plain `yyyy-MM-dd` `LocalDate` — device-local calendar day, for the
    /// convert flow's `dateOfBirth`/`startDate`.
    static func localDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        return formatter.string(from: date)
    }
}
