import Foundation

/// `OrderStatus` on the wire — lowercase, same convention as every other
/// status enum in this codebase.
enum OrderStatus: String, Codable, CaseIterable, Identifiable, Hashable {
    case active, completed, deactivated

    var id: String { rawValue }

    var label: String {
        switch self {
        case .active: return "Active"
        case .completed: return "Completed"
        case .deactivated: return "Deactivated"
        }
    }
}

/// `PaymentStatus` on the wire — lowercase. There is no "partial" state: an
/// order is `pending` until money is recorded, then `paid`, or `unpaid` if
/// it was explicitly written off.
enum PaymentStatus: String, Codable, CaseIterable, Identifiable, Hashable {
    case paid, unpaid, pending

    var id: String { rawValue }

    var label: String {
        switch self {
        case .paid: return "Paid"
        case .unpaid: return "Unpaid"
        case .pending: return "Pending"
        }
    }
}

/// The richer patient reference Order/Transaction detail responses carry —
/// `UserRef` (id/name only) is what list rows and `createdBy` use instead.
struct PatientContactRef: Decodable, Equatable, Hashable {
    let id: String
    let name: String
    let email: String?
    let phone: String?
}

/// "Full patientProgramme context" — the same shape backs both
/// `OrderDetail.patientProgramme` and `TransactionDetail.order.patientProgramme`
/// (the backend's `OrderProgrammeDto` is shared between the two). Distinct
/// from `PatientProgramme` in PatientModels.swift: that one is the patient's
/// own assignment-list row shape (has `priceInr`/`notes`, no `assignedBy`);
/// this one is what Orders/Transactions actually receive.
struct OrderProgrammeRef: Decodable, Equatable, Hashable {
    let id: String
    let serviceType: ServiceType?
    let catalogueItem: ServiceRef?
    let startDate: String?
    let endDate: String?
    let status: AssignmentStatus?
    let assignedBy: UserRef?
    let assignedDoctor: UserRef?

    var displayName: String { catalogueItem?.name ?? "Service" }
}

/// One entry of `OrderDetail.transactions` — a trimmed-down
/// `TransactionListItem`, only what the order detail screen needs inline.
struct OrderTransactionRef: Decodable, Equatable, Hashable, Identifiable {
    let id: String
    let transactionId: String
    let invoiceNumber: String
    let transactionType: TransactionType
    let paymentType: PaymentType
    let amountInr: Double
    private let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }

    private enum CodingKeys: String, CodingKey {
        case id, transactionId, invoiceNumber, transactionType, paymentType, amountInr
        case createdAtRaw = "createdAt"
    }
}

/// Row shape for `GET /orders`.
struct OrderListItem: Decodable, Equatable, Identifiable, Hashable {
    let id: String
    let patient: UserRef
    let serviceType: ServiceType?
    let serviceName: String?
    let amountInr: Double
    let status: OrderStatus
    let paymentStatus: PaymentStatus
    private let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var displayServiceName: String { serviceName ?? "Service" }

    private enum CodingKeys: String, CodingKey {
        case id, patient, serviceType, serviceName, amountInr, status, paymentStatus
        case createdAtRaw = "createdAt"
    }
}

/// Full record for `GET /orders/{id}`.
struct OrderDetail: Decodable, Equatable, Identifiable {
    let id: String
    let amountInr: Double
    let status: OrderStatus
    let paymentStatus: PaymentStatus
    let notes: String?
    let patient: PatientContactRef
    let patientProgramme: OrderProgrammeRef?
    let transactions: [OrderTransactionRef]
    let createdBy: UserRef?
    private let createdAtRaw: String?
    private let updatedAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var updatedAt: Date? { ISO8601.date(from: updatedAtRaw) }

    private enum CodingKeys: String, CodingKey {
        case id, amountInr, status, paymentStatus, notes, patient, patientProgramme, transactions, createdBy
        case createdAtRaw = "createdAt"
        case updatedAtRaw = "updatedAt"
    }
}

/// `PATCH /orders/{id}` body — partial update, every field optional. Marking
/// an order paid is just `UpdateOrderRequest(paymentStatus: .paid, status: nil, notes: nil)`;
/// the backend creates the activation transaction (and its invoice) as a
/// side effect automatically, but only when the order doesn't already have
/// one — most orders already do, generated at assignment time, so re-marking
/// an already-transacted order paid is a no-op beyond the status flip.
struct UpdateOrderRequest: Encodable {
    let paymentStatus: PaymentStatus?
    let status: OrderStatus?
    let notes: String?
}
