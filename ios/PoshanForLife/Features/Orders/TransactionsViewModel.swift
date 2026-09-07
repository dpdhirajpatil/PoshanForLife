import Foundation

/// Backs `TransactionsView`. Reads are scoped server-side exactly like
/// Orders — a DOCTOR sees only transactions for their own linked patients
/// regardless of `practitionerId` (the backend silently ignores that filter
/// for a DOCTOR caller, since they're already scoped to themselves), an
/// ADMIN sees all and can additionally filter by practitioner.
@MainActor
final class TransactionsViewModel: ObservableObject {

    private let repository: TransactionsRepository
    let isAdmin: Bool

    @Published var practitionerId: String? {
        didSet { Task { await load() } }
    }
    @Published var catalogueFilter: ServiceType? {
        didSet { Task { await load() } }
    }
    @Published var paymentTypeFilter: PaymentType? {
        didSet { Task { await load() } }
    }
    @Published var dateFrom: Date? {
        didSet { Task { await load() } }
    }
    @Published var dateTo: Date? {
        didSet { Task { await load() } }
    }
    @Published private(set) var listState: CardState<TransactionListResponse> = .loading

    init(repository: TransactionsRepository, isAdmin: Bool) {
        self.repository = repository
        self.isAdmin = isAdmin
    }

    func load() async {
        listState = .loading
        await performLoad()
    }

    func refresh() async {
        await performLoad()
    }

    private func performLoad() async {
        switch await repository.list(
            practitionerId: isAdmin ? practitionerId : nil,
            catalogue: catalogueFilter,
            paymentType: paymentTypeFilter,
            search: nil,
            dateFrom: dateFrom.map(LeadDateFormat.localDate),
            dateTo: dateTo.map(LeadDateFormat.localDate)
        ) {
        case .success(let response):
            listState = .success(response)
        case .failure(let error):
            listState = .failure(error.message)
        }
    }
}
