import Foundation

/// Backs `CatalogueView` in both its browse and picker modes. The form
/// (`CatalogueItemFormView`) owns its own local state instead of routing
/// through this class — creating/editing one item isn't a second mode of the
/// list screen, it's a separate screen that happens to reload this one's
/// list on the way back, same relationship `ConvertToPatientView` has to
/// `LeadsViewModel`.
@MainActor
final class CatalogueViewModel: ObservableObject {

    enum Mode: Equatable {
        /// Plain browse (practitioners) or browse+CRUD (admins) — every
        /// status is visible, with a filter to narrow it.
        case browse
        /// Assigning a service to a patient — only published items are
        /// offered, and the status filter UI doesn't appear at all. Matches
        /// Android's `CatalogueScreen(pickerMode = true)` forcing the same.
        case picker
    }

    enum ActionState: Equatable {
        case idle, inFlight
        case failure(String)
    }

    let mode: Mode
    private let repository: CatalogueRepository

    @Published var serviceType: ServiceType {
        didSet { if oldValue != serviceType { Task { await load() } } }
    }
    @Published var statusFilter: CatalogueStatus? {
        didSet { Task { await load() } }
    }
    @Published var searchQuery: String = "" {
        didSet { scheduleSearch() }
    }
    @Published private(set) var listState: CardState<[CatalogueItem]> = .loading
    @Published private(set) var deleteState: ActionState = .idle

    private var searchTask: Task<Void, Never>?

    init(repository: CatalogueRepository, mode: Mode, initialType: ServiceType = .programme) {
        self.repository = repository
        self.mode = mode
        self.serviceType = initialType
        self.statusFilter = mode == .picker ? .published : nil
    }

    func load() async {
        listState = .loading
        await performLoad()
    }

    func refresh() async {
        await performLoad()
    }

    /// Debounced the same 300ms as `LeadsViewModel`'s search — cancels any
    /// in-flight query so a slow response to a stale keystroke can't land
    /// after, and overwrite, a newer one.
    private func scheduleSearch() {
        searchTask?.cancel()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await self?.performLoad()
        }
    }

    private func performLoad() async {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        switch await repository.list(type: serviceType, status: statusFilter, search: query.isEmpty ? nil : query) {
        case .success(let items):
            listState = .success(items)
        case .failure(let error):
            listState = .failure(error.message)
        }
    }

    // MARK: - Admin

    /// Only ever called from `CatalogueView`'s admin swipe-action — the
    /// backend itself refuses this for an item with active assignments
    /// unless it's already archived, surfaced back through `deleteState`.
    func delete(_ item: CatalogueItem) async -> Bool {
        deleteState = .inFlight
        switch await repository.delete(type: serviceType, id: item.id) {
        case .success:
            deleteState = .idle
            await refresh()
            return true
        case .failure(let error):
            deleteState = .failure(error.message)
            return false
        }
    }
}
