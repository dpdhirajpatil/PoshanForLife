import Foundation

protocol CatalogueRepository: AnyObject {
    func list(type: ServiceType, search: String?) async -> Result<[CatalogueItem], APIError>
}

/// Just the one read the convert-to-patient flow needs — see
/// `CatalogueItem`'s doc comment for why this isn't the full IOS-15
/// catalogue feature.
final class CatalogueRepositoryImpl: CatalogueRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// `published` only — a lead shouldn't be assignable to a draft or
    /// archived item. The URL segment is the plural form (`fromPathSegment`
    /// on the backend); the JSON wire value for `serviceType` elsewhere is
    /// the singular label `ServiceType.rawValue` already matches.
    func list(type: ServiceType, search: String?) async -> Result<[CatalogueItem], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "status", value: "published"), URLQueryItem(name: "limit", value: "50")]
        if let search, !search.trimmingCharacters(in: .whitespaces).isEmpty {
            items.append(URLQueryItem(name: "search", value: search))
        }
        return await client.send(Endpoint(path: "catalogue/\(type.pathSegment)", method: .get, queryItems: items))
    }
}

private extension ServiceType {
    var pathSegment: String {
        switch self {
        case .programme: return "programmes"
        case .session: return "sessions"
        case .challenge: return "challenges"
        }
    }
}
