import SwiftUI

struct PatientListView: View {

    @StateObject private var viewModel: PatientManagementViewModel
    @Environment(\.appTheme) private var theme

    /// Held alongside the view model so the pushed detail screen can be built
    /// without routing every call back through it — same convention as
    /// `ReportsListView`.
    private let patientsRepository: PatientsRepository
    private let reportsRepository: ReportsRepository
    private let programmesRepository: ProgrammesRepository

    init(
        patientsRepository: PatientsRepository,
        reportsRepository: ReportsRepository,
        programmesRepository: ProgrammesRepository
    ) {
        self.patientsRepository = patientsRepository
        self.reportsRepository = reportsRepository
        self.programmesRepository = programmesRepository
        _viewModel = StateObject(
            wrappedValue: PatientManagementViewModel(
                patientsRepository: patientsRepository,
                reportsRepository: reportsRepository,
                programmesRepository: programmesRepository
            )
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch viewModel.listState {
                case .loading:
                    ForEach(0..<5, id: \.self) { _ in
                        SkeletonBlock(height: 64, cornerRadius: 12)
                    }

                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))

                case .success(let patients):
                    if patients.isEmpty {
                        EmptyPatientsView(isSearching: !viewModel.searchQuery.isEmpty)
                    } else {
                        ForEach(patients) { patient in
                            NavigationLink(value: patient) {
                                PatientRow(patient: patient)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Patients")
        .searchable(text: $viewModel.searchQuery, prompt: "Search patients")
        .refreshable { await viewModel.refreshList() }
        .task { await viewModel.loadList() }
        .navigationDestination(for: PatientSummary.self) { patient in
            PatientDetailView(
                patientId: patient.id,
                patientName: patient.name,
                patientsRepository: patientsRepository,
                reportsRepository: reportsRepository,
                programmesRepository: programmesRepository
            )
        }
    }
}

private struct PatientRow: View {
    let patient: PatientSummary
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Avatar(name: patient.name, avatarUrl: nil, size: 44)

            VStack(alignment: .leading, spacing: 3) {
                Text(patient.name)
                    .font(.displayFont(.semibold, size: 16))
                    .foregroundStyle(theme.onSurface)
                Text(patient.age.map { "\($0) yrs" } ?? "Age unknown")
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            Spacer(minLength: 8)

            Text(lastReportLabel)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.6))
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.onSurface.opacity(0.35))
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var lastReportLabel: String {
        guard let date = patient.lastReportDate else { return "No reports" }
        return date.formatted(date: .abbreviated, time: .omitted)
    }
}

private struct EmptyPatientsView: View {
    let isSearching: Bool
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "person.2.slash")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))

            Text(isSearching ? "No matching patients" : "No patients assigned yet")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
                .multilineTextAlignment(.center)

            if !isSearching {
                Text("Patients assigned to you will appear here.")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
        .padding(.horizontal, 24)
    }
}
