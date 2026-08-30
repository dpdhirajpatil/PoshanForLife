import SwiftUI

/// Renders one document (estimate or invoice) natively — line items, GST
/// breakdown, total — no PDF viewer needed to just look at it. "Share PDF"
/// fetches a fresh signed URL and hands it to the system share sheet.
///
/// `canManage` gates the draft→sent→paid status actions: `true` when opened
/// from `DocumentsListView` (admin/practitioner), `false` when a patient
/// reaches this from the dashboard's outstanding-balance card
/// (`DashboardRoute.invoice`), since `PATCH /documents/{id}` is
/// `@AdminOrDoctor`-only server-side and would just 403 for a patient.
struct DocumentDetailView: View {

    let documentId: String
    let repository: DocumentsRepository
    var canManage: Bool = false

    @Environment(\.appTheme) private var theme

    @State private var state: CardState<DocumentDetail> = .loading
    @State private var statusUpdating = false
    @State private var statusErrorMessage: String?
    @State private var shareURL: ShareURL?
    @State private var shareErrorMessage: String?
    @State private var fetchingShareURL = false

    private struct ShareURL: Identifiable {
        let url: URL
        var id: String { url.absoluteString }
    }

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

            case .success(let document):
                content(for: document)
            }
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle(state.value?.documentNumber ?? "Document")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if state.value != nil {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task { await fetchShareURL() }
                    } label: {
                        if fetchingShareURL {
                            ProgressView()
                        } else {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                    .disabled(fetchingShareURL)
                    .accessibilityIdentifier("share-document-pdf")
                }
            }
        }
        .task { await load() }
        .sheet(item: $shareURL) { share in
            ActivityShareSheet(activityItems: [share.url])
        }
        .alert("Couldn't get the PDF", isPresented: Binding(get: { shareErrorMessage != nil }, set: { if !$0 { shareErrorMessage = nil } })) {
            Button("OK", role: .cancel) { shareErrorMessage = nil }
        } message: {
            Text(shareErrorMessage ?? "")
        }
    }

    @ViewBuilder
    private func content(for document: DocumentDetail) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            header(for: document)
            lineItemsSection(for: document)
            totalsSection(for: document)
            if canManage {
                statusActions(for: document)
            }
            if let statusErrorMessage {
                Text(statusErrorMessage)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }
        }
        .padding(16)
    }

    private func header(for document: DocumentDetail) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(document.documentType.label)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                Spacer()
                DocumentStatusBadge(status: document.status)
            }
            Text(document.documentNumber)
                .font(.displayFont(.heavy, size: 20))
                .foregroundStyle(theme.onSurface)
            if let name = document.patient?.name ?? document.lead?.name {
                Text(name)
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func lineItemsSection(for document: DocumentDetail) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Line items")
                .font(.displayFont(.semibold, size: 16))
                .foregroundStyle(theme.onSurface)

            ForEach(Array(document.items.enumerated()), id: \.offset) { index, item in
                VStack(alignment: .leading, spacing: 3) {
                    HStack(alignment: .top) {
                        Text(item.itemName)
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface)
                        Spacer()
                        Text(CurrencyFormatter.inr(item.lineTotal))
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface)
                    }
                    HStack(spacing: 6) {
                        if let hsnSac = item.hsnSac, !hsnSac.isEmpty {
                            Text("HSN/SAC \(hsnSac)")
                            Text("·")
                        }
                        Text("Qty \(item.quantity) × \(CurrencyFormatter.inr(item.rateInr))")
                    }
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
                }
                .padding(.vertical, 4)

                if index < document.items.count - 1 {
                    Divider()
                }
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func totalsSection(for document: DocumentDetail) -> some View {
        VStack(spacing: 8) {
            totalsRow("Subtotal", document.subtotal)
            if document.discountInr > 0 {
                totalsRow("Discount", -document.discountInr)
            }
            totalsRow("CGST", document.cgstAmount)
            totalsRow("SGST", document.sgstAmount)
            Divider()
            totalsRow("Total", document.total, emphasized: true)
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func totalsRow(_ label: String, _ value: Double, emphasized: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(emphasized ? .displayFont(.semibold, size: 16) : .bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(emphasized ? 1 : 0.7))
            Spacer()
            Text(CurrencyFormatter.inr(value))
                .font(emphasized ? .displayFont(.semibold, size: 16) : .bodyFont(size: 14))
                .foregroundStyle(theme.onSurface)
        }
    }

    @ViewBuilder
    private func statusActions(for document: DocumentDetail) -> some View {
        switch document.status {
        case .draft:
            Button {
                Task { await updateStatus(to: .sent) }
            } label: {
                HStack {
                    Spacer()
                    Text(statusUpdating ? "Updating…" : "Mark as sent")
                    Spacer()
                }
            }
            .disabled(statusUpdating)
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("mark-document-sent")
        case .sent:
            Button {
                Task { await updateStatus(to: .paid) }
            } label: {
                HStack {
                    Spacer()
                    Text(statusUpdating ? "Updating…" : "Mark as paid")
                    Spacer()
                }
            }
            .disabled(statusUpdating)
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("mark-document-paid")
        case .paid:
            EmptyView()
        }
    }

    // MARK: - Networking

    private func load() async {
        state = .loading
        switch await repository.get(id: documentId) {
        case .success(let document):
            state = .success(document)
        case .failure(let error):
            state = .failure(error.message)
        }
    }

    private func updateStatus(to status: DocumentStatus) async {
        statusUpdating = true
        statusErrorMessage = nil
        switch await repository.updateStatus(id: documentId, status: status) {
        case .success(let document):
            state = .success(document)
            statusUpdating = false
        case .failure(let error):
            statusUpdating = false
            statusErrorMessage = error.message
        }
    }

    private func fetchShareURL() async {
        fetchingShareURL = true
        shareErrorMessage = nil
        switch await repository.pdfUrl(id: documentId) {
        case .success(let response):
            fetchingShareURL = false
            if let url = URL(string: response.pdfUrl) {
                shareURL = ShareURL(url: url)
            } else {
                shareErrorMessage = "Received an invalid PDF link."
            }
        case .failure(let error):
            fetchingShareURL = false
            shareErrorMessage = error.message
        }
    }
}
