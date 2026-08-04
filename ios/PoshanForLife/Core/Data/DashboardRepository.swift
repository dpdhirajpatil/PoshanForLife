import Foundation

/// The four reads behind the patient dashboard. Each is independent so the
/// ViewModel can surface one card's failure without touching the others.
protocol DashboardRepository: AnyObject {
    func currentUser() async -> Result<UserDetail, APIError>
    func latestHealthRecord(patientId: String) async -> Result<HealthRecord?, APIError>
    func activeProgramme(patientId: String) async -> Result<PatientProgramme?, APIError>
    func unpaidInvoices(patientId: String) async -> Result<[DocumentListItem], APIError>
}

final class DashboardRepositoryImpl: DashboardRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func currentUser() async -> Result<UserDetail, APIError> {
        await client.send(Endpoint(path: "users/me", method: .get))
    }

    /// The backend returns records **chronologically ascending**, and `limit`
    /// takes the most recent N while keeping that order — so the newest record
    /// is the LAST element, not the first. With `limit=1` the two coincide, but
    /// taking `.last` keeps this correct if the limit ever changes.
    func latestHealthRecord(patientId: String) async -> Result<HealthRecord?, APIError> {
        let result: Result<[HealthRecord], APIError> = await client.send(
            Endpoint(
                path: "health-records/\(patientId)",
                method: .get,
                queryItems: [URLQueryItem(name: "limit", value: "1")]
            )
        )
        return result.map { $0.last }
    }

    /// `GET patients/{id}/programmes` takes no `status` or `limit` parameters —
    /// passing them would be silently ignored and return everything — so the
    /// active one is picked here. Same approach as the Android client.
    func activeProgramme(patientId: String) async -> Result<PatientProgramme?, APIError> {
        let result: Result<[PatientProgramme], APIError> = await client.send(
            Endpoint(path: "patients/\(patientId)/programmes", method: .get)
        )
        return result.map { programmes in
            programmes.first { $0.status == PatientProgramme.activeStatus }
        }
    }

    /// For a PATIENT caller the backend overrides `patientId` with the caller's
    /// own id and forces `type=invoice` regardless of what's sent, so this is
    /// scoped server-side too — the parameters just keep the intent explicit
    /// and keep the call correct if a non-patient role ever reuses it.
    func unpaidInvoices(patientId: String) async -> Result<[DocumentListItem], APIError> {
        await client.send(
            Endpoint(
                path: "documents",
                method: .get,
                queryItems: [
                    URLQueryItem(name: "patientId", value: patientId),
                    URLQueryItem(name: "type", value: "invoice"),
                    URLQueryItem(name: "status", value: DocumentListItem.unpaidStatus),
                    URLQueryItem(name: "limit", value: "10"),
                ]
            )
        )
    }
}
