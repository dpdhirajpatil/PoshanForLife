import Foundation

protocol TransactionsRepository: AnyObject {
    /// `practitionerId` maps to the backend's `userId` param — ADMIN-only;
    /// a DOCTOR caller is always scoped to their own patients regardless of
    /// what's passed, so callers only need to send it when `isAdmin` is true.
    func list(practitionerId: String?, catalogue: ServiceType?, paymentType: PaymentType?, search: String?, dateFrom: String?, dateTo: String?) async -> Result<TransactionListResponse, APIError>
    func get(id: String) async -> Result<TransactionDetail, APIError>
}

/// `GET /transactions` is scoped server-side exactly like `/orders` — a
/// DOCTOR sees only transactions for their own linked patients, an ADMIN
/// sees all.
final class TransactionsRepositoryImpl: TransactionsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func list(practitionerId: String?, catalogue: ServiceType?, paymentType: PaymentType?, search: String?, dateFrom: String?, dateTo: String?) async -> Result<TransactionListResponse, APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "50")]
        if let practitionerId {
            items.append(URLQueryItem(name: "userId", value: practitionerId))
        }
        if let catalogue {
            items.append(URLQueryItem(name: "catalogue", value: catalogue.rawValue))
        }
        if let paymentType {
            items.append(URLQueryItem(name: "paymentType", value: paymentType.rawValue))
        }
        if let search, !search.trimmingCharacters(in: .whitespaces).isEmpty {
            items.append(URLQueryItem(name: "search", value: search))
        }
        if let dateFrom {
            items.append(URLQueryItem(name: "dateFrom", value: dateFrom))
        }
        if let dateTo {
            items.append(URLQueryItem(name: "dateTo", value: dateTo))
        }
        return await client.send(Endpoint(path: "transactions", method: .get, queryItems: items))
    }

    func get(id: String) async -> Result<TransactionDetail, APIError> {
        await client.send(Endpoint(path: "transactions/\(id)", method: .get))
    }
}
