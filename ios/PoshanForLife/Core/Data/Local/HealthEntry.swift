import Foundation

/// What a locally-logged entry measures.
///
/// Only ``weight`` has anywhere to go on the backend — `health_records` stores
/// body composition and nothing else. Water, nutrition and sleep have no column
/// and no endpoint, so they are local-only by design, not by omission.
enum HealthMetricType: String, Codable, CaseIterable {
    case water
    case nutrition
    case sleep
    case weight
    /// Read-only, written by HealthKit sync in IOS-11 — never entered by hand.
    case steps

    /// Whether the backend has somewhere to put this.
    var isSyncable: Bool {
        switch self {
        case .weight: return true
        case .water, .nutrition, .sleep, .steps: return false
        }
    }
}

/// Where the value came from. Manual entries are the user's own typing; synced
/// ones arrive from HealthKit and must not be double-counted or re-uploaded.
enum HealthEntrySource: String, Codable {
    case manual
    case healthKit
}

/// Tracks whether an entry still owes the backend a write.
///
/// `localOnly` matters: without it every water tap would sit in a "pending"
/// queue that can never drain, and the queue would grow forever while a
/// retry loop hammered an endpoint that has no field for it.
enum SyncState: String, Codable {
    case localOnly
    case pending
    case synced
}

struct HealthEntry: Codable, Identifiable, Equatable {
    let id: UUID
    let type: HealthMetricType
    var value: Double
    var unit: String
    var loggedAt: Date
    var source: HealthEntrySource
    var syncState: SyncState

    init(
        id: UUID = UUID(),
        type: HealthMetricType,
        value: Double,
        unit: String,
        loggedAt: Date = Date(),
        source: HealthEntrySource = .manual,
        syncState: SyncState? = nil
    ) {
        self.id = id
        self.type = type
        self.value = value
        self.unit = unit
        self.loggedAt = loggedAt
        self.source = source
        // Default derived from the type so a caller can't accidentally queue
        // something unsyncable.
        self.syncState = syncState ?? (type.isSyncable ? .pending : .localOnly)
    }
}

extension HealthEntry {
    /// Nutrition packs the meal into the unit as `kcal:breakfast`, matching the
    /// Android client's encoding so the two stay readable side by side.
    var mealType: String? {
        guard type == .nutrition else { return nil }
        let parts = unit.split(separator: ":", maxSplits: 1)
        return parts.count == 2 ? String(parts[1]) : nil
    }

    static func nutritionUnit(mealType: String) -> String { "kcal:\(mealType)" }
}
