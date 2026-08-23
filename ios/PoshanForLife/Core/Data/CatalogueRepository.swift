import Foundation

protocol CatalogueRepository: AnyObject {
    /// `statusFilter: nil` means "no filter" (admin browse); the
    /// convert-to-patient flow always passes `.published` explicitly.
    func list(type: ServiceType, status: CatalogueStatus?, search: String?) async -> Result<[CatalogueItem], APIError>
    func get(type: ServiceType, id: String) async -> Result<CatalogueItem, APIError>
    func create(type: ServiceType, request: CreateCatalogueItemRequest) async -> Result<CatalogueItem, APIError>
    func update(type: ServiceType, id: String, request: UpdateCatalogueItemRequest) async -> Result<CatalogueItem, APIError>
    func delete(type: ServiceType, id: String) async -> Result<EmptyResponse, APIError>
    func uploadCoverImage(type: ServiceType, fileName: String, contentType: String, data: Data) async -> Result<UploadCoverImageResponse, APIError>
}

final class CatalogueRepositoryImpl: CatalogueRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func list(type: ServiceType, status: CatalogueStatus?, search: String?) async -> Result<[CatalogueItem], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "50")]
        if let status {
            items.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        if let search, !search.trimmingCharacters(in: .whitespaces).isEmpty {
            items.append(URLQueryItem(name: "search", value: search))
        }
        return await client.send(Endpoint(path: "catalogue/\(type.pathSegment)", method: .get, queryItems: items))
    }

    func get(type: ServiceType, id: String) async -> Result<CatalogueItem, APIError> {
        await client.send(Endpoint(path: "catalogue/\(type.pathSegment)/\(id)", method: .get))
    }

    func create(type: ServiceType, request: CreateCatalogueItemRequest) async -> Result<CatalogueItem, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "catalogue/\(type.pathSegment)", method: .post, body: request)
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }

    func update(type: ServiceType, id: String, request: UpdateCatalogueItemRequest) async -> Result<CatalogueItem, APIError> {
        do {
            let endpoint = try Endpoint.json(path: "catalogue/\(type.pathSegment)/\(id)", method: .patch, body: request)
            return await client.send(endpoint)
        } catch {
            return .failure(.decoding)
        }
    }

    func delete(type: ServiceType, id: String) async -> Result<EmptyResponse, APIError> {
        await client.send(Endpoint(path: "catalogue/\(type.pathSegment)/\(id)", method: .delete))
    }

    func uploadCoverImage(type: ServiceType, fileName: String, contentType: String, data: Data) async -> Result<UploadCoverImageResponse, APIError> {
        await client.send(
            Endpoint.multipart(
                path: "catalogue/\(type.pathSegment)/upload",
                fields: [:],
                fileField: "file",
                fileName: fileName,
                fileData: data,
                fileContentType: contentType
            )
        )
    }
}
