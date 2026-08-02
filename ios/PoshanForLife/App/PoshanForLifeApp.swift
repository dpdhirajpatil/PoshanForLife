import SwiftUI

@main
struct PoshanForLifeApp: App {

    /// One container for the app's lifetime, built before any view exists.
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView(
                authRepository: container.authRepository,
                sessionExpired: container.sessionExpired
            )
            .environmentObject(container)
        }
    }
}
