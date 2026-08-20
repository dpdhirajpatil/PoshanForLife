import Foundation

protocol BadgesRepository: AnyObject {
    func badges(patientId: String) async -> Result<[PatientBadgeStatus], APIError>
}

/// Patient-facing read of the badge catalog — `GET /patients/{id}/badges`
/// returns every badge an admin has defined, annotated per-patient with
/// earned/earnedAt, so locked badges can render grayed-out rather than
/// being omitted from the response.
final class BadgesRepositoryImpl: BadgesRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func badges(patientId: String) async -> Result<[PatientBadgeStatus], APIError> {
        await client.send(Endpoint(path: "patients/\(patientId)/badges", method: .get))
    }
}
