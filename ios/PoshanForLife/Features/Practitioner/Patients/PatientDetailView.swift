import SwiftUI

struct PatientDetailView: View {

    let patientId: String
    let patientName: String

    /// Held alongside the view model so Reports/Programmes rows can push to
    /// IOS-05/IOS-06's own detail screens without routing back through it —
    /// same convention as `ReportsListView`/`ProgrammesListView`.
    private let reportsRepository: ReportsRepository
    private let programmesRepository: ProgrammesRepository

    @StateObject private var viewModel: PatientManagementViewModel
    @Environment(\.appTheme) private var theme
    @FocusState private var notesFocused: Bool
    @State private var selectedTab: Section = .overview

    private enum Section: String, CaseIterable, Identifiable {
        case overview = "Overview", reports = "Reports", programmes = "Programmes", notes = "Notes"
        var id: String { rawValue }
    }

    init(
        patientId: String,
        patientName: String,
        patientsRepository: PatientsRepository,
        reportsRepository: ReportsRepository,
        programmesRepository: ProgrammesRepository
    ) {
        self.patientId = patientId
        self.patientName = patientName
        self.reportsRepository = reportsRepository
        self.programmesRepository = programmesRepository
        _viewModel = StateObject(
            wrappedValue: PatientManagementViewModel(
                patientId: patientId,
                patientsRepository: patientsRepository,
                reportsRepository: reportsRepository,
                programmesRepository: programmesRepository
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            Picker("Section", selection: $selectedTab) {
                ForEach(Section.allCases) { tab in
                    Text(tab.rawValue).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            content
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle(patientName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.loadDetail() }
        .toolbar {
            if selectedTab == .notes {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        notesFocused = false
                        Task { await viewModel.saveNotes() }
                    }
                    .disabled(viewModel.notesSaveState == .saving)
                }
            }
        }
        .navigationDestination(for: String.self) { reportId in
            ReportDetailView(reportId: reportId, repository: reportsRepository)
        }
        .navigationDestination(for: PatientProgramme.self) { assignment in
            ProgrammeDetailView(
                assignment: assignment,
                patientId: patientId,
                repository: programmesRepository,
                initialProgress: nil,
                onProgressChange: { _ in },
                allowsCheckIn: false
            )
        }
    }

    @ViewBuilder
    private var header: some View {
        switch viewModel.detailState {
        case .success(let patient):
            PatientHeaderCard(patient: patient)
        case .loading:
            SkeletonBlock(height: 60, cornerRadius: 12)
                .padding(16)
        case .failure:
            EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.detailState {
        case .loading:
            Spacer(minLength: 0)
            ProgressView()
                .frame(maxWidth: .infinity)
            Spacer(minLength: 0)

        case .failure(let message):
            Spacer(minLength: 0)
            Text(message)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(24)
            Spacer(minLength: 0)

        case .success(let patient):
            switch selectedTab {
            case .overview:
                OverviewTab(patient: patient)
            case .reports:
                ReportsTab(viewModel: viewModel)
            case .programmes:
                ProgrammesTab(viewModel: viewModel)
            case .notes:
                NotesTab(viewModel: viewModel, notesFocused: $notesFocused)
            }
        }
    }
}

// MARK: - Header

private struct PatientHeaderCard: View {
    let patient: PatientDetail
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Avatar(name: patient.name, avatarUrl: nil, size: 52)

            VStack(alignment: .leading, spacing: 3) {
                Text(patient.name)
                    .font(.displayFont(.semibold, size: 18))
                    .foregroundStyle(theme.onSurface)
                Text(subtitle)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
                if let email = patient.email {
                    Text(email)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
    }

    private var subtitle: String {
        let parts = [
            patient.age.map { "\($0) yrs" },
            patient.gender?.capitalized,
            patient.bloodGroup,
        ].compactMap { $0 }
        return parts.isEmpty ? "No profile details yet" : parts.joined(separator: " · ")
    }
}

// MARK: - Overview

private struct OverviewTab: View {
    let patient: PatientDetail
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Latest vitals")
                    .font(.displayFont(.semibold, size: 17))
                    .foregroundStyle(theme.onSurface)

                if let vitals = patient.latestVitals {
                    HStack {
                        StatColumn(label: "Weight", value: numberLabel(vitals.weightKg, suffix: " kg"))
                        Spacer(minLength: 0)
                        StatColumn(label: "BMI", value: numberLabel(vitals.bmi, suffix: ""))
                        Spacer(minLength: 0)
                        StatColumn(label: "Body fat", value: numberLabel(vitals.bodyFatPct, suffix: "%"))
                    }
                    .padding(16)
                    .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                } else {
                    Text("No health records yet")
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }

                if let history = patient.medicalHistory, !history.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    InfoSection(title: "Medical history", text: history)
                }
                if let contact = patient.emergencyContact, !contact.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    InfoSection(title: "Emergency contact", text: contact)
                }
            }
            .padding(16)
        }
    }

    private func numberLabel(_ value: Double?, suffix: String) -> String {
        guard let value else { return "—" }
        return String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value) + suffix
    }
}

private struct StatColumn: View {
    let label: String
    let value: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
            Text(label)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.6))
        }
    }
}

private struct InfoSection: View {
    let title: String
    let text: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.displayFont(.semibold, size: 15))
                .foregroundStyle(theme.onSurface)
            Text(text)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - Reports

private struct ReportsTab: View {
    @ObservedObject var viewModel: PatientManagementViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                switch viewModel.reportsState {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(height: 60, cornerRadius: 12)
                    }
                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                case .success(let reports):
                    if reports.isEmpty {
                        Text("No reports yet")
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    } else {
                        ForEach(reports) { report in
                            NavigationLink(value: report.id) {
                                ReportSummaryRow(report: report)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

private struct ReportSummaryRow: View {
    let report: ReportListItem
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(report.title)
                    .font(.bodyFont(size: 16))
                    .foregroundStyle(theme.onSurface)
                if let date = report.createdAt {
                    Text(date, style: .date)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
            }
            Spacer(minLength: 8)
            if let status = report.parsedStatus {
                StatusBadge(status: status)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.onSurface.opacity(0.35))
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Programmes

private struct ProgrammesTab: View {
    @ObservedObject var viewModel: PatientManagementViewModel
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                switch viewModel.programmesState {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(height: 64, cornerRadius: 16)
                    }
                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                case .success(let assignments):
                    if assignments.isEmpty {
                        Text("No programmes assigned yet")
                            .font(.bodyFont(size: 14))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    } else {
                        ForEach(assignments) { assignment in
                            NavigationLink(value: assignment) {
                                ProgrammeSummaryRow(assignment: assignment)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
    }
}

private struct ProgrammeSummaryRow: View {
    let assignment: PatientProgramme
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: assignment.type?.icon ?? "square.grid.2x2.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.onTertiary)
                .frame(width: 34, height: 34)
                .background(theme.tertiary, in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text(assignment.displayName)
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onSurface)
                if let date = LocalDay.display(assignment.startDate) {
                    Text(date)
                        .font(.bodyFont(size: 12))
                        .foregroundStyle(theme.onSurface.opacity(0.7))
                }
            }

            Spacer(minLength: 8)
            if let status = assignment.parsedStatus {
                AssignmentStatusBadge(status: status)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.onSurface.opacity(0.35))
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Notes

private struct NotesTab: View {
    @ObservedObject var viewModel: PatientManagementViewModel
    var notesFocused: FocusState<Bool>.Binding
    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Practitioner notes")
                    .font(.displayFont(.semibold, size: 17))
                    .foregroundStyle(theme.onSurface)
                Text("Only visible to you and other practitioners — the patient never sees this.")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))

                TextEditor(text: Binding(
                    get: { viewModel.notesText },
                    set: { viewModel.onNotesChange($0) }
                ))
                .focused(notesFocused)
                .font(.bodyFont(size: 15))
                .frame(minHeight: 220)
                .padding(8)
                .scrollContentBackground(.hidden)
                .background(theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                saveStatus
            }
            .padding(16)
        }
    }

    @ViewBuilder
    private var saveStatus: some View {
        switch viewModel.notesSaveState {
        case .saving:
            Text("Saving…")
                .font(.bodyFont(size: 13))
                .foregroundStyle(theme.onSurface.opacity(0.6))
        case .saved:
            Label("Saved", systemImage: "checkmark.circle.fill")
                .font(.bodyFont(size: 13))
                .foregroundStyle(theme.tertiary)
        case .failure(let message):
            Text("Couldn't save: \(message)")
                .font(.bodyFont(size: 13))
                .foregroundStyle(theme.error)
        case .idle:
            EmptyView()
        }
    }
}
