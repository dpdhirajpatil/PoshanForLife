import SwiftUI

/// Admin/practitioner Orders browse screen. Reads are scoped server-side
/// exactly like Documents/Leads/Patients — a DOCTOR sees only their own
/// patients' orders, an ADMIN sees all — so there's no role param or
/// `isAdmin` flag here.
struct OrdersView: View {

    @StateObject private var viewModel: OrdersViewModel
    private let repository: OrdersRepository

    @Environment(\.appTheme) private var theme
    @State private var showDateRangeSheet = false
    @State private var pendingMarkPaid: OrderListItem?
    @State private var markPaidErrorMessage: String?

    init(repository: OrdersRepository) {
        self.repository = repository
        _viewModel = StateObject(wrappedValue: OrdersViewModel(repository: repository))
    }

    var body: some View {
        content
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Orders")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $viewModel.searchQuery, prompt: "Search patients")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showDateRangeSheet = true
                    } label: {
                        Image(systemName: dateRangeActive ? "calendar.badge.checkmark" : "calendar")
                    }
                    .accessibilityIdentifier("order-date-range-filter")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    paymentStatusMenu
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    statusMenu
                }
            }
            .task { await viewModel.load() }
            .sheet(isPresented: $showDateRangeSheet) {
                DateRangeFilterSheet(dateFrom: $viewModel.dateFrom, dateTo: $viewModel.dateTo)
            }
            .navigationDestination(for: OrderListItem.self) { item in
                OrderDetailView(orderId: item.id, repository: repository)
            }
            .confirmationDialog(
                "Mark this order as paid?",
                isPresented: Binding(get: { pendingMarkPaid != nil }, set: { if !$0 { pendingMarkPaid = nil } }),
                titleVisibility: .visible
            ) {
                Button("Mark as paid") {
                    guard let order = pendingMarkPaid else { return }
                    pendingMarkPaid = nil
                    Task {
                        if !(await viewModel.markAsPaid(order.id)), case .failure(let message) = viewModel.markPaidState {
                            markPaidErrorMessage = message
                        }
                    }
                }
                Button("Cancel", role: .cancel) { pendingMarkPaid = nil }
            } message: {
                Text("A transaction and invoice will be generated for this order.")
            }
            .alert(
                "Couldn't mark as paid",
                isPresented: Binding(get: { markPaidErrorMessage != nil }, set: { if !$0 { markPaidErrorMessage = nil } })
            ) {
                Button("OK", role: .cancel) { markPaidErrorMessage = nil }
            } message: {
                Text(markPaidErrorMessage ?? "")
            }
    }

    private var dateRangeActive: Bool {
        viewModel.dateFrom != nil || viewModel.dateTo != nil
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch viewModel.listState {
        case .loading:
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(0..<6, id: \.self) { _ in SkeletonBlock(height: 72, cornerRadius: 12) }
                }
                .padding(16)
            }

        case .failure(let message):
            VStack(spacing: 8) {
                Spacer(minLength: 0)
                Text(message)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .multilineTextAlignment(.center)
                Spacer(minLength: 0)
            }
            .padding(24)

        case .success(let items):
            if items.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink(value: item) {
                            OrderRow(item: item)
                        }
                        .listRowBackground(theme.surface)
                        .swipeActions(edge: .trailing) {
                            if item.paymentStatus != .paid {
                                Button {
                                    pendingMarkPaid = item
                                } label: {
                                    Label("Mark as paid", systemImage: "checkmark.circle")
                                }
                                .tint(theme.primary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await viewModel.refresh() }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Spacer(minLength: 0)
            Image(systemName: "shippingbox")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))
            Text("No orders found")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    // MARK: - Filters

    private var statusMenu: some View {
        Menu {
            Button {
                viewModel.statusFilter = nil
            } label: {
                if viewModel.statusFilter == nil { Label("All statuses", systemImage: "checkmark") } else { Text("All statuses") }
            }
            ForEach(OrderStatus.allCases) { status in
                Button {
                    viewModel.statusFilter = status
                } label: {
                    if viewModel.statusFilter == status { Label(status.label, systemImage: "checkmark") } else { Text(status.label) }
                }
            }
        } label: {
            Image(systemName: "tag")
        }
        .accessibilityIdentifier("order-status-filter")
    }

    private var paymentStatusMenu: some View {
        Menu {
            Button {
                viewModel.paymentStatusFilter = nil
            } label: {
                if viewModel.paymentStatusFilter == nil { Label("All payments", systemImage: "checkmark") } else { Text("All payments") }
            }
            ForEach(PaymentStatus.allCases) { status in
                Button {
                    viewModel.paymentStatusFilter = status
                } label: {
                    if viewModel.paymentStatusFilter == status { Label(status.label, systemImage: "checkmark") } else { Text(status.label) }
                }
            }
        } label: {
            Image(systemName: "creditcard")
        }
        .accessibilityIdentifier("order-payment-status-filter")
    }
}

// MARK: - Row

private struct OrderRow: View {
    let item: OrderListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.patient.name)
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                Text(item.displayServiceName)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 4) {
                Text(CurrencyFormatter.inr(item.amountInr))
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface)
                HStack(spacing: 4) {
                    OrderStatusBadge(status: item.status)
                    PaymentStatusBadge(status: item.paymentStatus)
                }
            }
        }
        .padding(.vertical, 6)
    }
}

// MARK: - Badges

struct OrderStatusBadge: View {
    let status: OrderStatus
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(status.label)
            .font(.bodyFont(size: 10))
            .foregroundStyle(foreground)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch status {
        case .active: return theme.onSurface.opacity(0.75)
        case .completed: return theme.onPrimary
        case .deactivated: return theme.error
        }
    }

    private var background: Color {
        switch status {
        case .active: return theme.onSurface.opacity(0.1)
        case .completed: return theme.primary
        case .deactivated: return theme.error.opacity(0.15)
        }
    }
}

struct PaymentStatusBadge: View {
    let status: PaymentStatus
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(status.label)
            .font(.bodyFont(size: 10))
            .foregroundStyle(foreground)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch status {
        case .paid: return theme.onPrimary
        case .pending: return theme.onTertiary
        case .unpaid: return theme.error
        }
    }

    private var background: Color {
        switch status {
        case .paid: return theme.primary
        case .pending: return theme.tertiary
        case .unpaid: return theme.error.opacity(0.15)
        }
    }
}
