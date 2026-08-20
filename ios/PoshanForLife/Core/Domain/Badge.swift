import Foundation

/// Wire values are lowercase snake_case — `BadgeCriteriaType`'s `@JsonValue`
/// on the backend.
enum BadgeCriteriaType: String, Decodable {
    case challengeCompleted = "challenge_completed"
    case programmeCount = "programme_count"
    case streakDays = "streak_days"
    case custom
}

/// The full badge catalog annotated per-patient — `GET
/// /patients/{id}/badges` — includes locked/unearned badges so `BadgesView`
/// can render them grayed-out with a lock overlay rather than omitting them.
struct PatientBadgeStatus: Decodable, Identifiable, Equatable {
    let id: String
    let name: String
    let description: String?
    let iconKey: String
    let criteriaType: BadgeCriteriaType
    let criteriaValue: Int
    let earned: Bool
    let earnedAt: String?
}

extension PatientBadgeStatus {
    /// `iconKey` is a free-form string an admin types in the web console —
    /// there's no fixed enum on the backend for it — so this is a
    /// best-effort keyword match with `criteriaType` as a fallback. Mirrors
    /// Android's `iconFor(badge)` so the same badge reads the same way on
    /// both platforms.
    var symbolName: String {
        let key = iconKey.lowercased()
        if key.contains("streak") || key.contains("fire") { return "flame.fill" }
        if key.contains("star") { return "star.fill" }
        if key.contains("medal") { return "rosette" }
        if key.contains("trophy") || key.contains("cup") { return "trophy.fill" }
        if key.contains("book") || key.contains("program") { return "book.closed.fill" }
        if key.contains("premium") || key.contains("crown") { return "sparkles" }

        switch criteriaType {
        case .streakDays: return "flame.fill"
        case .programmeCount: return "book.closed.fill"
        case .challengeCompleted: return "rosette"
        case .custom: return "trophy.fill"
        }
    }
}
