import SwiftUI

/// Admin/practitioner Transactions ledger: summary cards for the current
/// filter, then a filterable list below. No detail screen — the prompt's
/// Output list only calls for `TransactionsView`/`TransactionsViewModel`,
/// and rows carry everything worth showing already.
struct TransactionsView: View {

    @StateObject private var viewModel: TransactionsViewModel
    private let userRepository: UserRepository
    private let isAdmin: Bool

    @Environment(\.appTheme) private var theme
    @State private var doctors: [UserDetail] = []
    @State private var showDateRangeSheet = false

    init(repository: TransactionsRepository, userRepository: UserRepository, isAdmin: Bool) {
        self.userRepository = userRepository
        self.isAdmin = isAdmin
        _viewModel = StateObject(wrappedValue: TransactionsViewModel(repository: repository, isAdmin: isAdmin))
    }

    var body: some View {
        VStack(spacing: 0) {
            summaryCards
            filterRow
            content
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Transactions")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.load()
            if isAdmin { await loadDoctors() }
        }
        .sheet(isPresented: $showDateRangeSheet) {
            DateRangeFilterSheet(dateFrom: $viewModel.dateFrom, dateTo: $viewModel.dateTo)
        }
    }

    // MARK: - Summary

    @ViewBuilder
    private var summaryCards: some View {
        let summary = viewModel.listState.value?.summary
        HStack(spacing: 10) {
            SummaryCard(
                title: "Transaction value",
                value: summary.map { CurrencyFormatter.inr($0.totalTransactionValue) } ?? "—"
            )
            SummaryCard(
                title: "Credit consumed",
                value: summary.map { CurrencyFormatter.inr($0.totalCreditConsumed) } ?? "—"
            )
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    // MARK: - Filters

    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                catalogueMenu
                paymentTypeMenu
                if isAdmin {
                    practitionerMenu
                }
                dateRangeButton
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 10)
        }
    }

    private var catalogueMenu: some View {
        Menu {
            Button {
                viewModel.catalogueFilter = nil
            } label: {
                if viewModel.catalogueFilter == nil { Label("All types", systemImage: "checkmark") } else { Text("All types") }
            }
            ForEach(ServiceType.allCases, id: \.self) { type in
                Button {
                    viewModel.catalogueFilter = type
                } label: {
                    if viewModel.catalogueFilter == type { Label(type.label, systemImage: "checkmark") } else { Text(type.label) }
                }
            }
        } label: {
            FilterChip(label: viewModel.catalogueFilter?.label ?? "Catalogue type", isActive: viewModel.catalogueFilter != nil)
        }
        .accessibilityIdentifier("transaction-catalogue-filter")
    }

    private var paymentTypeMenu: some View {
        Menu {
            Button {
                viewModel.paymentTypeFilter = nil
            } label: {
                if viewModel.paymentTypeFilter == nil { Label("All payment types", systemImage: "checkmark") } else { Text("All payment types") }
            }
            ForEach(PaymentType.allCases) { type in
                Button {
                    viewModel.paymentTypeFilter = type
                } label: {
                    if viewModel.paymentTypeFilter == type { Label(type.label, systemImage: "checkmark") } else { Text(type.label) }
                }
            }
        } label: {
            FilterChip(label: viewModel.paymentTypeFilter?.label ?? "Payment type", isActive: viewModel.paymentTypeFilter != nil)
        }
        .accessibilityIdentifier("transaction-payment-type-filter")
    }

    private var practitionerMenu: some View {
        Menu {
            Button {
                viewModel.practitionerId = nil
            } label: {
                if viewModel.practitionerId == nil { Label("All practitioners", systemImage: "checkmark") } else { Text("All practitioners") }
            }
            ForEach(doctors) { doctor in
                Button {
                    viewModel.practitionerId = doctor.id
                } label: {
                    if viewModel.practitionerId == doctor.id { Label(doctor.name, systemImage: "checkmark") } else { Text(doctor.name) }
                }
            }
        } label: {
            FilterChip(
                label: doctors.first(where: { $0.id == viewModel.practitionerId })?.name ?? "Practitioner",
                isActive: viewModel.practitionerId != nil
            )
        }
        .accessibilityIdentifier("transaction-practitioner-filter")
    }

    private var dateRangeButton: some View {
        Button {
            showDateRangeSheet = true
        } label: {
            FilterChip(
                label: "Date range",
                isActive: viewModel.dateFrom != nil || viewModel.dateTo != nil,
                systemImage: "calendar"
            )
        }
        .accessibilityIdentifier("transaction-date-range-filter")
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch viewModel.listState {
        case .loading:
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(0..<6, id: \.self) { _ in SkeletonBlock(height: 64, cornerRadius: 12) }
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

        case .success(let response):
            if response.transactions.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(response.transactions) { item in
                        TransactionRow(item: item)
                            .listRowBackground(theme.surface)
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
            Image(systemName: "creditcard")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))
            Text("No transactions found")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    // MARK: - Networking

    private func loadDoctors() async {
        if case .success(let list) = await userRepository.listDoctors() {
            doctors = list
        }
    }
}

// MARK: - Summary card

private struct SummaryCard: View {
    let title: String
    let value: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.displayFont(.heavy, size: 18))
                .foregroundStyle(theme.onSurface)
            Text(title)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.7))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Filter chip

private struct FilterChip: View {
    let label: String
    let isActive: Bool
    var systemImage: String?
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage {
                Image(systemName: systemImage)
            }
            Text(label)
            Image(systemName: "chevron.down")
                .font(.system(size: 9, weight: .semibold))
        }
        .font(.displayFont(.semibold, size: 12))
        .foregroundStyle(isActive ? theme.onPrimary : theme.onSurface.opacity(0.75))
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(isActive ? theme.primary : theme.surface, in: Capsule())
    }
}

// MARK: - Row

private struct TransactionRow: View {
    let item: TransactionListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.invoiceNumber)
                    .font(.displayFont(.semibold, size: 14))
                    .foregroundStyle(theme.onSurface)
                Text("\(item.patient.name) · \(item.displayServiceName)")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                Text(item.transactionType.label)
                    .font(.bodyFont(size: 11))
                    .foregroundStyle(theme.onSurface.opacity(0.55))
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 4) {
                Text(CurrencyFormatter.inr(item.amountInr))
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface)
                Text(item.paymentType.label)
                    .font(.bodyFont(size: 11))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }
        }
        .padding(.vertical, 6)
    }
}
