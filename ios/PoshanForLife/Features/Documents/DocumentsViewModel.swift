import Foundation

/// Backs `DocumentsListView` — the admin/practitioner browse screen. Unlike
/// `CatalogueViewModel` there's no browse/picker mode split: nothing in this
/// feature needs a "pick an existing document" sheet, and creation
/// (`CreateEstimateView`) owns its own local state the same way
/// `CatalogueItemFormView` does.
@MainActor
final class DocumentsViewModel: ObservableObject {

    private let repository: DocumentsRepository

    @Published var typeFilter: DocumentType? {
        didSet { Task { await load() } }
    }
    @Published var statusFilter: DocumentStatus? {
        didSet { Task { await load() } }
    }
    @Published private(set) var listState: CardState<[DocumentListItem]> = .loading

    init(repository: DocumentsRepository) {
        self.repository = repository
    }

    func load() async {
        listState = .loading
        await performLoad()
    }

    func refresh() async {
        await performLoad()
    }

    private func performLoad() async {
        switch await repository.list(type: typeFilter, status: statusFilter, patientId: nil, leadId: nil) {
        case .success(let items):
            listState = .success(items)
        case .failure(let error):
            listState = .failure(error.message)
        }
    }
}
