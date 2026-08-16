import SwiftUI

@main
struct PoshanForLifeApp: App {

    /// Bridges the handful of UIKit delegate callbacks push needs
    /// (`didRegisterForRemoteNotificationsWithDeviceToken`, the two
    /// notification delegate protocols) that SwiftUI's `App` has no hook for.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    /// One container for the app's lifetime, built before any view exists.
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView(
                authRepository: container.authRepository,
                sessionExpired: container.sessionExpired
            )
            .environmentObject(container)
            // Separate from `container` itself: `AppContainer` forwards to
            // this but publishes nothing of its own (see its doc comment), so
            // a view needs the concrete `DeepLinkRouter` in its environment to
            // actually observe `pending` changing.
            .environmentObject(container.deepLinkRouter)
        }
    }
}
