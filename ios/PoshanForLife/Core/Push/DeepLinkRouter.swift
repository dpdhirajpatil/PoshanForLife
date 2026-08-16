import Foundation

/// Where a tapped notification wants to go — set from two places:
/// `PushCoordinator`'s `UNUserNotificationCenterDelegate` (a delivered push
/// was tapped) and `NotificationsListView` (a row in the in-app list was
/// tapped). Whoever owns the current navigation reads `pending` and clears it
/// once handled.
///
/// **Honest scope note:** every `relatedEntityType` this backend actually
/// sends — `patient`, `lead`, `report`, `badge` (grep-verified against every
/// `NotificationService.create` call site) — targets a detail screen that
/// does not exist on iOS yet. Practitioner's Patients/Leads tabs and a
/// per-doctor report view are all still placeholders; there is no Badges
/// screen at all. So this router is real, tested, and ready — but nothing
/// consumes `pending` into an actual navigation today. That is a gap in the
/// destination screens, not in this plumbing; wiring a real case in here is a
/// one-line addition once any of those screens exists. See `RootView` for
/// where `pending` is currently drained (mark-read only, no navigation).
@MainActor
final class DeepLinkRouter: ObservableObject {
    @Published var pending: NotificationDeepLink?
}
