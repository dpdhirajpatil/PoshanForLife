import Foundation

protocol ProgrammesRepository: AnyObject {
    func assignments(patientId: String) async -> Result<[PatientProgramme], APIError>
    func challengeProgress(patientId: String, ppId: String) async -> Result<ChallengeProgress, APIError>
    func checkIn(patientId: String, ppId: String) async -> Result<ChallengeProgress, APIError>
}

/// Patient-facing reads of `patient_programmes` — the assignments a
/// practitioner has made, plus challenge check-in progress.
///
/// **What this endpoint does not give you:** `catalogueItem` is a
/// `ServiceRefDto` (id, name, serviceCode, duration) with **no description**.
/// The full description lives on the catalogue item, and
/// `/api/v1/catalogue/{type}` is class-level `@AdminOrDoctor` — a PATIENT
/// caller gets `403 INSUFFICIENT_ROLE`, verified live. So the detail screen
/// shows the assignment's own facts and never a service blurb.
///
/// Access is scoped server-side: `PatientProgrammeService` force-scopes a
/// PATIENT caller to their own record and answers **404, not 403**, for anyone
/// else's id — the same not-found-rather-than-forbidden pattern as reports, so
/// probing ids can't confirm that a patient exists.
final class ProgrammesRepositoryImpl: ProgrammesRepository {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    /// Ordered `createdAt` descending by the backend — most recently assigned
    /// first, which is not the same as date order. The view model re-sorts.
    func assignments(patientId: String) async -> Result<[PatientProgramme], APIError> {
        await client.send(Endpoint(path: "patients/\(patientId)/programmes", method: .get))
    }

    /// Challenge assignments only. Calling this for a programme or session is a
    /// **404**, not an empty body — `findChallengeAssignment` rejects any other
    /// `serviceType` — so callers must check the type first.
    ///
    /// A GET creates the progress row on first read, so a never-checked-in
    /// challenge returns zeros rather than 404.
    func challengeProgress(patientId: String, ppId: String) async -> Result<ChallengeProgress, APIError> {
        await client.send(
            Endpoint(path: "patients/\(patientId)/programmes/\(ppId)/progress", method: .get)
        )
    }

    /// Idempotent for the calendar day: checking in twice leaves the streak
    /// untouched, so the button doesn't need to guard against a double tap for
    /// correctness (only for the spinner).
    func checkIn(patientId: String, ppId: String) async -> Result<ChallengeProgress, APIError> {
        let body = try? JSONEncoder().encode(CheckInRequest(checkedInToday: true))
        return await client.send(
            Endpoint(
                path: "patients/\(patientId)/programmes/\(ppId)/progress",
                method: .patch,
                body: body
            )
        )
    }
}
