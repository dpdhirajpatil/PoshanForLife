import Foundation

/// Wire values are lowercase — `"inbody"`, not `"INBODY"`. The backend accepts
/// either casing as a *query* value (its `fromWire` uppercases first), but the
/// values it *returns* are always lowercase, so comparisons must be too.
enum ReportType: String, Codable {
    case inbody, lab, prescription, other

    static let inbodyWire = "inbody"
}

enum ReportStatus: String, Codable {
    case pending, processing, done, error

    var label: String {
        switch self {
        case .pending: return "Pending"
        case .processing: return "Processing"
        case .done: return "Done"
        case .error: return "Failed"
        }
    }
}

struct ReportListItem: Decodable, Identifiable, Equatable {
    let id: String
    let title: String
    let type: String
    let status: String
    let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var parsedStatus: ReportStatus? { ReportStatus(rawValue: status) }

    private enum CodingKeys: String, CodingKey {
        case id, title, type, status
        case createdAtRaw = "createdAt"
    }
}

/// The 20 fields Claude extracts from an InBody printout. Every one is optional:
/// a low-confidence scan may populate only a handful.
///
/// Note there is **no segmental (arm/trunk/leg) data** — the backend doesn't
/// model it, so the detail screen can't show a Segmental Lean Analysis section.
struct InBodyData: Decodable, Equatable {
    let weightKg: Double?
    let bodyFatPercent: Double?
    let skeletalMuscleMassKg: Double?
    let bmi: Double?
    let visceralFatLevel: Double?
    let bodyWaterL: Double?
    let proteinKg: Double?
    let mineralKg: Double?
    let basalMetabolicRate: Double?
    let bodyFatMassKg: Double?
    let fatFreeMassKg: Double?
    let waistHipRatio: Double?
    let targetWeightKg: Double?
    let weightControlKg: Double?
    let fatControlKg: Double?
    let muscleControlKg: Double?
    let obesityDegreePercent: Double?
    let intracellularWaterL: Double?
    let extracellularWaterL: Double?
    let inbodyScore: Int?

    /// Counted from the data actually present, NOT taken from the response's
    /// `extractedFieldCount`. That stored value can disagree with `parsedData`
    /// — a live report reported 4 while carrying 11 populated fields — and a
    /// header reading "4 of 20" above eleven visible rows just looks wrong.
    var populatedFieldCount: Int {
        let values: [Any?] = [
            weightKg, bodyFatPercent, skeletalMuscleMassKg, bmi, visceralFatLevel,
            bodyWaterL, proteinKg, mineralKg, basalMetabolicRate, bodyFatMassKg,
            fatFreeMassKg, waistHipRatio, targetWeightKg, weightControlKg,
            fatControlKg, muscleControlKg, obesityDegreePercent,
            intracellularWaterL, extracellularWaterL, inbodyScore,
        ]
        return values.filter { $0 != nil }.count
    }
}

struct ReportDetail: Decodable, Identifiable, Equatable {
    let id: String
    let title: String
    let type: String
    let notes: String?
    let status: String
    /// Freshly-signed URL against a private bucket — nil when the report has no
    /// file, and short-lived, so don't cache it.
    let fileUrl: String?
    let parsedData: InBodyData?
    /// "high" / "medium" / "low" — present on the detail only, never on the list.
    let confidence: String?
    let extractedFieldCount: Int?
    let extractionMethod: String?
    let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var parsedStatus: ReportStatus? { ReportStatus(rawValue: status) }
    var isLowConfidence: Bool { confidence?.lowercased() == "low" }

    private enum CodingKeys: String, CodingKey {
        case id, title, type, notes, status, fileUrl, parsedData
        case confidence, extractedFieldCount, extractionMethod
        case createdAtRaw = "createdAt"
    }
}

/// `GET /reports` wraps its page in an object rather than returning a bare array.
struct ReportListResponse: Decodable {
    let reports: [ReportListItem]
}
