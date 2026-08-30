import SwiftUI

/// Admin/practitioner Documents (estimates & invoices) browse+create screen.
/// Reads are scoped server-side exactly like Leads/Patients — a DOCTOR sees
/// only documents tied to their own leads/patients, an ADMIN sees all — so
/// there's no role param or `isAdmin` flag here at all. Unlike Catalogue,
/// BOTH roles can create (`POST /documents` is `@AdminOrDoctor`, not
/// admin-only), so the add button always shows.
struct DocumentsListView: View {

    @StateObject private var viewModel: DocumentsViewModel
    private let repository: DocumentsRepository
    private let patientsRepository: PatientsRepository
    private let leadsRepository: LeadsRepository

    @Environment(\.appTheme) private var theme
    @State private var creatingEstimate = false

    init(repository: DocumentsRepository, patientsRepository: PatientsRepository, leadsRepository: LeadsRepository) {
        self.repository = repository
        self.patientsRepository = patientsRepository
        self.leadsRepository = leadsRepository
        _viewModel = StateObject(wrappedValue: DocumentsViewModel(repository: repository))
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Type", selection: $viewModel.typeFilter) {
                Text("All").tag(DocumentType?.none)
                ForEach(DocumentType.allCases) { type in
                    Text(type.label + "s").tag(DocumentType?.some(type))
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, 6)

            content
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Invoices & estimates")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                statusFilterMenu
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    creatingEstimate = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityIdentifier("add-document")
            }
        }
        .task { await viewModel.load() }
        // `.fullScreenCover`, not `.sheet` — `CreateEstimateView` itself
        // presents `DocumentPartyPickerView` as a `.sheet`, and stacking two
        // plain `.sheet`s breaks touch routing on iOS 18.2's simulator: taps
        // inside the inner sheet silently land on the outer one instead
        // (confirmed live — the picker's row tap kept re-hitting the outer
        // form's "Choose patient or lead" button underneath, no error, no
        // effect). `ConvertToPatientView`/`CatalogueView` already establishes
        // fullScreenCover-then-sheet as the combination that works here.
        .fullScreenCover(isPresented: $creatingEstimate) {
            NavigationStack {
                CreateEstimateView(
                    repository: repository,
                    patientsRepository: patientsRepository,
                    leadsRepository: leadsRepository
                ) {
                    Task { await viewModel.refresh() }
                }
            }
        }
        .navigationDestination(for: DocumentListItem.self) { item in
            DocumentDetailView(documentId: item.id, repository: repository, canManage: true)
        }
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

        case .success(let items):
            if items.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink(value: item) {
                            DocumentRow(item: item)
                        }
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
            Image(systemName: "doc.text")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))
            Text("No documents found")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            Text("Tap + to create an estimate.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    private var statusFilterMenu: some View {
        Menu {
            Button {
                viewModel.statusFilter = nil
            } label: {
                if viewModel.statusFilter == nil { Label("All", systemImage: "checkmark") } else { Text("All") }
            }
            ForEach(DocumentStatus.allCases) { status in
                Button {
                    viewModel.statusFilter = status
                } label: {
                    if viewModel.statusFilter == status { Label(status.label, systemImage: "checkmark") } else { Text(status.label) }
                }
            }
        } label: {
            Image(systemName: "line.3.horizontal.decrease.circle")
        }
        .accessibilityIdentifier("document-status-filter")
    }
}

// MARK: - Row

private struct DocumentRow: View {
    let item: DocumentListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.documentNumber)
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                Text(item.partyName)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 4) {
                Text(CurrencyFormatter.inr(item.total))
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface)
                DocumentStatusBadge(status: item.parsedStatus ?? .draft)
            }
        }
        .padding(.vertical, 6)
    }
}

struct DocumentStatusBadge: View {
    let status: DocumentStatus
    @Environment(\.appTheme) private var theme

    var body: some View {
        Text(status.label)
            .font(.bodyFont(size: 11))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch status {
        case .paid: return theme.onPrimary
        case .sent: return theme.onTertiary
        case .draft: return theme.onSurface.opacity(0.75)
        }
    }

    private var background: Color {
        switch status {
        case .paid: return theme.primary
        case .sent: return theme.tertiary
        case .draft: return theme.onSurface.opacity(0.1)
        }
    }
}
