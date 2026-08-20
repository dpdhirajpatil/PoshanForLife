import Foundation

/// Tracks which earned badge ids the celebration animation has already
/// played for, so `BadgesView` only celebrates once per newly-earned badge
/// rather than on every screen visit — the API response carries no "newly
/// earned" flag of its own. Mirrors Android's `SeenBadgesDataStore`.
final class SeenBadgesStore {

    private let defaults: UserDefaults
    private static let key = "seen_badge_ids"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func isSeen(_ badgeId: String) -> Bool {
        seenIds.contains(badgeId)
    }

    func markSeen(_ badgeId: String) {
        defaults.set(Array(seenIds.union([badgeId])), forKey: Self.key)
    }

    private var seenIds: Set<String> {
        Set(defaults.stringArray(forKey: Self.key) ?? [])
    }
}
