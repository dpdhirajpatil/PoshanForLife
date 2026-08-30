import Foundation

/// Wire enum, lowercase — same convention as every other status/type enum in
/// this codebase (`@JsonValue` on the backend).
enum DocumentType: String, Codable, CaseIterable, Identifiable, Hashable {
    case estimate, invoice

    var id: String { rawValue }

    var label: String {
        switch self {
        case .estimate: return "Estimate"
        case .invoice: return "Invoice"
        }
    }
}

/// No "pending" status — an unpaid issued invoice is `sent`. See
/// `DocumentListItem.unpaidStatus`.
enum DocumentStatus: String, Codable, CaseIterable, Identifiable, Hashable {
    case draft, sent, paid

    var id: String { rawValue }

    var label: String {
        switch self {
        case .draft: return "Draft"
        case .sent: return "Sent"
        case .paid: return "Paid"
        }
    }
}

/// One line of `DocumentDetail.items` — `lineTotal` is computed server-side
/// (`quantity * rateInr`), not recomputed here.
struct DocumentItem: Decodable, Equatable, Hashable {
    let itemName: String
    let description: String?
    let hsnSac: String?
    let quantity: Int
    let rateInr: Double
    let lineTotal: Double
}

/// `GET /documents/{id}` / create / update-status response. `subtotal`,
/// `cgstAmount`, `sgstAmount`, and `total` are computed at read time on the
/// backend (never persisted), so they always reflect the current GST rate.
struct DocumentDetail: Decodable, Identifiable, Equatable {
    let id: String
    let documentType: DocumentType
    let documentNumber: String
    let status: DocumentStatus
    let lead: DocumentPartyRef?
    let patient: DocumentPartyRef?
    let items: [DocumentItem]
    let discountInr: Double
    let subtotal: Double
    let cgstAmount: Double
    let sgstAmount: Double
    let total: Double
    let notes: String?
    let validForDays: Int?
    let createdBy: UserRef?
    private let createdAtRaw: String?
    private let updatedAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var updatedAt: Date? { ISO8601.date(from: updatedAtRaw) }

    private enum CodingKeys: String, CodingKey {
        case id, documentType, documentNumber, status, lead, patient, items
        case discountInr, subtotal, cgstAmount, sgstAmount, total, notes, validForDays, createdBy
        case createdAtRaw = "createdAt"
        case updatedAtRaw = "updatedAt"
    }
}

/// `POST /documents`' `items[]` shape.
struct CreateDocumentItemRequest: Encodable {
    let itemName: String
    let description: String?
    let hsnSac: String?
    let quantity: Int
    let rateInr: Double
}

/// `POST /documents` body. Exactly one of `leadId`/`patientId` must be set
/// (422 otherwise) — enforced by `CreateEstimateView`'s picker, which only
/// ever fills in one. There is no `status` field: every document is born
/// `DRAFT` server-side; "save & send" is a follow-up `PATCH`.
struct CreateDocumentRequest: Encodable {
    let documentType: DocumentType
    let leadId: String?
    let patientId: String?
    let items: [CreateDocumentItemRequest]
    let notes: String?
    let discountInr: Double?
    let validForDays: Int?
}

struct UpdateDocumentStatusRequest: Encodable {
    let status: DocumentStatus
}

/// `POST /documents/from-order` body — turns an already-paid `Order` into an
/// invoice. Not wired to any screen yet (no "create invoice from order" flow
/// exists in the Orders screen, which is still a placeholder), but part of
/// the repository's contract since the backend already supports it.
struct FromOrderRequest: Encodable {
    let orderId: String
}

/// `GET /documents/{id}/pdf`'s response — one field, a signed download URL
/// (24h TTL, freshly minted on every call).
struct PdfUrlResponse: Decodable {
    let pdfUrl: String
}

/// The result of `DocumentPartyPickerView` — not a DTO itself, just enough to
/// fill in `CreateDocumentRequest.leadId`/`patientId`.
struct DocumentPartySelection: Identifiable, Equatable {
    let id: String
    let name: String
    let isLead: Bool
}
