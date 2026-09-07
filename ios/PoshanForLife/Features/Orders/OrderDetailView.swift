import SwiftUI

/// Full order detail: the patient, the "full patientProgramme context" the
/// prompt calls for (catalogue item, dates, assigned doctor), every
/// transaction generated against this order, and a "Mark as paid" action
/// gated behind the same confirmation dialog `OrdersView`'s swipe action uses.
struct OrderDetailView: View {

    let orderId: String
    let repository: OrdersRepository

    @Environment(\.appTheme) private var theme

    @State private var state: CardState<OrderDetail> = .loading
    @State private var marking = false
    @State private var markErrorMessage: String?
    @State private var confirmingMarkPaid = false

    var body: some View {
        ScrollView {
            switch state {
            case .loading:
                VStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { _ in SkeletonBlock(height: 60, cornerRadius: 12) }
                }
                .padding(16)

            case .failure(let message):
                VStack(spacing: 8) {
                    Spacer(minLength: 40)
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .padding(24)

            case .success(let order):
                content(for: order)
            }
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle(state.value?.patient.name ?? "Order")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .confirmationDialog(
            "Mark this order as paid?",
            isPresented: $confirmingMarkPaid,
            titleVisibility: .visible
        ) {
            Button("Mark as paid") { Task { await markPaid() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("A transaction and invoice will be generated for this order.")
        }
    }

    @ViewBuilder
    private func content(for order: OrderDetail) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            header(for: order)
            if let programme = order.patientProgramme {
                serviceSection(for: programme)
            }
            if !order.transactions.isEmpty {
                transactionsSection(for: order)
            }
            if let notes = order.notes, !notes.isEmpty {
                notesSection(notes)
            }
            if order.paymentStatus != .paid {
                markPaidButton
            }
            if let markErrorMessage {
                Text(markErrorMessage)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }
        }
        .padding(16)
    }

    private func header(for order: OrderDetail) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                OrderStatusBadge(status: order.status)
                PaymentStatusBadge(status: order.paymentStatus)
                Spacer()
                Text(CurrencyFormatter.inr(order.amountInr))
                    .font(.displayFont(.heavy, size: 18))
                    .foregroundStyle(theme.onSurface)
            }
            Text(order.patient.name)
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            if let email = order.patient.email {
                Text(email)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
            if let phone = order.patient.phone {
                Text(phone)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func serviceSection(for programme: OrderProgrammeRef) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Service")
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)

            Text(programme.displayName)
                .font(.bodyFont(size: 15))
                .foregroundStyle(theme.onSurface)

            if let duration = programme.catalogueItem?.durationLabel {
                Text(duration)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            if let start = LocalDay.display(programme.startDate) {
                let end = LocalDay.display(programme.endDate)
                Text(end != nil && end != start ? "\(start) – \(end!)" : start)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            if let doctor = programme.assignedDoctor {
                Text("Assigned to \(doctor.name)")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            if let status = programme.status {
                AssignmentStatusBadge(status: status)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func transactionsSection(for order: OrderDetail) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Transactions")
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)

            ForEach(Array(order.transactions.enumerated()), id: \.element.id) { index, transaction in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(transaction.invoiceNumber)
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface)
                        Spacer()
                        Text(CurrencyFormatter.inr(transaction.amountInr))
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface)
                    }
                    HStack(spacing: 6) {
                        Text(transaction.transactionType.label)
                        Text("·")
                        Text(transaction.paymentType.label)
                    }
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
                }
                .padding(.vertical, 4)

                if index < order.transactions.count - 1 {
                    Divider()
                }
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func notesSection(_ notes: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Notes")
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)
            Text(notes)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var markPaidButton: some View {
        Button {
            confirmingMarkPaid = true
        } label: {
            HStack {
                Spacer()
                Text(marking ? "Marking as paid…" : "Mark as paid")
                Spacer()
            }
        }
        .buttonStyle(.borderedProminent)
        .disabled(marking)
        .accessibilityIdentifier("mark-order-paid")
    }

    // MARK: - Networking

    private func load() async {
        state = .loading
        switch await repository.get(id: orderId) {
        case .success(let order):
            state = .success(order)
        case .failure(let error):
            state = .failure(error.message)
        }
    }

    private func markPaid() async {
        marking = true
        markErrorMessage = nil
        switch await repository.update(id: orderId, request: UpdateOrderRequest(paymentStatus: .paid, status: nil, notes: nil)) {
        case .success(let order):
            state = .success(order)
            marking = false
        case .failure(let error):
            marking = false
            markErrorMessage = error.message
        }
    }
}
