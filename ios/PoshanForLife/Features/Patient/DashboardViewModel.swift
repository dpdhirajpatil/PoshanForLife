import Foundation

/// Per-card state, so one card's failure or slowness never masks another's
/// content. Deliberately not a single screen-wide enum.
enum CardState<T> {
    case loading
    case success(T)
    case failure(String)

    var value: T? {
        if case .success(let v) = self { return v }
        return nil
    }

    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
}

@MainActor
final class DashboardViewModel: ObservableObject {

    @Published private(set) var profile: CardState<UserDetail> = .loading
    @Published private(set) var healthRecord: CardState<HealthRecord?> = .loading
    @Published private(set) var programme: CardState<PatientProgramme?> = .loading
    @Published private(set) var invoices: CardState<[DocumentListItem]> = .loading

    private let repository: DashboardRepository

    init(repository: DashboardRepository) {
        self.repository = repository
    }

    /// Backs both the initial load and `.refreshable`. The three patient-scoped
    /// reads run concurrently and each writes its own card as it lands, so a
    /// slow one never holds up the rest.
    ///
    /// Cards are NOT reset to `.loading` on refresh: pull-to-refresh already has
    /// its own spinner, and blanking content the user is looking at to show
    /// skeletons reads as data loss. The first load starts at `.loading` anyway.
    func load() async {
        let profileResult = await repository.currentUser()
        profile = profileResult.cardState

        guard case .success(let user) = profileResult else {
            // Every other call is keyed by the patient id, so without the
            // profile there's nothing to ask for. Say so on each card rather
            // than leaving three spinners running forever.
            var message = "Couldn't load your profile"
            if case .failure(let error) = profileResult { message = error.message }
            healthRecord = .failure(message)
            programme = .failure(message)
            invoices = .failure(message)
            return
        }

        let patientId = user.id
        await withTaskGroup(of: Void.self) { group in
            group.addTask { [weak self] in await self?.loadHealthRecord(patientId) }
            group.addTask { [weak self] in await self?.loadProgramme(patientId) }
            group.addTask { [weak self] in await self?.loadInvoices(patientId) }
        }
    }

    private func loadHealthRecord(_ patientId: String) async {
        healthRecord = (await repository.latestHealthRecord(patientId: patientId)).cardState
    }

    private func loadProgramme(_ patientId: String) async {
        programme = (await repository.activeProgramme(patientId: patientId)).cardState
    }

    private func loadInvoices(_ patientId: String) async {
        invoices = (await repository.unpaidInvoices(patientId: patientId)).cardState
    }

}

private extension Result where Failure == APIError {
    var cardState: CardState<Success> {
        switch self {
        case .success(let value): return .success(value)
        case .failure(let error): return .failure(error.message)
        }
    }
}
