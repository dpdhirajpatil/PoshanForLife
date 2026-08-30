import Foundation

protocol DocumentsRepository: AnyObject {
    func list(type: DocumentType?, status: DocumentStatus?, patientId: String?, leadId: String?) async -> Result<[DocumentListItem], APIError>
    func get(id: String) async -> Result<DocumentDetail, APIError>
    func create(_ request: CreateDocumentRequest) async -> Result<DocumentDetail, APIError>
    func updateStatus(id: String, status: DocumentStatus) async -> Result<DocumentDetail, APIError>
    func pdfUrl(id: String) async -> Result<PdfUrlResponse, APIError>
    func createFromOrder(orderId: String) async -> Result<DocumentDetail, APIError>
}

/// `GET /documents` is scoped server-side exactly like `/leads` and
/// `/patients`: a DOCTOR caller sees only documents tied to their own
/// leads/patients, an ADMIN sees all. A PATIENT caller additionally has
/// `type` force-overridden to `invoice` and `patientId` force-set to their
/// own id regardless of what's sent — see `DashboardRepository.unpaidInvoices`
/// for the patient-facing read path this repository doesn't need to special-case.
final class DocumentsRepositoryImpl: DocumentsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func list(type: DocumentType?, status: DocumentStatus?, patientId: String?, leadId: String?) async -> Result<[DocumentListItem], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "50")]
        if let type {
            items.append(URLQueryItem(name: "type", value: type.rawValue))
        }
        if let status {
            items.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        if let patientId {
            items.append(URLQueryItem(name: "patientId", value: patientId))
        }
        if let leadId {
            items.append(URLQueryItem(name: "leadId", value: leadId))
        }
        return await client.send(Endpoint(path: "documents", method: .get, queryItems: items))
    }

    func get(id: String) async -> Result<DocumentDetail, APIError> {
        await client.send(Endpoint(path: "documents/\(id)", method: .get))
    }

    func create(_ request: CreateDocumentRequest) async -> Result<DocumentDetail, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "documents", method: .post, body: request)
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }

    func updateStatus(id: String, status: DocumentStatus) async -> Result<DocumentDetail, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "documents/\(id)", method: .patch, body: UpdateDocumentStatusRequest(status: status))
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }

    func pdfUrl(id: String) async -> Result<PdfUrlResponse, APIError> {
        await client.send(Endpoint(path: "documents/\(id)/pdf", method: .get))
    }

    func createFromOrder(orderId: String) async -> Result<DocumentDetail, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "documents/from-order", method: .post, body: FromOrderRequest(orderId: orderId))
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }
}
