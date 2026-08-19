import Foundation
import HealthKit

/// Wraps `HKHealthStore` and the read-only sync flow for steps, sleep, heart
/// rate, and body mass.
///
/// **The second deliberate `.shared` in this codebase** (see
/// `PushCoordinator`'s doc comment for the first, and why): the background
/// refresh task registered in `AppDelegate.didFinishLaunchingWithOptions`
/// needs to reach this before `AppContainer` — built by SwiftUI's `App`
/// struct — necessarily exists yet, for the same structural reason push does.
///
/// Doesn't hold a `HealthTrackingRepository` itself, so it stays a plain
/// HealthKit wrapper rather than a second thing that knows about the local
/// cache. `AppContainer` bridges a sync's result into the repository via
/// `onSync`, mirroring exactly how it bridges an FCM token into
/// `UserRepository` via `PushCoordinator.onTokenChange`.
@MainActor
final class HealthKitManager: ObservableObject {

    static let shared = HealthKitManager()

    enum ConnectionStatus: Equatable {
        case unavailable
        case notConnected
        case connected
    }

    struct DailySnapshot {
        var steps: Double?
        var heartRateAvgBpm: Double?
        var weightKg: Double?
        var sleepHours: Double?
    }

    @Published private(set) var connectionStatus: ConnectionStatus
    @Published private(set) var isSyncing = false
    @Published private(set) var lastSyncedAt: Date?
    @Published private(set) var lastSyncError: String?

    private let store = HKHealthStore()
    private var syncHandler: ((DailySnapshot) async -> Void)?

    private static let connectedKey = "healthkit_connected"
    private static let lastSyncedKey = "healthkit_last_synced_at"

    /// `false` on iPad — HealthKit is iPhone/Apple Watch only. Gate any
    /// HealthKit UI on this before ever touching `HKHealthStore`.
    static var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    private static let readTypes: Set<HKObjectType> = {
        var types: Set<HKObjectType> = [
            HKObjectType.categoryType(forIdentifier: .sleepAnalysis)!,
        ]
        for identifier: HKQuantityTypeIdentifier in [.stepCount, .heartRate, .bodyMass] {
            types.insert(HKObjectType.quantityType(forIdentifier: identifier)!)
        }
        return types
    }()

    private init() {
        connectionStatus = Self.isAvailable
            ? (UserDefaults.standard.bool(forKey: Self.connectedKey) ? .connected : .notConnected)
            : .unavailable
        lastSyncedAt = UserDefaults.standard.object(forKey: Self.lastSyncedKey) as? Date
    }

    /// Registers the closure that receives each sync's result.
    func onSync(_ handler: @escaping (DailySnapshot) async -> Void) {
        syncHandler = handler
    }

    /// Requests read access to all four types in one sheet.
    ///
    /// HealthKit deliberately never reveals per-type grant/deny for read-only
    /// access — `authorizationStatus(for:)` only means anything for types
    /// requested to *share* (write), which this app doesn't. So "connected"
    /// here means "the request flow completed", not "every type was
    /// granted" — the same ambiguity `NotificationPermissionGate` avoids by
    /// tracking "have we asked" rather than trying to read a status that
    /// doesn't exist for this case. A sync against a denied type just comes
    /// back with no samples; nothing breaks, it just has nothing to show.
    @discardableResult
    func requestAuthorization() async -> Bool {
        guard Self.isAvailable else { return false }
        do {
            try await store.requestAuthorization(toShare: [], read: Self.readTypes)
        } catch {
            lastSyncError = "Couldn't connect to Apple Health."
            return false
        }
        connectionStatus = .connected
        UserDefaults.standard.set(true, forKey: Self.connectedKey)
        return true
    }

    /// Backs both the Track tab's manual "Sync now" button and the
    /// background refresh task. A simple daily-statistics read rather than
    /// `HKAnchoredObjectQuery` incremental sync — this only ever needs
    /// "today's total", not a running history, so anchor bookkeeping would be
    /// complexity with nothing to show for it.
    func syncNow() async {
        guard connectionStatus == .connected, !isSyncing else { return }
        isSyncing = true
        lastSyncError = nil
        defer { isSyncing = false }

        let snapshot = await fetchTodaySnapshot()
        await syncHandler?(snapshot)

        lastSyncedAt = Date()
        UserDefaults.standard.set(lastSyncedAt, forKey: Self.lastSyncedKey)
    }

    // MARK: - Queries

    private func fetchTodaySnapshot() async -> DailySnapshot {
        let now = Date()
        let startOfToday = Calendar.current.startOfDay(for: now)
        let todayPredicate = HKQuery.predicateForSamples(withStart: startOfToday, end: now, options: .strictStartDate)

        async let steps = cumulativeSum(for: .stepCount, unit: .count(), predicate: todayPredicate)
        async let heartRate = discreteAverage(for: .heartRate, unit: HKUnit(from: "count/min"), predicate: todayPredicate)
        // Weight isn't a "today" reading the way steps/heart rate are — most
        // people don't step on a scale daily — so this takes the most recent
        // sample regardless of date rather than requiring one logged today.
        async let weight = mostRecentQuantitySample(for: .bodyMass, unit: .gramUnit(with: .kilo))
        async let sleep = asleepHours(since: now.addingTimeInterval(-24 * 3600), to: now)

        return await DailySnapshot(
            steps: steps,
            heartRateAvgBpm: heartRate,
            weightKg: weight,
            sleepHours: sleep
        )
    }

    private func cumulativeSum(
        for identifier: HKQuantityTypeIdentifier,
        unit: HKUnit,
        predicate: NSPredicate
    ) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, stats, _ in
                continuation.resume(returning: stats?.sumQuantity()?.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    private func discreteAverage(
        for identifier: HKQuantityTypeIdentifier,
        unit: HKUnit,
        predicate: NSPredicate
    ) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: predicate, options: .discreteAverage) { _, stats, _ in
                continuation.resume(returning: stats?.averageQuantity()?.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    private func mostRecentQuantitySample(for identifier: HKQuantityTypeIdentifier, unit: HKUnit) async -> Double? {
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        return await withCheckedContinuation { continuation in
            let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
            let query = HKSampleQuery(sampleType: type, predicate: nil, limit: 1, sortDescriptors: [sort]) { _, samples, _ in
                let value = (samples?.first as? HKQuantitySample)?.quantity.doubleValue(for: unit)
                continuation.resume(returning: value)
            }
            store.execute(query)
        }
    }

    /// Sums every category sample in the window whose value isn't `.inBed` or
    /// `.awake` — covers `.asleepUnspecified` plus the sleep-stage values
    /// (`.asleepCore`/`.asleepDeep`/`.asleepREM`) Apple Watch reports,
    /// without hard-coding which stages a given device chooses to report.
    private func asleepHours(since start: Date, to end: Date) async -> Double? {
        guard let type = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                guard let categorySamples = samples as? [HKCategorySample] else {
                    continuation.resume(returning: nil)
                    return
                }
                let asleepSeconds = categorySamples
                    .filter {
                        $0.value != HKCategoryValueSleepAnalysis.inBed.rawValue
                            && $0.value != HKCategoryValueSleepAnalysis.awake.rawValue
                    }
                    .reduce(0.0) { $0 + $1.endDate.timeIntervalSince($1.startDate) }
                continuation.resume(returning: asleepSeconds > 0 ? asleepSeconds / 3600 : nil)
            }
            store.execute(query)
        }
    }
}
