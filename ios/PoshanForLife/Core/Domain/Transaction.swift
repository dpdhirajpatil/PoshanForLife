import Foundation

/// `TransactionType` on the wire — lowercase.
enum TransactionType: String, Decodable, CaseIterable, Identifiable, Hashable {
    case activation, deactivation, refund

    var id: String { rawValue }

    var label: String {
        switch self {
        case .activation: return "Activation"
        case .deactivation: return "Deactivation"
        case .refund: return "Refund"
        }
    }
}

/// `PaymentType` on the wire — lowercase. `credit` is reserved for a future
/// credit-wallet feature; every transaction today is `offline` or `online`.
enum PaymentType: String, Decodable, CaseIterable, Identifiable, Hashable {
    case offline, online, credit

    var id: String { rawValue }

    var label: String {
        switch self {
        case .offline: return "Offline"
        case .online: return "Online"
        case .credit: return "Credit"
        }
    }
}

/// Row shape for `GET /transactions`.
struct TransactionListItem: Decodable, Equatable, Identifiable, Hashable {
    let id: String
    let transactionId: String
    let invoiceNumber: String
    let transactionType: TransactionType
    let paymentType: PaymentType
    let patient: UserRef
    let catalogueType: ServiceType?
    let serviceName: String?
    let amountInr: Double
    let creditCharged: Double
    let createdBy: UserRef?
    private let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }
    var displayServiceName: String { serviceName ?? "Service" }

    private enum CodingKeys: String, CodingKey {
        case id, transactionId, invoiceNumber, transactionType, paymentType, patient
        case catalogueType, serviceName, amountInr, creditCharged, createdBy
        case createdAtRaw = "createdAt"
    }
}

/// `TransactionDetail.order` — a thin wrapper; the interesting part is the
/// same `OrderProgrammeRef` Order detail uses.
struct TransactionOrderRef: Decodable, Equatable {
    let id: String
    let patientProgramme: OrderProgrammeRef?
}

/// Full record for `GET /transactions/{id}`. Not currently pushed to from any
/// screen (IOS-17's Output list has no transaction detail view), but the
/// repository exposes it for parity with the documented API surface.
struct TransactionDetail: Decodable, Equatable, Identifiable {
    let id: String
    let transactionId: String
    let invoiceNumber: String
    let transactionType: TransactionType
    let paymentType: PaymentType
    let priceInr: Double
    let discountInr: Double
    let amountInr: Double
    let creditCharged: Double
    let source: String?
    let paymentGatewayRef: String?
    let notes: String?
    let patient: PatientContactRef
    let createdBy: UserRef?
    let order: TransactionOrderRef?
    private let createdAtRaw: String?

    var createdAt: Date? { ISO8601.date(from: createdAtRaw) }

    private enum CodingKeys: String, CodingKey {
        case id, transactionId, invoiceNumber, transactionType, paymentType
        case priceInr, discountInr, amountInr, creditCharged, source, paymentGatewayRef, notes
        case patient, createdBy, order
        case createdAtRaw = "createdAt"
    }
}

/// `GET /transactions`'s summary block — computed server-side over every
/// transaction matching the current filters, not just the visible page.
/// `totalCreditConsumed` is a rupee value (absolute sum of `creditCharged`),
/// not a session/programme usage count — there's no credit-wallet feature
/// yet, so it's effectively always 0 today.
struct TransactionTotals: Decodable, Equatable {
    let totalTransactionValue: Double
    let totalCreditConsumed: Double
}

/// `GET /transactions`'s `data` payload — summary travels inline with the
/// page of transactions, same shape `LeadListResponse` uses for its own
/// summary stats.
struct TransactionListResponse: Decodable {
    let transactions: [TransactionListItem]
    let summary: TransactionTotals
}
