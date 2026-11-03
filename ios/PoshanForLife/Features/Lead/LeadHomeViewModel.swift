import Foundation

/// Streak + badges for the lead home screen. Port of Android's LeadHomeViewModel.
@MainActor
final class LeadHomeViewModel: ObservableObject {

    @Published private(set) var isLoading = true
    @Published private(set) var streak: LeadStreak?
    @Published private(set) var badges: [PatientBadgeStatus] = []
    @Published private(set) var isCheckingIn = false

    private let repository: LeadSelfRepository

    init(repository: LeadSelfRepository) {
        self.repository = repository
    }

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    var checkedInToday: Bool {
        streak?.lastLoggedDate == Self.dayFormatter.string(from: Date())
    }

    func load() async {
        async let streakResult = repository.streak()
        async let badgesResult = repository.badges()
        if case .success(let value) = await streakResult { streak = value }
        if case .success(let value) = await badgesResult { badges = value }
        isLoading = false
    }

    func checkIn() async {
        guard !checkedInToday, !isCheckingIn else { return }
        isCheckingIn = true
        if case .success(let value) = await repository.checkIn() { streak = value }
        isCheckingIn = false
        // A check-in can earn a badge server-side, so refresh the row.
        if case .success(let value) = await repository.badges() { badges = value }
    }
}
