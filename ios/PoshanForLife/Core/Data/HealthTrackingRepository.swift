import Combine
import Foundation

/// Local-first health logging.
///
/// Every `log` writes to disk and publishes immediately, so the UI updates on
/// the next frame; the backend write (weight only) happens afterwards and never
/// blocks the tap. A failed sync leaves the entry `.pending` and is retried on
/// the next log or refresh — nothing is lost if the phone is offline.
@MainActor
final class HealthTrackingRepository: ObservableObject {

    @Published private(set) var entries: [HealthEntry] = []

    private let store: JSONFileStore<HealthEntry>
    private let client: APIClient
    private var isSyncing = false

    init(client: APIClient, store: JSONFileStore<HealthEntry> = JSONFileStore(filename: "health-entries.json")) {
        self.client = client
        self.store = store
    }

    func loadFromDisk() async {
        entries = await store.load()
        await syncPending()
    }

    func log(_ type: HealthMetricType, value: Double, unit: String, loggedAt: Date = Date()) async {
        let entry = HealthEntry(type: type, value: value, unit: unit, loggedAt: loggedAt)
        entries.append(entry)
        await persist()
        await syncPending()
    }

    /// Replaces today's HealthKit value for a metric rather than appending, so
    /// repeated syncs of a running daily total don't stack up.
    ///
    /// Only `.weight` is ever queued for the backend — `syncState` defaults to
    /// `.pending` there and `.localOnly` for everything else, same rule as a
    /// manual `log()`. Re-syncing an existing HealthKit weight entry re-queues
    /// it too, since a new reading is worth re-pushing even if the last one
    /// already made it to the backend.
    func upsertFromHealthKit(_ type: HealthMetricType, value: Double, unit: String) async {
        let startOfToday = Calendar.current.startOfDay(for: Date())
        let index = entries.firstIndex {
            $0.type == type && $0.source == .healthKit && $0.loggedAt >= startOfToday
        }
        if let index {
            entries[index].value = value
            entries[index].unit = unit
            entries[index].loggedAt = Date()
            if type.isSyncable { entries[index].syncState = .pending }
        } else {
            entries.append(HealthEntry(type: type, value: value, unit: unit, source: .healthKit))
        }
        await persist()
        await syncPending()
    }

    // MARK: - Reads

    func entries(on day: Date = Date()) -> [HealthEntry] {
        let start = Calendar.current.startOfDay(for: day)
        guard let end = Calendar.current.date(byAdding: .day, value: 1, to: start) else { return [] }
        return entries.filter { $0.loggedAt >= start && $0.loggedAt < end }
    }

    func total(_ type: HealthMetricType, on day: Date = Date()) -> Double {
        entries(on: day).filter { $0.type == type }.reduce(0) { $0 + $1.value }
    }

    func latest(_ type: HealthMetricType) -> HealthEntry? {
        entries.filter { $0.type == type }.max { $0.loggedAt < $1.loggedAt }
    }

    func latestToday(_ type: HealthMetricType) -> HealthEntry? {
        entries(on: Date()).filter { $0.type == type }.max { $0.loggedAt < $1.loggedAt }
    }

    // MARK: - Sync

    /// Pushes anything still `.pending`. Only weight ever gets here — water,
    /// nutrition and sleep are created `.localOnly` because `health_records`
    /// has no column for them, so they're never queued in the first place.
    func syncPending() async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        for entry in entries where entry.syncState == .pending {
            guard entry.type == .weight else { continue }
            let request = UpsertHealthRecordRequest(
                // patientId is deliberately omitted: the backend rejects it
                // outright for a PATIENT/LEAD caller, who may only write their
                // own record. Sending it is a 422, not a convenience.
                recordDate: Self.dayFormatter.string(from: entry.loggedAt),
                source: entry.source == .healthKit ? "wearable_sync" : "patient_manual",
                weightKg: entry.value
            )
            // Skip rather than fall back to a body-less POST — that would be an
            // empty upsert the backend would happily accept, blanking nothing
            // but recording a bogus patient_manual entry for the day.
            guard let endpoint = try? Endpoint.json(path: "health-records", method: .post, body: request) else {
                continue
            }
            let result: Result<UpsertHealthRecordResponse, APIError> = await client.send(endpoint)
            if case .success = result, let index = entries.firstIndex(where: { $0.id == entry.id }) {
                entries[index].syncState = .synced
            }
        }
        await persist()
    }

    private func persist() async {
        await store.save(entries)
    }

    /// The backend expects a plain `yyyy-MM-dd` date, interpreted as UTC.
    static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()
}

/// `POST health-records`. Every metric is optional and only overwrites when
/// non-nil — the endpoint upserts one record per patient per day.
struct UpsertHealthRecordRequest: Encodable {
    var recordDate: String?
    /// Must be `patient_manual` or `wearable_sync` for a PATIENT/LEAD caller;
    /// anything else is rejected.
    let source: String
    var weightKg: Double?
    var bodyFatPct: Double?
    var bmi: Double?
}

struct UpsertHealthRecordResponse: Decodable {
    let upserted: Bool

    private enum CodingKeys: String, CodingKey { case upserted }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        upserted = try c.decodeIfPresent(Bool.self, forKey: .upserted) ?? false
    }
}
