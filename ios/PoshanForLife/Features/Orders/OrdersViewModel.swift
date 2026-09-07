import Foundation

/// Backs `OrdersView`. Reads are scoped server-side exactly like
/// Leads/Patients/Documents — a DOCTOR sees only their own patients' orders,
/// an ADMIN sees all — so there's no role param here at all.
@MainActor
final class OrdersViewModel: ObservableObject {

    enum ActionState: Equatable {
        case idle, inFlight
        case failure(String)
    }

    private let repository: OrdersRepository

    @Published var statusFilter: OrderStatus? {
        didSet { Task { await load() } }
    }
    @Published var paymentStatusFilter: PaymentStatus? {
        didSet { Task { await load() } }
    }
    @Published var dateFrom: Date? {
        didSet { Task { await load() } }
    }
    @Published var dateTo: Date? {
        didSet { Task { await load() } }
    }
    @Published var searchQuery: String = "" {
        didSet { scheduleSearch() }
    }
    @Published private(set) var listState: CardState<[OrderListItem]> = .loading
    @Published private(set) var markPaidState: ActionState = .idle

    private var searchTask: Task<Void, Never>?

    init(repository: OrdersRepository) {
        self.repository = repository
    }

    func load() async {
        listState = .loading
        await performLoad()
    }

    func refresh() async {
        await performLoad()
    }

    /// Debounced the same 300ms as every other search field in this app.
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
        switch await repository.list(
            status: statusFilter,
            paymentStatus: paymentStatusFilter,
            search: query.isEmpty ? nil : query,
            dateFrom: dateFrom.map(LeadDateFormat.localDate),
            dateTo: dateTo.map(LeadDateFormat.localDate)
        ) {
        case .success(let items):
            listState = .success(items)
        case .failure(let error):
            listState = .failure(error.message)
        }
    }

    /// Fires the single `PATCH` that marks an order paid — the backend
    /// generates the activation transaction/invoice as a side effect (see
    /// `UpdateOrderRequest`'s doc comment). Used by both the list's swipe
    /// action and `OrderDetailView`'s button.
    func markAsPaid(_ orderId: String) async -> Bool {
        markPaidState = .inFlight
        switch await repository.update(id: orderId, request: UpdateOrderRequest(paymentStatus: .paid, status: nil, notes: nil)) {
        case .success:
            markPaidState = .idle
            await refresh()
            return true
        case .failure(let error):
            markPaidState = .failure(error.message)
            return false
        }
    }
}
