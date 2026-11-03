import Foundation

/// Self-service calls for a self-signup account (role LEAD), under
/// `/leads/me`. Kept apart from `LeadsRepository`, which is the staff-facing CRM.
protocol LeadSelfRepository: AnyObject {
    /// Appends a NOTE to the caller's own Lead record for staff to follow up.
    func requestConsultation(preferredContactTime: String?, message: String?) async -> Result<Void, APIError>
    func streak() async -> Result<LeadStreak, APIError>
    /// Idempotent per day server-side; returns the updated streak.
    func checkIn() async -> Result<LeadStreak, APIError>
    /// Streak badges only (the other criteria need a programme a lead doesn't have).
    func badges() async -> Result<[PatientBadgeStatus], APIError>
}

/// `percentComplete` treats a 7-day streak as full, for the progress ring.
struct LeadStreak: Decodable, Equatable {
    let currentStreak: Int
    let longestStreak: Int
    /// `yyyy-MM-dd`, in the server's calendar.
    let lastLoggedDate: String?
    let percentComplete: Int
}

private struct RequestConsultationBody: Encodable {
    let preferredContactTime: String?
    let message: String?
}

final class LeadSelfRepositoryImpl: LeadSelfRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func requestConsultation(preferredContactTime: String?, message: String?) async -> Result<Void, APIError> {
        guard let endpoint = try? Endpoint.json(
            path: "leads/me/request-consultation",
            method: .post,
            body: RequestConsultationBody(preferredContactTime: preferredContactTime, message: message)
        ) else {
            return .failure(.transport(code: "ENCODING_ERROR", message: "Could not build the request"))
        }
        let result: Result<[String: Bool], APIError> = await client.send(endpoint)
        return result.map { _ in () }
    }

    func streak() async -> Result<LeadStreak, APIError> {
        await client.send(Endpoint(path: "leads/me/streak", method: .get))
    }

    func checkIn() async -> Result<LeadStreak, APIError> {
        await client.send(Endpoint(path: "leads/me/streak/check-in", method: .patch))
    }

    func badges() async -> Result<[PatientBadgeStatus], APIError> {
        await client.send(Endpoint(path: "leads/me/badges", method: .get))
    }
}
