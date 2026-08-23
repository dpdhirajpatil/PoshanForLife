import Foundation

/// `CatalogueStatus` on the wire — lowercase, same convention as every other
/// status enum in this codebase.
enum CatalogueStatus: String, Codable, CaseIterable, Identifiable, Hashable {
    case draft, published, archived

    var id: String { rawValue }

    var label: String {
        switch self {
        case .draft: return "Draft"
        case .published: return "Published"
        case .archived: return "Archived"
        }
    }
}

/// The full `CatalogueItemDto` shape — one struct for all three catalogue
/// types (programme/session/challenge), same as the backend's one DTO class.
/// There is no field on the wire saying which type this is: the caller
/// already knows from which `{type}` URL segment it queried, so `type`
/// below is the free-text sub-category (e.g. "Weight loss"), not a
/// programme/session/challenge discriminator — see `ServiceType` for that.
struct CatalogueItem: Decodable, Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    let serviceCode: String?
    let type: String?
    let priceInr: Double?
    let description: String?
    let coverImageUrl: String?
    let status: CatalogueStatus?
    let durationWeeks: Int?
    let durationMinutes: Int?
    let durationDays: Int?
    let goalDescription: String?
    let activeAssignmentCount: Int?
    let createdBy: UserRef?
    private let createdAtRaw: String?
    private let updatedAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var updatedAt: Date? { ISO8601.date(from: updatedAtRaw) }

    /// The one duration/goal field that applies to this item's service type
    /// — for a row subtitle that doesn't need to know which type it is.
    var durationLabel: String? {
        if let durationWeeks { return "\(durationWeeks) week\(durationWeeks == 1 ? "" : "s")" }
        if let durationMinutes { return "\(durationMinutes) min" }
        if let durationDays { return "\(durationDays) day\(durationDays == 1 ? "" : "s")" }
        return nil
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, serviceCode, type, priceInr, description, coverImageUrl, status
        case durationWeeks, durationMinutes, durationDays, goalDescription
        case activeAssignmentCount, createdBy
        case createdAtRaw = "createdAt"
        case updatedAtRaw = "updatedAt"
    }

    /// Memberwise, for previews/tests and the trimmed inline-picker use —
    /// every field but the three the picker actually reads defaults to nil.
    init(
        id: String,
        name: String,
        serviceCode: String? = nil,
        type: String? = nil,
        priceInr: Double? = nil,
        description: String? = nil,
        coverImageUrl: String? = nil,
        status: CatalogueStatus? = nil,
        durationWeeks: Int? = nil,
        durationMinutes: Int? = nil,
        durationDays: Int? = nil,
        goalDescription: String? = nil,
        activeAssignmentCount: Int? = nil,
        createdBy: UserRef? = nil,
        createdAtRaw: String? = nil,
        updatedAtRaw: String? = nil
    ) {
        self.id = id
        self.name = name
        self.serviceCode = serviceCode
        self.type = type
        self.priceInr = priceInr
        self.description = description
        self.coverImageUrl = coverImageUrl
        self.status = status
        self.durationWeeks = durationWeeks
        self.durationMinutes = durationMinutes
        self.durationDays = durationDays
        self.goalDescription = goalDescription
        self.activeAssignmentCount = activeAssignmentCount
        self.createdBy = createdBy
        self.createdAtRaw = createdAtRaw
        self.updatedAtRaw = updatedAtRaw
    }
}

/// `POST /catalogue/{type}` body. All type-specific fields are sent, with the
/// ones that don't apply to the current `ServiceType` left `nil` — the
/// backend requires exactly the matching one(s) and rejects the rest being
/// present with a value, same as `CreateCatalogueItemRequest`'s shape.
struct CreateCatalogueItemRequest: Encodable {
    let name: String
    let serviceCode: String
    let type: String
    let priceInr: Double
    let description: String?
    let coverImageUrl: String?
    let status: CatalogueStatus?
    let durationWeeks: Int?
    let durationMinutes: Int?
    let durationDays: Int?
    let goalDescription: String?
}

/// `PATCH /catalogue/{type}/{id}` body — same shared+type-specific fields as
/// create, minus `type` itself (the backend has no way to change a service's
/// sub-category via update; only creation sets it).
struct UpdateCatalogueItemRequest: Encodable {
    let name: String
    let serviceCode: String
    let priceInr: Double
    let description: String?
    let coverImageUrl: String?
    let status: CatalogueStatus?
    let durationWeeks: Int?
    let durationMinutes: Int?
    let durationDays: Int?
    let goalDescription: String?
}

/// `POST /catalogue/{type}/upload`'s response — one key, `url`.
struct UploadCoverImageResponse: Decodable {
    let url: String
}
