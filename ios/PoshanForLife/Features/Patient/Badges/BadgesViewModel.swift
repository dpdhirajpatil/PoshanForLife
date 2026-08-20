import Foundation

@MainActor
final class BadgesViewModel: ObservableObject {

    @Published private(set) var badges: CardState<[PatientBadgeStatus]> = .loading
    /// Non-nil while a newly-earned badge's celebration should play; cleared
    /// automatically a couple seconds after it's set.
    @Published private(set) var celebratingBadge: PatientBadgeStatus?

    private let repository: BadgesRepository
    private let profile: DashboardRepository
    private let seenBadges: SeenBadgesStore

    init(repository: BadgesRepository, profile: DashboardRepository, seenBadges: SeenBadgesStore = SeenBadgesStore()) {
        self.repository = repository
        self.profile = profile
        self.seenBadges = seenBadges
    }

    func load() async {
        guard case .success(let user) = await profile.currentUser() else {
            badges = .failure("Couldn't load your profile")
            return
        }

        let result = await repository.badges(patientId: user.id)
        badges = result.cardState

        if case .success(let list) = result {
            checkForNewlyEarned(list)
        }
    }

    /// At most one badge celebrates per refresh — a second newly-earned badge
    /// (rare: two criteria satisfied by the same check-in) just gets its
    /// celebration on the next load, rather than stacking overlays.
    private func checkForNewlyEarned(_ list: [PatientBadgeStatus]) {
        guard celebratingBadge == nil else { return }
        guard let newlyEarned = list.first(where: { $0.earned && !seenBadges.isSeen($0.id) }) else { return }

        seenBadges.markSeen(newlyEarned.id)
        celebratingBadge = newlyEarned

        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            self?.celebratingBadge = nil
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
