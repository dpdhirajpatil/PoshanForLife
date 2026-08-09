import SwiftUI

struct ProgrammesListView: View {

    @StateObject private var viewModel: ProgrammesViewModel
    @Environment(\.appTheme) private var theme

    private let repository: ProgrammesRepository

    init(repository: ProgrammesRepository, profile: DashboardRepository) {
        self.repository = repository
        _viewModel = StateObject(
            wrappedValue: ProgrammesViewModel(repository: repository, profile: profile)
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                switch viewModel.assignments {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in
                        SkeletonBlock(height: 108, cornerRadius: 16)
                    }

                case .failure(let message):
                    Text(message)
                        .font(.bodyFont(size: 14))
                        .foregroundStyle(theme.onSurface.opacity(0.7))

                case .success:
                    let items = viewModel.sorted()
                    if items.isEmpty {
                        EmptyProgrammesView()
                    } else {
                        TrapeziumSectionLabel("Your plan")
                        ForEach(items) { assignment in
                            NavigationLink(value: assignment) {
                                AssignmentRow(
                                    assignment: assignment,
                                    progress: viewModel.challengeProgress[assignment.id]
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Programmes")
        .refreshable { await viewModel.load() }
        .task { await viewModel.load() }
        .navigationDestination(for: PatientProgramme.self) { assignment in
            ProgrammeDetailView(
                assignment: assignment,
                patientId: viewModel.patientId,
                repository: repository,
                initialProgress: viewModel.challengeProgress[assignment.id],
                onProgressChange: { viewModel.updateProgress($0, for: assignment.id) }
            )
        }
    }
}

// MARK: - Row

private struct AssignmentRow: View {
    let assignment: PatientProgramme
    let progress: ChallengeProgress?

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: assignment.type?.icon ?? "square.grid.2x2.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(theme.onTertiary)
                    .frame(width: 36, height: 36)
                    .background(theme.tertiary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 3) {
                    Text(assignment.displayName)
                        .font(.displayFont(.semibold, size: 16))
                        .foregroundStyle(theme.onSurface)
                        .fixedSize(horizontal: false, vertical: true)

                    if let doctor = assignment.assignedDoctor {
                        Text("with \(doctor.name)")
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                }

                Spacer(minLength: 8)

                if let status = assignment.parsedStatus {
                    AssignmentStatusBadge(status: status)
                }
            }

            progressSection
        }
        .padding(14)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// Three different treatments, because the three types measure different
    /// things: a programme runs down a calendar, a challenge counts check-ins,
    /// and a session either has happened or hasn't.
    @ViewBuilder
    private var progressSection: some View {
        switch assignment.type {
        case .programme:
            let elapsed = ProgrammeProgress(
                startDate: assignment.startDate, endDate: assignment.endDate
            )
            ProgressRow(fraction: elapsed.fraction, caption: elapsed.remainingLabel)

        case .challenge:
            if let progress {
                ProgressRow(
                    fraction: progress.fraction,
                    caption: progress.currentStreak > 0
                        ? "\(progress.percentComplete)% · \(progress.currentStreak) day streak"
                        : "\(progress.percentComplete)% · not started"
                )
            } else {
                // Progress arrives on a second request; showing a zero bar here
                // would read as "you've done nothing" rather than "loading".
                Text("Loading progress…")
                    .font(.bodyFont(size: 12))
                    .foregroundStyle(theme.onSurface.opacity(0.6))
            }

        case .session, .none:
            HStack(spacing: 6) {
                Image(systemName: sessionIcon)
                    .font(.system(size: 12, weight: .semibold))
                Text(SessionState(startDate: assignment.startDate).label)
                    .font(.bodyFont(size: 13))
                if let date = LocalDay.display(assignment.startDate) {
                    Text("· \(date)")
                        .font(.bodyFont(size: 13))
                        .foregroundStyle(theme.onSurface.opacity(0.6))
                }
            }
            .foregroundStyle(theme.onSurface.opacity(0.8))
        }
    }

    private var sessionIcon: String {
        switch SessionState(startDate: assignment.startDate) {
        case .past: return "checkmark.circle.fill"
        case .today: return "clock.fill"
        case .upcoming: return "calendar"
        }
    }
}

private struct ProgressRow: View {
    let fraction: Double
    let caption: String

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(theme.onSurface.opacity(0.12))
                    Capsule()
                        .fill(theme.primary)
                        .frame(width: geo.size.width * max(0, min(fraction, 1)))
                }
            }
            .frame(height: 8)

            Text(caption)
                .font(.bodyFont(size: 12))
                .foregroundStyle(theme.onSurface.opacity(0.7))
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text("\(Int(max(0, min(fraction, 1)) * 100)) percent, \(caption)"))
    }
}

struct AssignmentStatusBadge: View {
    let status: AssignmentStatus
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
        case .active: return theme.onTertiary
        case .cancelled: return theme.error
        case .completed: return theme.onSurface.opacity(0.75)
        }
    }

    private var background: Color {
        switch status {
        case .active: return theme.tertiary
        case .cancelled: return theme.error.opacity(0.15)
        case .completed: return theme.onSurface.opacity(0.1)
        }
    }
}

// MARK: - Empty state

/// Wording matches the web and Android builds so the three clients don't each
/// invent their own way of saying the same thing.
private struct EmptyProgrammesView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "book.closed")
                .font(.system(size: 44, weight: .light))
                .foregroundStyle(theme.primary.opacity(0.8))

            Text("No active programmes yet")
                .font(.displayFont(.semibold, size: 18))
                .foregroundStyle(theme.onSurface)
                .multilineTextAlignment(.center)

            Text("Ask your practitioner to get you started with a programme, session, or challenge.")
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
        .padding(.horizontal, 24)
    }
}
