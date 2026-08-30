import SwiftUI

/// Patient/lead picker for `CreateEstimateView`. A segmented source switch
/// rather than a merged list — patients and leads are different repositories
/// with different search endpoints. There's no existing "picker mode" on
/// `PatientListView`/`LeadListView` (unlike `CatalogueView`, both are
/// push-navigation-only), so this is its own small screen rather than a
/// retrofit of either.
struct DocumentPartyPickerView: View {

    let patientsRepository: PatientsRepository
    let leadsRepository: LeadsRepository
    let onSelect: (DocumentPartySelection) -> Void

    private enum Source: String, CaseIterable, Identifiable {
        case patient = "Patients"
        case lead = "Leads"
        var id: String { rawValue }
    }

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    @State private var source: Source = .patient
    @State private var searchQuery = ""
    @State private var patients: CardState<[PatientSummary]> = .loading
    @State private var leads: CardState<[LeadListItem]> = .loading
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 0) {
            Picker("Source", selection: $source) {
                ForEach(Source.allCases) { source in
                    Text(source.rawValue).tag(source)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            content
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Select patient or lead")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchQuery, prompt: "Search")
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Cancel") { dismiss() }
            }
        }
        .task { await load() }
        .onChange(of: source) { _ in Task { await load() } }
        .onChange(of: searchQuery) { _ in scheduleSearch() }
    }

    @ViewBuilder
    private var content: some View {
        switch source {
        case .patient:
            switch patients {
            case .loading: loadingList
            case .failure(let message): messageView(message)
            case .success(let list):
                if list.isEmpty {
                    messageView("No matching patients")
                } else {
                    List(list) { patient in
                        row(name: patient.name, subtitle: patient.phone ?? patient.email) {
                            select(id: patient.id, name: patient.name, isLead: false)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
        case .lead:
            switch leads {
            case .loading: loadingList
            case .failure(let message): messageView(message)
            case .success(let list):
                if list.isEmpty {
                    messageView("No matching leads")
                } else {
                    List(list) { lead in
                        row(name: lead.name, subtitle: lead.phone ?? lead.email) {
                            select(id: lead.id, name: lead.name, isLead: true)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
        }
    }

    private var loadingList: some View {
        ScrollView {
            VStack(spacing: 10) {
                ForEach(0..<6, id: \.self) { _ in SkeletonBlock(height: 56, cornerRadius: 12) }
            }
            .padding(16)
        }
    }

    private func messageView(_ message: String) -> some View {
        VStack {
            Spacer(minLength: 0)
            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
            Spacer(minLength: 0)
        }
        .padding(24)
    }

    private func row(name: String, subtitle: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                Text(name)
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                if let subtitle {
                    Text(subtitle)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                }
            }
        }
        .buttonStyle(.plain)
        .listRowBackground(theme.surface)
    }

    private func select(id: String, name: String, isLead: Bool) {
        onSelect(DocumentPartySelection(id: id, name: name, isLead: isLead))
        dismiss()
    }

    // MARK: - Networking

    private func scheduleSearch() {
        searchTask?.cancel()
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await load()
        }
    }

    private func load() async {
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        switch source {
        case .patient:
            patients = .loading
            switch await patientsRepository.list(search: query.isEmpty ? nil : query) {
            case .success(let list): patients = .success(list)
            case .failure(let error): patients = .failure(error.message)
            }
        case .lead:
            leads = .loading
            switch await leadsRepository.list(stage: nil, search: query.isEmpty ? nil : query) {
            case .success(let response): leads = .success(response.leads)
            case .failure(let error): leads = .failure(error.message)
            }
        }
    }
}
