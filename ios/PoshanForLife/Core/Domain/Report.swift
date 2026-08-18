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
struct InBodyData: Codable, Equatable {
    var weightKg: Double? = nil
    var bodyFatPercent: Double? = nil
    var skeletalMuscleMassKg: Double? = nil
    var bmi: Double? = nil
    var visceralFatLevel: Double? = nil
    var bodyWaterL: Double? = nil
    var proteinKg: Double? = nil
    var mineralKg: Double? = nil
    var basalMetabolicRate: Double? = nil
    var bodyFatMassKg: Double? = nil
    var fatFreeMassKg: Double? = nil
    var waistHipRatio: Double? = nil
    var targetWeightKg: Double? = nil
    var weightControlKg: Double? = nil
    var fatControlKg: Double? = nil
    var muscleControlKg: Double? = nil
    var obesityDegreePercent: Double? = nil
    var intracellularWaterL: Double? = nil
    var extracellularWaterL: Double? = nil
    var inbodyScore: Int? = nil

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

/// `POST /reports/upload`'s response. Only the fields the capture flow
/// actually reads — the backend also sends `healthRecordId`, `fileUrl`,
/// `confidence`, `extractionMethod`, and `warnings`, but the confidence tier
/// is recomputed client-side from `extractedFieldCount` (see
/// `confidenceTierFor`), matching Android rather than trusting the server's
/// own tier string.
struct ReportUploadResponse: Decodable {
    let reportId: String
    let parsedData: InBodyData?
    let extractedFieldCount: Int
}

/// `PATCH /reports/{id}` body. There is no `confirmedData` field on the
/// backend — corrections to a low-confidence AI extraction go through the
/// same `parsedData` key the upload response first populated.
struct UpdateReportRequest: Encodable {
    let title: String
    let notes: String?
    let status: String? = nil
    let parsedData: InBodyData
}
