import Foundation

@MainActor
final class ProgrammesViewModel: ObservableObject {

    @Published private(set) var assignments: CardState<[PatientProgramme]> = .loading

    /// Keyed by assignment id. Only challenge rows appear here — see
    /// `loadChallengeProgress` for why these are fetched separately at all.
    @Published private(set) var challengeProgress: [String: ChallengeProgress] = [:]

    private(set) var patientId: String?

    private let repository: ProgrammesRepository
    private let profile: DashboardRepository

    init(repository: ProgrammesRepository, profile: DashboardRepository) {
        self.repository = repository
        self.profile = profile
    }

    func load() async {
        guard case .success(let user) = await profile.currentUser() else {
            assignments = .failure("Couldn't load your profile")
            return
        }
        patientId = user.id

        let result = await repository.assignments(patientId: user.id)
        assignments = result.cardState

        if case .success(let list) = result {
            await loadChallengeProgress(for: list, patientId: user.id)
        }
    }

    /// The list endpoint carries no check-in data, so each challenge needs its
    /// own request. That is an N+1 — accepted deliberately, and bounded three
    /// ways: only `challenge` rows qualify (programmes and sessions are
    /// skipped, and asking for them would 404), a patient holds a handful of
    /// assignments at most, and the calls run concurrently rather than in
    /// series.
    ///
    /// The alternative was showing calendar progress for challenges, which
    /// would tell someone who has never checked in that they're 80% done. A
    /// challenge measures what you did, not what day it is.
    private func loadChallengeProgress(for list: [PatientProgramme], patientId: String) async {
        let challenges = list.filter { $0.type == .challenge }
        guard !challenges.isEmpty else {
            challengeProgress = [:]
            return
        }

        let repository = self.repository
        var loaded: [String: ChallengeProgress] = [:]

        await withTaskGroup(of: (String, ChallengeProgress?).self) { group in
            for challenge in challenges {
                group.addTask {
                    let result = await repository.challengeProgress(
                        patientId: patientId, ppId: challenge.id
                    )
                    return (challenge.id, try? result.get())
                }
            }
            for await (id, progress) in group {
                // A single failed progress fetch drops that one ring rather
                // than failing the whole screen — the assignment itself loaded
                // fine and is still worth showing.
                if let progress { loaded[id] = progress }
            }
        }

        challengeProgress = loaded
    }

    /// Replaces one entry after a check-in on the detail screen, so returning
    /// to the list shows the new streak without a full reload.
    func updateProgress(_ progress: ChallengeProgress, for assignmentId: String) {
        challengeProgress[assignmentId] = progress
    }

    /// The backend orders by `createdAt` descending — assignment-entry order,
    /// which has nothing to do with what the patient is currently doing. Left
    /// alone it puts a finished consultation above the 12-week programme
    /// they're halfway through.
    ///
    /// So: active assignments first, and within those, anything still running
    /// or still to come before anything already finished. Live items sort
    /// soonest-first (the next thing to happen is the thing you care about);
    /// finished ones sort most-recent-first, since old history is the least
    /// interesting thing on the screen.
    func sorted() -> [PatientProgramme] {
        guard let all = assignments.value else { return [] }
        return all.sorted { lhs, rhs in
            let lhsStatus = rank(lhs.parsedStatus)
            let rhsStatus = rank(rhs.parsedStatus)
            if lhsStatus != rhsStatus { return lhsStatus < rhsStatus }

            let lhsFinished = hasFinished(lhs)
            let rhsFinished = hasFinished(rhs)
            if lhsFinished != rhsFinished { return !lhsFinished }

            let lhsDate = LocalDay.date(from: lhs.startDate) ?? .distantPast
            let rhsDate = LocalDay.date(from: rhs.startDate) ?? .distantPast
            if lhsDate != rhsDate {
                return lhsFinished ? lhsDate > rhsDate : lhsDate < rhsDate
            }
            return lhs.id < rhs.id
        }
    }

    /// Past its end date. For a session `endDate == startDate`, so this is
    /// "the appointment already happened"; for a programme or challenge it's
    /// "the window has closed". Note this is independent of `status`, which
    /// stays `active` until someone explicitly closes the assignment.
    private func hasFinished(_ assignment: PatientProgramme) -> Bool {
        guard let end = LocalDay.date(from: assignment.endDate) else { return false }
        return end < LocalDay.startOfToday()
    }

    private func rank(_ status: AssignmentStatus?) -> Int {
        switch status {
        case .active: return 0
        case .completed: return 1
        case .cancelled: return 2
        case nil: return 3
        }
    }
}

private extension Result where Failure == APIError {
    var cardState: CardState<Success> {
        switch self {
        case .success(let value): return .success(value)
        case .failure(let error): return .failure(error.message)
        }
    }
}
