import BackgroundTasks
import FirebaseMessaging
import UIKit
import UserNotifications

/// Minimal `UIApplicationDelegate` bridge — SwiftUI's `App` protocol has no
/// hook for `didRegisterForRemoteNotificationsWithDeviceToken` or the two
/// delegate protocols push needs, so `@UIApplicationDelegateAdaptor` in
/// `PoshanForLifeApp` brings this in just for those.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseBootstrap.configureIfAvailable()

        // Only wire Messaging's delegate when there's a configured FirebaseApp
        // for it to belong to — see FirebaseBootstrap's doc comment.
        if FirebaseBootstrap.isConfigured {
            Messaging.messaging().delegate = PushCoordinator.shared
        }
        UNUserNotificationCenter.current().delegate = PushCoordinator.shared

        // Harmless without Firebase configured: this is a plain APNs/UIKit
        // call, independent of FirebaseMessaging. The resulting device token
        // just has nowhere to go until Firebase is configured (see below).
        application.registerForRemoteNotifications()

        // Must happen before this method returns — BGTaskScheduler requires
        // every launch handler registered by then, so it can't wait for
        // AppContainer to exist. See HealthKitBackgroundSync's doc comment.
        HealthKitBackgroundSync.register()

        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        HealthKitBackgroundSync.scheduleNext()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard FirebaseBootstrap.isConfigured else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        #if DEBUG
        print("[Push] APNs registration failed: \(error.localizedDescription)")
        #endif
    }
}
