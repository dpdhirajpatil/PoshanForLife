import FirebaseMessaging
import Foundation
import UserNotifications

/// Bridges the two delegate protocols Firebase/UIKit require (`MessagingDelegate`,
/// `UNUserNotificationCenterDelegate`) into the rest of the app.
///
/// **The one deliberate `.shared` in this codebase**, and why: every other
/// cross-cutting concern here is constructor-injected through `AppContainer`
/// (`TokenRefreshCoordinator`, `ReminderScheduler`, …), but `AppDelegate` is
/// instantiated by `@UIApplicationDelegateAdaptor` with no DI entry point —
/// SwiftUI owns its construction, not `PoshanForLifeApp.init()`, and by the
/// time the App struct's body runs there is no reliable way to hand it a
/// dependency built from `AppContainer`. A shared coordinator both
/// `AppDelegate` (which sets it as the delegate) and `AppContainer` (which
/// reads its published state) can reach without one is the standard shape for
/// this exact problem — this is not a "reach for a singleton because DI is
/// hard" shortcut, it's the one place in the app that's structurally outside
/// SwiftUI's environment graph.
@MainActor
final class PushCoordinator: NSObject, ObservableObject {

    static let shared = PushCoordinator()
    private override init() { super.init() }

    /// The FCM registration token, once Firebase has minted one. `nil` until
    /// then — including permanently, on a build with no `GoogleService-Info.plist`.
    @Published private(set) var fcmToken: String?

    let deepLinkRouter = DeepLinkRouter()

    private var tokenHandler: ((String) -> Void)?

    /// Registers for future token changes AND fires immediately if a token is
    /// already cached — covers both orderings: Firebase minting a token before
    /// login (nothing to send it to yet) and after (send it right away).
    /// Mirrors Android's `FcmTokenSynchronizer` re-push-on-every-open path.
    func onTokenChange(_ handler: @escaping (String) -> Void) {
        tokenHandler = handler
        if let fcmToken { handler(fcmToken) }
    }
}

extension PushCoordinator: MessagingDelegate {
    /// Fires on first launch and on every token rotation. `nonisolated` because
    /// Firebase calls this off the main thread; the hop to `@MainActor` happens
    /// inside.
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in
            self.fcmToken = fcmToken
            self.tokenHandler?(fcmToken)
        }
    }
}

extension PushCoordinator: UNUserNotificationCenterDelegate {

    /// iOS suppresses the banner/sound for a notification that arrives while
    /// the app is in the foreground unless this returns non-empty — without
    /// it, a push delivered while the app is open would be silently dropped
    /// from the user's view entirely (no banner, and — because this delegate
    /// method not returning `.badge` doesn't stop the badge from
    /// incrementing anyway — just an inconsistent "the badge went up but
    /// nothing else happened" experience).
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }

    /// The user tapped a delivered notification (foreground or background).
    /// Same `userInfo` shape the backend already sends Android —
    /// `relatedEntityType`/`relatedEntityId` — read directly rather than via a
    /// decoder, since APNs payloads arrive as a loosely-typed `[AnyHashable: Any]`.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        let relatedEntityType = userInfo["relatedEntityType"] as? String
        let relatedEntityId = userInfo["relatedEntityId"] as? String
        guard relatedEntityType != nil || relatedEntityId != nil else { return }

        await MainActor.run {
            deepLinkRouter.pending = NotificationDeepLink(
                relatedEntityType: relatedEntityType,
                relatedEntityId: relatedEntityId
            )
        }
    }
}
