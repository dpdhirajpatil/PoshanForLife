import Foundation

/// Backs both `LeadListView` and `LeadDetailView` — one class, mode decided
/// by whether a `leadId` was given, same shape as `PatientManagementViewModel`.
@MainActor
final class LeadsViewModel: ObservableObject {

    enum ActionState: Equatable {
        case idle, inFlight
        case failure(String)
    }

    let leadId: String?

    // MARK: List

    @Published private(set) var listState: CardState<[LeadListItem]> = .loading
    @Published private(set) var summary: LeadSummary?
    @Published var searchQuery: String = "" {
        didSet { scheduleSearch() }
    }
    @Published var stageFilter: LeadStage? {
        didSet { Task { await performListLoad() } }
    }

    // MARK: Detail

    @Published private(set) var detailState: CardState<LeadDetail> = .loading
    @Published private(set) var stageChangeState: ActionState = .idle
    @Published private(set) var activityState: ActionState = .idle
    @Published private(set) var followupState: ActionState = .idle

    private let repository: LeadsRepository
    private var searchTask: Task<Void, Never>?

    init(leadId: String? = nil, repository: LeadsRepository) {
        self.leadId = leadId
        self.repository = repository
    }

    // MARK: - List

    func loadList() async {
        listState = .loading
        await performListLoad()
    }

    func refreshList() async {
        await performListLoad()
    }

    /// Debounced: cancels any in-flight search so a slow response to a stale
    /// keystroke can't land after — and overwrite — a newer one. Same 300ms
    /// as `PatientManagementViewModel`/Android's lead search.
    private func scheduleSearch() {
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await self?.performListLoad()
        }
    }

    private func performListLoad() async {
        switch await repository.list(stage: stageFilter, search: searchQuery) {
        case .success(let response):
            listState = .success(response.leads)
            summary = response.summary
        case .failure(let error):
            listState = .failure(error.message)
        }
    }

    // MARK: - Detail

    func loadDetail() async {
        guard let leadId else { return }
        detailState = .loading
        detailState = (await repository.detail(id: leadId)).cardState
    }

    /// Reloads the detail on success rather than patching `stage` in place —
    /// the backend auto-logs a `stage_change` activity, and the timeline
    /// should reflect it without a second round trip.
    func changeStage(to stage: LeadStage) async {
        guard let leadId else { return }
        stageChangeState = .inFlight
        switch await repository.updateStage(id: leadId, stage: stage) {
        case .success(let detail):
            detailState = .success(detail)
            stageChangeState = .idle
        case .failure(let error):
            stageChangeState = .failure(error.message)
        }
    }

    func logActivity(type: LeadActivityType, description: String) async -> Bool {
        guard let leadId else { return false }
        activityState = .inFlight
        switch await repository.addActivity(id: leadId, type: type, description: description) {
        case .success:
            activityState = .idle
            await loadDetail()
            return true
        case .failure(let error):
            activityState = .failure(error.message)
            return false
        }
    }

    func scheduleFollowup(at date: Date, message: String?) async -> Bool {
        guard let leadId else { return false }
        followupState = .inFlight
        switch await repository.scheduleFollowup(id: leadId, at: date, message: message) {
        case .success(let detail):
            detailState = .success(detail)
            followupState = .idle
            return true
        case .failure(let error):
            followupState = .failure(error.message)
            return false
        }
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
