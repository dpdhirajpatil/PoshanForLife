import Foundation

protocol LeadsRepository: AnyObject {
    func list(stage: LeadStage?, search: String?) async -> Result<LeadListResponse, APIError>
    func detail(id: String) async -> Result<LeadDetail, APIError>
    func updateStage(id: String, stage: LeadStage) async -> Result<LeadDetail, APIError>
    func addActivity(id: String, type: LeadActivityType, description: String) async -> Result<LeadActivity, APIError>
    func scheduleFollowup(id: String, at date: Date, message: String?) async -> Result<LeadDetail, APIError>
    func convert(id: String, request: ConvertLeadRequest) async -> Result<ConvertLeadResponse, APIError>
}

/// `GET /leads` is scoped server-side exactly like `/patients` — DOCTOR gets
/// their own assigned leads, ADMIN gets every lead — so there's no
/// role/practitionerId param to pass from the client (verified against
/// `LeadService`).
final class LeadsRepositoryImpl: LeadsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// One large page (`limit=100`) rather than paged loading, same call as
    /// `PatientsRepository.list` — a practitioner's own pipeline (or even
    /// the admin's whole one) is bounded in practice.
    func list(stage: LeadStage?, search: String?) async -> Result<LeadListResponse, APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "100")]
        if let stage {
            items.append(URLQueryItem(name: "stage", value: stage.rawValue))
        }
        if let search, !search.trimmingCharacters(in: .whitespaces).isEmpty {
            items.append(URLQueryItem(name: "search", value: search))
        }
        return await client.send(Endpoint(path: "leads", method: .get, queryItems: items))
    }

    func detail(id: String) async -> Result<LeadDetail, APIError> {
        await client.send(Endpoint(path: "leads/\(id)", method: .get))
    }

    /// Sends only `stage` — every other `UpdateLeadRequest` field is absent,
    /// and the backend leaves an absent field untouched, so this can never
    /// clobber the rest of the record. The backend auto-logs a
    /// `stage_change` activity, so the caller should reload the detail
    /// (fresh `activities`) rather than patch the returned value in place.
    func updateStage(id: String, stage: LeadStage) async -> Result<LeadDetail, APIError> {
        guard let endpoint = try? Endpoint.json(
            path: "leads/\(id)",
            method: .patch,
            body: UpdateLeadStageRequest(stage: stage.rawValue)
        ) else {
            return .failure(.decoding)
        }
        return await client.send(endpoint)
    }

    func addActivity(id: String, type: LeadActivityType, description: String) async -> Result<LeadActivity, APIError> {
        guard let endpoint = try? Endpoint.json(
            path: "leads/\(id)/activities",
            method: .post,
            body: CreateLeadActivityRequest(activityType: type.rawValue, description: description)
        ) else {
            return .failure(.decoding)
        }
        return await client.send(endpoint)
    }

    func scheduleFollowup(id: String, at date: Date, message: String?) async -> Result<LeadDetail, APIError> {
        guard let endpoint = try? Endpoint.json(
            path: "leads/\(id)/schedule-followup",
            method: .post,
            body: ScheduleFollowupRequest(followupAt: LeadDateFormat.offsetDateTime(date), message: message)
        ) else {
            return .failure(.decoding)
        }
        return await client.send(endpoint)
    }

    func convert(id: String, request: ConvertLeadRequest) async -> Result<ConvertLeadResponse, APIError> {
        guard let endpoint = try? Endpoint.json(path: "leads/\(id)/convert", method: .post, body: request) else {
            return .failure(.decoding)
        }
        return await client.send(endpoint)
    }
}
