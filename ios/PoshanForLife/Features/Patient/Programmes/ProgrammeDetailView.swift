import SwiftUI

/// Detail for one assignment.
///
/// The whole model arrives from the list — `GET /patients/{id}/programmes`
/// returns every field this screen shows — so there is no re-fetch on push.
/// The one thing that *is* fetched here is challenge check-in progress, and
/// only for challenge assignments.
///
/// **No service description.** The prompt asks for one, but `ServiceRefDto`
/// doesn't carry it and the catalogue endpoint that does is `@AdminOrDoctor`
/// (a patient gets 403). Everything below is drawn from the assignment itself.
struct ProgrammeDetailView: View {

    let assignment: PatientProgramme
    let patientId: String?
    let repository: ProgrammesRepository
    let initialProgress: ChallengeProgress?
    let onProgressChange: (ChallengeProgress) -> Void
    /// The check-in PATCH is `@PatientOnly` server-side (verified live) — a
    /// practitioner viewing a patient's challenge would get a 403 tapping it.
    /// Progress itself stays visible either way; only the button hides.
    var allowsCheckIn: Bool = true

    @Environment(\.appTheme) private var theme

    @State private var progress: ChallengeProgress?
    @State private var checkingIn = false
    @State private var checkInError: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                factsCard

                if let notes = assignment.notes, !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    NotesCard(notes: notes)
                }

                if assignment.type == .challenge {
                    challengeCard
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(theme.background.ignoresSafeArea())
        .navigationTitle("Details")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            progress = initialProgress
            await loadProgressIfNeeded()
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(assignment.displayName)
                .themedHeading(size: 22)
                .fixedSize(horizontal: false, vertical: true)

            if let doctor = assignment.assignedDoctor {
                Text("with \(doctor.name)")
                    .font(.bodyFont(size: 15))
                    .foregroundStyle(theme.onBackground.opacity(0.7))
            }
        }
    }

    private var factsCard: some View {
        DetailCard {
            if let type = assignment.type {
                DetailRow(label: "Type", value: type.label)
            }
            if let status = assignment.parsedStatus {
                DetailRow(label: "Status", value: status.label)
            }

            // A session is a single day, so one "Date" row is honest where a
            // start/end pair would print the same value twice.
            if assignment.type == .session {
                if let date = LocalDay.display(assignment.startDate) {
                    DetailRow(label: "Date", value: date)
                }
                DetailRow(label: "When", value: SessionState(startDate: assignment.startDate).label)
            } else {
                if let start = LocalDay.display(assignment.startDate) {
                    DetailRow(label: "Starts", value: start)
                }
                if let end = LocalDay.display(assignment.endDate) {
                    DetailRow(label: "Ends", value: end)
                }
            }

            if let duration = assignment.catalogueItem?.durationLabel {
                DetailRow(label: "Duration", value: duration)
            }
            if let price = assignment.priceInr {
                // A zero price is real — challenges and some bundled programmes
                // are seeded at 0 — but "₹0.00" reads like a rendering fault.
                DetailRow(label: "Price", value: price > 0 ? CurrencyFormatter.inr(price) : "Free")
            }
        }
    }

    private var challengeCard: some View {
        DetailCard {
            Text("Challenge progress")
                .font(.displayFont(.semibold, size: 17))
                .foregroundStyle(theme.onSurface)

            if let progress {
                HStack(spacing: 16) {
                    CircularProgressRing(progress: progress.fraction, size: 76, lineWidth: 8) {
                        Text("\(progress.percentComplete)%")
                            .font(.displayFont(.semibold, size: 15))
                            .foregroundStyle(theme.onSurface)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 5) {
                            Image(systemName: "flame.fill")
                                .font(.system(size: 14))
                                .foregroundStyle(theme.primary)
                            Text(progress.currentStreak == 1 ? "1 day streak" : "\(progress.currentStreak) day streak")
                                .font(.displayFont(.semibold, size: 16))
                                .foregroundStyle(theme.onSurface)
                        }
                        Text("Best: \(progress.longestStreak == 1 ? "1 day" : "\(progress.longestStreak) days")")
                            .font(.bodyFont(size: 13))
                            .foregroundStyle(theme.onSurface.opacity(0.7))
                    }
                    Spacer(minLength: 0)
                }
                .padding(.top, 4)

                if allowsCheckIn {
                    checkInButton(progress: progress)
                }
            } else {
                Text("Loading progress…")
                    .font(.bodyFont(size: 14))
                    .foregroundStyle(theme.onSurface.opacity(0.7))
            }

            if let checkInError {
                Text(checkInError)
                    .font(.bodyFont(size: 13))
                    .foregroundStyle(theme.error)
            }
        }
    }

    @ViewBuilder
    private func checkInButton(progress: ChallengeProgress) -> some View {
        if progress.checkedInToday {
            // Deliberately not a tappable no-op: the backend treats a repeat
            // check-in as idempotent, so the only thing another tap could do is
            // imply it counted twice.
            HStack(spacing: 6) {
                Image(systemName: "checkmark.circle.fill")
                Text("Checked in today")
            }
            .font(.displayFont(.semibold, size: 15))
            .foregroundStyle(theme.onSurface.opacity(0.6))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(theme.onSurface.opacity(0.08), in: Capsule())
            .accessibilityIdentifier("checked-in-today")
        } else {
            Button {
                Task { await checkIn() }
            } label: {
                Text(checkingIn ? "Checking in…" : "Check in today")
                    .font(.displayFont(.semibold, size: 15))
                    .foregroundStyle(theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(theme.primary, in: Capsule())
            }
            .disabled(checkingIn || patientId == nil)
            .accessibilityIdentifier("check-in-today")
        }
    }

    // MARK: - Actions

    private func loadProgressIfNeeded() async {
        guard assignment.type == .challenge, progress == nil, let patientId else { return }
        if case .success(let loaded) = await repository.challengeProgress(
            patientId: patientId, ppId: assignment.id
        ) {
            progress = loaded
            onProgressChange(loaded)
        }
    }

    private func checkIn() async {
        guard let patientId, !checkingIn else { return }
        checkingIn = true
        checkInError = nil

        switch await repository.checkIn(patientId: patientId, ppId: assignment.id) {
        case .success(let updated):
            progress = updated
            onProgressChange(updated)
        case .failure(let error):
            checkInError = error.message
        }

        checkingIn = false
    }
}

// MARK: - Building blocks

private struct DetailCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct DetailRow: View {
    let label: String
    let value: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.7))
            Spacer(minLength: 12)
            Text(value)
                .font(.displayFont(.semibold, size: 14))
                .foregroundStyle(theme.onSurface)
                .multilineTextAlignment(.trailing)
        }
    }
}

private struct NotesCard: View {
    let notes: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        DetailCard {
            Text("Notes from your practitioner")
                .font(.displayFont(.semibold, size: 17))
                .foregroundStyle(theme.onSurface)
            Text(notes)
                .font(.bodyFont(size: 14))
                .foregroundStyle(theme.onSurface.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
