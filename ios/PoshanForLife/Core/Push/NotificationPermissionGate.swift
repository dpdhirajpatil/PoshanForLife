import Foundation
import UserNotifications

/// Decides whether the "why we want to notify you" rationale sheet should
/// show, and requests the real system permission once the user agrees.
///
/// **Why a persisted flag, not just `authorizationStatus == .notDetermined`:**
/// status alone isn't enough to gate a one-shot rationale sheet. A user who
/// taps "Not now" leaves the OS status at `.notDetermined` forever — nothing
/// was ever decided — so checking status alone would show the sheet again
/// every single app open. iOS shows its own system prompt exactly once ever
/// per install; this sheet needs the same one-shot behavior, which means
/// tracking "have we already asked" ourselves.
///
/// This also composes correctly with `ReminderScheduler`, which already
/// requests the same system permission for medication reminders (IOS-04) —
/// `UNUserNotificationCenter` authorization is one systemwide grant, not a
/// per-feature one. If a user already answered via Track's reminders flow,
/// `authorizationStatus` is no longer `.notDetermined`, `shouldShowRationale`
/// returns `false`, and this sheet never appears at all — exactly right,
/// since re-asking would just be noise.
enum NotificationPermissionGate {

    private static let hasAskedKey = "notification_rationale_shown"

    static func shouldShowRationale(defaults: UserDefaults = .standard) async -> Bool {
        guard !defaults.bool(forKey: hasAskedKey) else { return false }
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        return status == .notDetermined
    }

    /// Shows the real system prompt and marks the rationale as shown either
    /// way — an explicit decline is still a decision; the sheet must not
    /// reappear because the user said no.
    @discardableResult
    static func requestAuthorization(defaults: UserDefaults = .standard) async -> Bool {
        defer { defaults.set(true, forKey: hasAskedKey) }
        return (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// "Not now" on the rationale sheet: no system prompt, but still a
    /// decision — don't show the sheet again on the next launch.
    static func declineWithoutAsking(defaults: UserDefaults = .standard) {
        defaults.set(true, forKey: hasAskedKey)
    }
}
