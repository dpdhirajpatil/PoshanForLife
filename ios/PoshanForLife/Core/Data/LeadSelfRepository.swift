import Foundation

/// Self-service calls a LEAD-role account makes about its own record —
/// distinct from `LeadsRepository`, which is the staff-facing CRM (list,
/// convert, log activity on *other people's* leads, admin+doctor scoped).
/// `@LeadOnly` server-side: a PATIENT/DOCTOR/ADMIN bearer token gets a 403.
protocol LeadSelfRepository: AnyObject {
    func requestConsultation(preferredContactTime: String?, message: String?) async -> Result<Void, APIError>
}

/// `POST /leads/me/request-consultation` body — both fields optional.
struct RequestConsultationRequest: Encodable {
    let preferredContactTime: String?
    let message: String?
}

final class LeadSelfRepositoryImpl: LeadSelfRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func requestConsultation(preferredContactTime: String?, message: String?) async -> Result<Void, APIError> {
        do {
            let endpoint = try Endpoint.json(
                path: "leads/me/request-consultation",
                method: .post,
                body: RequestConsultationRequest(preferredContactTime: preferredContactTime, message: message)
            )
            // The response body is just `{"submitted": true}` — not worth a
            // dedicated DTO; `EmptyResponse`'s zero-property decode ignores
            // whatever keys are actually there.
            let result: Result<EmptyResponse, APIError> = await client.send(endpoint)
            return result.map { _ in () }
        } catch {
            return .failure(.decoding)
        }
    }
}
