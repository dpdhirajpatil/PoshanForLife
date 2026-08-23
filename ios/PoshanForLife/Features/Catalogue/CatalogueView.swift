import SwiftUI

/// The one catalogue browse screen for every entry point: a plain
/// practitioner browse, an admin browse+CRUD screen, and a picker sheet for
/// assigning a service (from `ConvertToPatientView`, and eventually a
/// patient-detail "assign a service" flow). Which one it is comes entirely
/// from `isAdmin`/`onSelect` — never a duplicated screen, matching Android's
/// `CatalogueScreen(isAdmin, pickerMode, onItemSelected)`.
struct CatalogueView: View {

    @StateObject private var viewModel: CatalogueViewModel
    private let repository: CatalogueRepository
    private let isAdmin: Bool
    private let onSelect: ((CatalogueItem) -> Void)?

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var creatingNew = false
    @State private var editingItem: CatalogueItem?
    @State private var pendingDelete: CatalogueItem?
    @State private var deleteErrorMessage: String?

    /// `onSelect` present ⇒ picker mode: only published items, tapping a row
    /// selects and dismisses, no status filter or admin affordances appear
    /// regardless of `isAdmin`.
    init(repository: CatalogueRepository, isAdmin: Bool = false, initialType: ServiceType = .programme, onSelect: ((CatalogueItem) -> Void)? = nil) {
        self.repository = repository
        self.isAdmin = isAdmin
        self.onSelect = onSelect
        _viewModel = StateObject(
            wrappedValue: CatalogueViewModel(
                repository: repository,
                mode: onSelect != nil ? .picker : .browse,
                initialType: initialType
            )
        )
    }

    private var showsAdminControls: Bool { isAdmin && onSelect == nil }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Type", selection: $viewModel.serviceType) {
                ForEach(ServiceType.allCases, id: \.self) { type in
                    Text(type.label + "s").tag(type)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, 6)

            content
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle(onSelect != nil ? "Select a service" : "Catalogue")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $viewModel.searchQuery, prompt: "Search \(viewModel.serviceType.label.lowercased())s")
        .toolbar {
            if onSelect != nil {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
            if !viewModel.mode.isPicker {
                ToolbarItem(placement: .navigationBarTrailing) {
                    statusFilterMenu
                }
            }
            if showsAdminControls {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        creatingNew = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityIdentifier("add-catalogue-item")
                }
            }
        }
        .task { await viewModel.load() }
        .sheet(isPresented: $creatingNew) {
            NavigationStack {
                CatalogueItemFormView(
                    type: viewModel.serviceType,
                    existingItem: nil,
                    repository: repository
                ) {
                    Task { await viewModel.refresh() }
                }
            }
        }
        .sheet(item: $editingItem) { item in
            NavigationStack {
                CatalogueItemFormView(
                    type: viewModel.serviceType,
                    existingItem: item,
                    repository: repository
                ) {
                    Task { await viewModel.refresh() }
                }
            }
        }
        .alert("Couldn't delete", isPresented: Binding(get: { deleteErrorMessage != nil }, set: { if !$0 { deleteErrorMessage = nil } })) {
            Button("OK", role: .cancel) { deleteErrorMessage = nil }
        } message: {
            Text(deleteErrorMessage ?? "")
        }
        .confirmationDialog(
            "Delete \(pendingDelete?.name ?? "this item")?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                guard let item = pendingDelete else { return }
                pendingDelete = nil
                Task {
                    if !(await viewModel.delete(item)), case .failure(let message) = viewModel.deleteState {
                        deleteErrorMessage = message
                    }
                }
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
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
                        row(for: item)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await viewModel.refresh() }
            }
        }
    }

    @ViewBuilder
    private func row(for item: CatalogueItem) -> some View {
        Button {
            if let onSelect {
                onSelect(item)
                dismiss()
            } else if showsAdminControls {
                editingItem = item
            }
        } label: {
            CatalogueRow(item: item)
        }
        .buttonStyle(.plain)
        .listRowBackground(theme.surface)
        .disabled(onSelect == nil && !showsAdminControls)
        .swipeActions(edge: .trailing) {
            if showsAdminControls {
                Button(role: .destructive) {
                    pendingDelete = item
                } label: {
                    Label("Delete", systemImage: "trash")
                }
                Button {
                    editingItem = item
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                .tint(theme.tertiary)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Spacer(minLength: 0)
            Image(systemName: "bag.badge.plus")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))
            Text("No \(viewModel.serviceType.label.lowercased())s found")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            if showsAdminControls {
                Text("Tap + to add one.")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }
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
            ForEach(CatalogueStatus.allCases) { status in
                Button {
                    viewModel.statusFilter = status
                } label: {
                    if viewModel.statusFilter == status { Label(status.label, systemImage: "checkmark") } else { Text(status.label) }
                }
            }
        } label: {
            Image(systemName: "line.3.horizontal.decrease.circle")
        }
        .accessibilityIdentifier("catalogue-status-filter")
    }
}

private extension CatalogueViewModel.Mode {
    var isPicker: Bool { self == .picker }
}

// MARK: - Row

private struct CatalogueRow: View {
    let item: CatalogueItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            CoverThumbnail(url: item.coverImageUrl)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.name)
                    .font(.displayFont(.semibold, size: 16))
                    .foregroundStyle(theme.onSurface)

                HStack(spacing: 6) {
                    if let priceInr = item.priceInr {
                        Text(CurrencyFormatter.inr(priceInr))
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                    if let duration = item.durationLabel {
                        Text("·").foregroundStyle(theme.onSurface.opacity(0.4))
                        Text(duration)
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                }
            }

            Spacer(minLength: 8)

            if let status = item.status {
                CatalogueStatusBadge(status: status)
            }
        }
        .padding(.vertical, 6)
    }
}

private struct CoverThumbnail: View {
    let url: String?
    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            if let url, let imageURL = URL(string: url) {
                AsyncImage(url: imageURL) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var placeholder: some View {
        RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(theme.onSurface.opacity(0.08))
            .overlay(
                Image(systemName: "photo")
                    .foregroundStyle(theme.onSurface.opacity(0.3))
            )
    }
}

struct CatalogueStatusBadge: View {
    let status: CatalogueStatus
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
        case .published: return theme.onPrimary
        case .draft: return theme.onSurface.opacity(0.75)
        case .archived: return theme.error
        }
    }

    private var background: Color {
        switch status {
        case .published: return theme.primary
        case .draft: return theme.onSurface.opacity(0.1)
        case .archived: return theme.error.opacity(0.15)
        }
    }
}
