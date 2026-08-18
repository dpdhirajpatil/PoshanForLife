import Foundation

protocol PatientsRepository: AnyObject {
    func list(search: String?) async -> Result<[PatientSummary], APIError>
    func detail(id: String) async -> Result<PatientDetail, APIError>
    func updateNotes(id: String, doctorNotes: String) async -> Result<PatientDetail, APIError>
}

/// `GET /patients` is DOCTOR-scoped server-side — `PatientService.list` hard-
/// limits a DOCTOR caller to their own assigned patients no matter what's
/// asked for, so there's no client-side filtering needed to get that right
/// (verified live). `GET /patients/{id}` for a patient NOT assigned to the
/// caller is a **403** (`AccessDeniedException`), unlike Reports/Programmes'
/// 404-for-privacy convention — moot here since navigation only ever reaches a
/// detail screen through this same scoped list.
final class PatientsRepositoryImpl: PatientsRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// The backend paginates (`page`/`limit`, default `limit=10`) and supports
    /// a real server-side `search`. This asks for one large page (`limit=200`)
    /// rather than wiring up paged loading — a practitioner's own caseload is
    /// bounded in practice, and every other list screen in this app (Reports,
    /// Programmes) already assumes a single fetch is enough.
    func list(search: String?) async -> Result<[PatientSummary], APIError> {
        var items: [URLQueryItem] = [URLQueryItem(name: "limit", value: "200")]
        if let search, !search.trimmingCharacters(in: .whitespaces).isEmpty {
            items.append(URLQueryItem(name: "search", value: search))
        }
        return await client.send(Endpoint(path: "patients", method: .get, queryItems: items))
    }

    func detail(id: String) async -> Result<PatientDetail, APIError> {
        await client.send(Endpoint(path: "patients/\(id)", method: .get))
    }

    /// Sends only `doctorNotes` — every other `UpdatePatientRequest` field is
    /// absent, and the backend leaves an absent field untouched (verified
    /// live), so this can never clobber the rest of the profile.
    func updateNotes(id: String, doctorNotes: String) async -> Result<PatientDetail, APIError> {
        guard let endpoint = try? Endpoint.json(
            path: "patients/\(id)",
            method: .patch,
            body: UpdateDoctorNotesRequest(doctorNotes: doctorNotes)
        ) else {
            return .failure(.decoding)
        }
        return await client.send(endpoint)
    }
}

private struct UpdateDoctorNotesRequest: Encodable {
    let doctorNotes: String
}
