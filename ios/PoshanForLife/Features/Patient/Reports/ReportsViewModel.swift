import Foundation

/// How far back the trend charts look.
enum TrendWindow: String, CaseIterable, Identifiable {
    case thirty = "30d"
    case ninety = "90d"
    case oneEighty = "180d"
    case all = "All"

    var id: String { rawValue }

    var days: Int? {
        switch self {
        case .thirty: return 30
        case .ninety: return 90
        case .oneEighty: return 180
        case .all: return nil
        }
    }
}

@MainActor
final class ReportsViewModel: ObservableObject {

    @Published private(set) var reports: CardState<[ReportListItem]> = .loading
    @Published private(set) var records: CardState<[HealthRecord]> = .loading
    @Published var window: TrendWindow = .ninety

    private let repository: ReportsRepository
    private let profile: DashboardRepository

    init(repository: ReportsRepository, profile: DashboardRepository) {
        self.repository = repository
        self.profile = profile
    }

    func load() async {
        // Both reads are keyed by the patient id, so the profile has to land
        // first. Everything after it runs concurrently.
        guard case .success(let user) = await profile.currentUser() else {
            reports = .failure("Couldn't load your profile")
            records = .failure("Couldn't load your profile")
            return
        }

        async let reportsResult = repository.inbodyReports(patientId: user.id)
        async let recordsResult = repository.trendRecords(patientId: user.id)

        reports = (await reportsResult).cardState
        records = (await recordsResult).cardState
    }

    /// Records inside the selected window, oldest first (the order the backend
    /// already returns and the order Swift Charts wants).
    func windowedRecords() -> [HealthRecord] {
        guard let all = records.value else { return [] }
        guard let days = window.days else { return all }
        guard let cutoff = Calendar.current.date(byAdding: .day, value: -days, to: Date()) else { return all }
        return all.filter { ($0.date ?? .distantPast) >= cutoff }
    }

    /// Only metrics with at least two points are worth drawing — a single
    /// reading isn't a trend, and a line chart of one point renders as nothing.
    func plottableMetrics() -> [TrendMetric] {
        let windowed = windowedRecords()
        return TrendMetric.allCases.filter { metric in
            windowed.compactMap { $0.value(for: metric) }.count >= 2
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
