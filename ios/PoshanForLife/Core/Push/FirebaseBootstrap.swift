import FirebaseCore
import Foundation

/// Configures Firebase iff a real `GoogleService-Info.plist` is bundled.
///
/// **Why this guard exists, and isn't optional:** `FirebaseApp.configure()`
/// terminates the process if the plist is missing or malformed — it does not
/// throw, log-and-continue, or return an error. Every other Firebase call
/// (`Messaging.messaging()`, `apnsToken` assignment, `MessagingDelegate`
/// registration) then crashes too, because they all assume a configured
/// `FirebaseApp` exists. Without this check, this app would crash on launch
/// the moment IOS-08's code shipped, on every build until someone completed
/// the one manual step this feature has: registering the iOS app in the
/// Firebase console and dropping the resulting plist into the project. That
/// step is **not done as of this commit** — see `ios/README.md`'s Push
/// Notifications section for exactly what's left and why it couldn't be
/// finished here.
///
/// Every other push code path checks `isConfigured` before touching
/// `FirebaseMessaging` for the same reason.
enum FirebaseBootstrap {

    private(set) static var isConfigured = false

    static func configureIfAvailable() {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            #if DEBUG
            print("[Push] GoogleService-Info.plist not found — Firebase not configured, push disabled.")
            #endif
            return
        }
        FirebaseApp.configure()
        isConfigured = true
    }
}
