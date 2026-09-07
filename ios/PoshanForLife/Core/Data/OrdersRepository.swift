import Foundation

protocol OrdersRepository: AnyObject {
    func list(status: OrderStatus?, paymentStatus: PaymentStatus?, search: String?, dateFrom: String?, dateTo: String?) async -> Result<[OrderListItem], APIError>
    func get(id: String) async -> Result<OrderDetail, APIError>
    func update(id: String, request: UpdateOrderRequest) async -> Result<OrderDetail, APIError>
}

/// `GET /orders` is scoped server-side exactly like `/leads`/`/patients` — a
/// DOCTOR sees only orders for their own linked patients, an ADMIN sees all.
final class OrdersRepositoryImpl: OrdersRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func list(status: OrderStatus?, paymentStatus: PaymentStatus?, search: String?, dateFrom: String?, dateTo: String?) async -> Result<[OrderListItem], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "50")]
        if let status {
            items.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        if let paymentStatus {
            items.append(URLQueryItem(name: "paymentStatus", value: paymentStatus.rawValue))
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
        return await client.send(Endpoint(path: "orders", method: .get, queryItems: items))
    }

    func get(id: String) async -> Result<OrderDetail, APIError> {
        await client.send(Endpoint(path: "orders/\(id)", method: .get))
    }

    func update(id: String, request: UpdateOrderRequest) async -> Result<OrderDetail, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "orders/\(id)", method: .patch, body: request)
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }
}
